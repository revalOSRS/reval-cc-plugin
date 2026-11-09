package com.revalclan.api;

import com.google.gson.Gson;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.achievements.AchievementsResponse;
import com.revalclan.api.leaderboard.LeaderboardResponse;
import com.revalclan.api.admin.ActualizeRankChangeResponse;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.api.admin.AdminLoginRequest;
import com.revalclan.api.admin.PendingRankChangesResponse;
import com.revalclan.api.competitions.*;
import com.revalclan.api.announcements.AnnouncementsResponse;
import com.revalclan.api.common.ApiEndpoints;
import com.revalclan.api.common.ApiResponse;
import com.revalclan.api.notifications.NotificationAckResponse;
import com.revalclan.api.notifications.NotificationsResponse;
import com.revalclan.api.diaries.DiariesResponse;
import com.revalclan.api.events.ActiveTeamsResponse;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.api.events.RegistrationResponse;
import com.revalclan.api.points.PointsResponse;
import com.revalclan.api.common.ApiEnvelope;
import com.revalclan.api.common.PublicApiResponse;
import com.revalclan.api.leaguesbingo.LeaguesBingoMeResponse;
import com.revalclan.api.leaguesbingo.LeaguesBingoPickResponse;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse;
import com.revalclan.auth.ApiOrigin;
import com.revalclan.auth.LinkException;
import com.revalclan.auth.LinkTokenManager;
import com.revalclan.auth.SecureHttp;
import com.revalclan.auth.ServerError;
import com.revalclan.util.PluginVersion;
import com.revalclan.util.RequestScope;
import javax.inject.Named;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import okhttp3.*;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for fetching data from the Reval Plugin API.
 */
@Singleton
public class RevalApiService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Gson gson;
    private final OkHttpClient httpClient;
    private enum Auth { NONE, REQUIRED }
    private final LinkTokenManager tokens;
    private final ApiOrigin origin;
    private volatile OkHttpClient credentialedClient;
    private final ClientThread clientThread;
    private volatile RequestScope requests = new RequestScope();

    public void startUp() { requests.cancel(); requests = new RequestScope(); resetEventsSession(); }
    public void shutDown() { requests.cancel(); clearCache(); resetEventsSession(); }

    // Cache durations
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000;

    private final CachedFetch<PointsResponse> points = new CachedFetch<>(CACHE_DURATION_MS);
    private final CachedFetch<ActiveTeamsResponse> teams = new CachedFetch<>(CACHE_DURATION_MS);
    private final CachedFetch<DiariesResponse> diaries = new CachedFetch<>(CACHE_DURATION_MS);

    @Inject
    public RevalApiService(@Named("reval-http") OkHttpClient httpClient, Gson gson,
                           LinkTokenManager tokens, ClientThread clientThread) {
        this(httpClient, gson, tokens, clientThread, ApiOrigin.PRODUCTION);
    }

    RevalApiService(OkHttpClient httpClient, Gson gson, LinkTokenManager tokens,
                    ClientThread clientThread, ApiOrigin origin) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.tokens = tokens;
        this.clientThread = clientThread;
        this.origin = origin;
    }

    /** Client for requests that carry a token: no redirects, no cache, same-origin only. */
    OkHttpClient credentialedClient() {
        OkHttpClient client = credentialedClient;
        if (client == null) {
            client = SecureHttp.harden(httpClient, origin, SecureHttp.API_TIMEOUT_MS);
            credentialedClient = client;
        }
        return client;
    }

    /** Capture authority for delayed UI application; relinking never revives an earlier response. */
    public BooleanSupplier accountScope() {
        RequestScope owner = requests;
        try {
            LinkTokenManager.Grant grant = tokens.grantForCurrentAccount(HttpUrl.get(ApiEndpoints.BASE_URL), null);
            return () -> owner.isActive() && tokens.isCurrent(grant);
        } catch (LinkException error) { return () -> false; }
    }

    // ==================== POINTS API ====================

    public void fetchPoints(Consumer<PointsResponse> onSuccess, Consumer<Exception> onError) {
        observe(points.get(null, () -> getFuture(ApiEndpoints.POINTS, PointsResponse.class)), onSuccess, onError);
    }

    // ==================== ACCOUNT API ====================

    public void fetchAccount(long accountHash, Consumer<AccountResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.ACCOUNT + "?accountHash=" + accountHash, AccountResponse.class, onSuccess, onError);
    }

    public void fetchAccountById(int osrsAccountId, Consumer<AccountResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.accountById(osrsAccountId), AccountResponse.class, onSuccess, onError);
    }

    // ==================== LEADERBOARD API ====================

    public void fetchLeaderboard(Consumer<LeaderboardResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.LEADERBOARD, LeaderboardResponse.class, onSuccess, onError);
    }

    // ==================== EVENTS API ====================

    private final CopyOnWriteArrayList<Consumer<EventsResponse>> eventsListeners = new CopyOnWriteArrayList<>();
    // Only the client thread replaces this lifetime or applies event state.
    private RequestScope eventsScope = new RequestScope();

    /** Successful event responses, or an empty response on reset, on the client thread. */
    public void addEventsListener(Consumer<EventsResponse> listener) {
        eventsListeners.add(listener);
    }

    /** Queue reset behind any publication already running; listeners never overlap or run under a service lock. */
    public void resetEventsSession() {
        RequestScope plugin = requests;
        clientThread.invokeLater(() -> {
            if (plugin != requests) return;
            eventsScope.cancel();
            eventsScope = new RequestScope();
            eventsListeners.forEach(listener -> listener.accept(new EventsResponse()));
        });
    }

    /** Event callbacks run on the client thread, once, including cancellation. */
    public void fetchEvents(Consumer<EventsResponse> onSuccess, Consumer<Exception> onError) {
        RequestScope plugin = requests;
        clientThread.invokeLater(() -> {
            // A caller delayed before enqueue must not disturb a newer plugin lifetime.
            if (!plugin.isActive()) {
                onError.accept(new CancellationException());
                return;
            }
            eventsScope.cancel();
            RequestScope owner = eventsScope = new RequestScope();
            // Keep completion cancellable until the queued state application actually runs.
            CompletableFuture<EventsResponse> result = owner.track(new CompletableFuture<>());
            observe(result, onSuccess, onError);
            owner.track(requestFuture(plugin, ApiEndpoints.url(ApiEndpoints.EVENTS), "GET", null, Auth.NONE, null,
                EventsResponse.class)).whenComplete((response, error) -> clientThread.invokeLater(() -> {
                    if (!owner.isActive() || !plugin.isActive()) result.cancel(false);
                    else if (error != null) result.completeExceptionally(error);
                    else {
                        try {
                            eventsListeners.forEach(listener -> listener.accept(response));
                            result.complete(response);
                        } catch (RuntimeException publicationError) {
                            result.completeExceptionally(publicationError);
                        }
                    }
                }));
        });
    }

    public void fetchActiveTeams(Consumer<ActiveTeamsResponse> onSuccess, Consumer<Exception> onError) {
        observe(teams.get(null, () -> getFuture(ApiEndpoints.EVENTS_ACTIVE_TEAMS, ActiveTeamsResponse.class)), onSuccess, onError);
    }

    /**
     * Full Leagues Bingo payload for an event: every region board with tiles,
     * every team with unlocks, completions and per-tile progress. Not cached:
     * the caller decides when a refresh is worth a round-trip.
     */
    public void fetchLeaguesBingoEvent(String eventId, Consumer<LeaguesBingoResponse> onSuccess, Consumer<Exception> onError) {
        getPublic(ApiEndpoints.leaguesBingoEventUrl(eventId), LeaguesBingoResponse.class, onSuccess, onError);
    }

    /** What this account may do in a Leagues Bingo event (team, role, may pick). */
    public void fetchLeaguesBingoMe(String eventId, long accountHash,
                                    Consumer<LeaguesBingoMeResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.leaguesBingoMe(eventId, accountHash), LeaguesBingoMeResponse.class, onSuccess, onError);
    }

    /**
     * Spend one pick token on a region. teamId is only honoured for
     * superadmins; pickers always act for their own team.
     */
    public void pickLeaguesBingoRegion(String eventId, long accountHash, String region, String teamId,
                                       Consumer<LeaguesBingoPickResponse> onSuccess, Consumer<Exception> onError) {
        Map<String, Object> body = new HashMap<>();
        body.put("accountHash", String.valueOf(accountHash));
        body.put("region", region);
        if (teamId != null) body.put("teamId", teamId);
        post(ApiEndpoints.leaguesBingoPick(eventId), gson.toJson(body), accountHash, LeaguesBingoPickResponse.class, onSuccess, onError);
    }

    public void registerForEvent(String eventId, long accountHash,
                                 Consumer<RegistrationResponse> onSuccess, Consumer<Exception> onError) {
        post(ApiEndpoints.eventRegister(eventId), "{\"accountHash\":\"" + accountHash + "\"}", accountHash,
            RegistrationResponse.class, onSuccess, onError);
    }

    public void cancelEventRegistration(String eventId, long accountHash,
                                        Consumer<RegistrationResponse> onSuccess, Consumer<Exception> onError) {
        delete(ApiEndpoints.eventRegister(eventId), "{\"accountHash\":\"" + accountHash + "\"}", accountHash,
            RegistrationResponse.class, onSuccess, onError);
    }

    // ==================== ACHIEVEMENTS API ====================

    public void fetchAchievementDefinitions(long accountHash, Consumer<AchievementsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.ACHIEVEMENTS + "?accountHash=" + accountHash, AchievementsResponse.class, onSuccess, onError);
    }

    // ==================== DIARIES API ====================

    public void fetchDiaries(Long accountHash, Consumer<DiariesResponse> onSuccess, Consumer<Exception> onError) {
        if (accountHash == null) {
            observe(diaries.get(null, () -> getFuture(ApiEndpoints.DIARIES, DiariesResponse.class)), onSuccess, onError);
        } else {
            get(ApiEndpoints.DIARIES + "?accountHash=" + accountHash, DiariesResponse.class, onSuccess, onError);
        }
    }

    // ==================== COMPETITIONS API ====================

    public void fetchFeaturedRace(Consumer<FeaturedRaceResponse> onSuccess, Consumer<Exception> onError) {
        getPublic(ApiEndpoints.publicUrl(ApiEndpoints.PUBLIC_FEATURED_RACE), FeaturedRaceResponse.class, onSuccess, onError);
    }

    public void fetchScheduledCompetitions(Consumer<CompetitionsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.COMPETITIONS_SCHEDULED, CompetitionsResponse.class, onSuccess, onError);
    }

    public void fetchActiveCompetitions(Consumer<CompetitionsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.COMPETITIONS_ACTIVE, CompetitionsResponse.class, onSuccess, onError);
    }

    public void fetchCompetitionDetails(String competitionId, Consumer<CompetitionDetailsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.competitionById(competitionId), CompetitionDetailsResponse.class, onSuccess, onError);
    }

    // ==================== COMPETITION VOTES API ====================

    public void fetchVotes(Consumer<VotesResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.COMPETITION_VOTES, VotesResponse.class, onSuccess, onError);
    }

    public void castVote(String voteId, String optionId, long accountHash,
                         Consumer<CastVoteResponse> onSuccess, Consumer<Exception> onError) {
        Map<String, Object> body = new HashMap<>();
        body.put("optionId", optionId);
        post(ApiEndpoints.voteCast(voteId) + "?accountHash=" + accountHash,
            gson.toJson(body), accountHash, CastVoteResponse.class, onSuccess, onError);
    }

    public void fetchMyVote(String voteId, long accountHash,
                            Consumer<MyVoteResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.voteMyVote(voteId) + "?accountHash=" + accountHash,
            MyVoteResponse.class, onSuccess, onError);
    }

    // ==================== ADMIN API ====================

    public void adminLogin(Long accountHash, String osrsNickname,
                           Consumer<AdminAuthResponse> onSuccess, Consumer<Exception> onError) {
        AdminLoginRequest requestBody = new AdminLoginRequest(
            accountHash != null ? String.valueOf(accountHash) : null,
            osrsNickname
        );
        request(ApiEndpoints.url(ApiEndpoints.ADMIN_AUTH_LOGIN), "POST", gson.toJson(requestBody), Auth.REQUIRED,
            accountHash, AdminAuthResponse.class, onSuccess, onError);
    }

    public void fetchPendingRankChanges(Consumer<PendingRankChangesResponse> onSuccess, Consumer<Exception> onError) {
        getPrivate(ApiEndpoints.ADMIN_RANK_CHANGES_PENDING + "?limit=100",
            PendingRankChangesResponse.class, onSuccess, onError);
    }

    public void actualizeRankChange(int rankChangeId,
                                    Consumer<ActualizeRankChangeResponse> onSuccess, Consumer<Exception> onError) {
        request(ApiEndpoints.url(ApiEndpoints.rankChangeActualize(rankChangeId)), "POST", null, Auth.REQUIRED,
            null, ActualizeRankChangeResponse.class, onSuccess, onError);
    }

    // ==================== ANNOUNCEMENTS API ====================

    public void fetchAnnouncements(Consumer<AnnouncementsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.ANNOUNCEMENTS, AnnouncementsResponse.class, onSuccess, onError);
    }

    // ==================== NOTIFICATIONS API ====================

    public void fetchNotifications(long accountHash, Consumer<NotificationsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.NOTIFICATIONS + "?accountHash=" + accountHash,
            NotificationsResponse.class, onSuccess, onError);
    }

    public void acknowledgeNotifications(long accountHash, List<Integer> notificationIds,
                                         Consumer<NotificationAckResponse> onSuccess, Consumer<Exception> onError) {
        Map<String, Object> body = new HashMap<>();
        body.put("accountHash", String.valueOf(accountHash));
        body.put("notificationIds", notificationIds);
        post(ApiEndpoints.NOTIFICATIONS_ACK, gson.toJson(body), accountHash,
            NotificationAckResponse.class, onSuccess, onError);
    }

    // ==================== CACHE MANAGEMENT ====================

    public void clearCache() {
        points.clear(); teams.clear(); diaries.clear();
    }


    // ==================== HTTP HELPERS ====================

    /**
     * Read of the plugin API. A read that names an account (an {@code accountHash} query) needs the bearer,
     * exactly as the backend requires, and can only be made for the logged-in account; any other read is
     * public and goes out without one.
     */
    private <T extends ApiResponse> void get(String endpoint, Class<T> responseClass,
                                             Consumer<T> onSuccess, Consumer<Exception> onError) {
        String url = ApiEndpoints.url(endpoint);
        HttpUrl parsed = HttpUrl.parse(url);
        Auth auth = parsed != null && parsed.queryParameterNames().contains("accountHash") ? Auth.REQUIRED : Auth.NONE;
        request(url, "GET", null, auth, null, responseClass, onSuccess, onError);
    }

    /** Read of private data: needs the logged-in account's link. */
    private <T extends ApiResponse> void getPrivate(String endpoint, Class<T> responseClass,
                                                    Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(ApiEndpoints.url(endpoint), "GET", null, Auth.REQUIRED, null, responseClass, onSuccess, onError);
    }

    /** GET against the public API (full URL, {success, data} envelope). Never carries a token. */
    private <T extends PublicApiResponse> void getPublic(String url, Class<T> responseClass,
                                                         Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(url, "GET", null, Auth.NONE, null, responseClass, onSuccess, onError);
    }

    /** Change on behalf of {@code accountHash}, which must be the logged-in, linked account. */
    private <T extends ApiResponse> void post(String endpoint, String body, long accountHash, Class<T> responseClass,
                                              Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(ApiEndpoints.url(endpoint), "POST", body, Auth.REQUIRED, accountHash, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void delete(String endpoint, String body, long accountHash, Class<T> responseClass,
                                                Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(ApiEndpoints.url(endpoint), "DELETE", body, Auth.REQUIRED, accountHash, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> CompletableFuture<T> getFuture(String endpoint, Class<T> type) {
        return requestFuture(requests, ApiEndpoints.url(endpoint), "GET", null, Auth.NONE, null, type);
    }

    /** Exactly one success or error callback, including cancellation and a stopped plugin. */
    private static <T> void observe(CompletableFuture<T> request, Consumer<T> success, Consumer<Exception> failure) {
        request.whenComplete((response, error) -> {
            if (error == null) success.accept(response);
            else {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                failure.accept(cause instanceof Exception ? (Exception) cause : new Exception(cause));
            }
        });
    }

    private <T extends ApiEnvelope> void request(String url, String method, String body, Auth auth,
                                                 Long accountHash, Class<T> type,
                                                 Consumer<T> success, Consumer<Exception> failure) {
        observe(requestFuture(requests, url, method, body, auth, accountHash, type), success, failure);
    }

    private <T extends ApiEnvelope> CompletableFuture<T> requestFuture(RequestScope scope, String url, String method,
                                                                      String body, Auth auth, Long accountHash, Class<T> type) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) return CompletableFuture.failedFuture(new IOException("Invalid request URL"));
        final LinkTokenManager.Grant grant;
        try { grant = auth == Auth.REQUIRED ? tokens.grantForCurrentAccount(parsed, accountHash) : null; }
        catch (LinkException error) { return CompletableFuture.failedFuture(error); }
        LinkTokenManager.Login login = tokens.currentLogin();
        BooleanSupplier current = () -> grant == null ? tokens.isCurrent(login) : tokens.isCurrent(grant);
        Request.Builder request = new Request.Builder().url(parsed)
            .header("Accept", "application/json").header("User-Agent", PluginVersion.userAgent())
            .header("Content-Type", "application/json");
        if (grant != null) grant.applyTo(request);
        RequestBody payload = body == null ? null : RequestBody.create(JSON, body.getBytes(StandardCharsets.UTF_8));
        if ("POST".equals(method)) request.post(payload != null ? payload : RequestBody.create(JSON, new byte[0]));
        else if ("DELETE".equals(method)) request.delete(payload);
        CompletableFuture<T> transport = scope.request(grant == null ? httpClient : credentialedClient(), request.build(), response -> {
            tokens.observeServerTime(response);
            if (!current.getAsBoolean()) throw new CancellationException();
            if (response.isRedirect()) throw new IOException("Unexpected redirect from the Reval API");
            if (!response.isSuccessful()) {
                ServerError error = ServerError.read(response, gson);
                if (grant != null && response.code() == 401) {
                    if (error.is(ServerError.INVALID_PLUGIN_CREDENTIAL)) {
                        tokens.rejected(grant);
                        throw new LinkException(LinkException.Kind.EXPIRED);
                    }
                    throw new LinkException(LinkException.Kind.REFUSED);
                }
                throw new IOException(failureMessage(error, type, response.code()));
            }
            if (response.body() == null) throw new IOException("Empty response body");
            T value = gson.fromJson(response.body().string(), type);
            if (!current.getAsBoolean()) throw new CancellationException();
            if (value == null) throw new IOException("Empty or invalid response");
            if (!value.isSuccess()) throw new IOException(value.getErrorMessage() != null ? value.getErrorMessage() : "Request failed");
            return value;
        });
        CompletableFuture<T> result = scope.track(new CompletableFuture<>());
        result.whenComplete((value, error) -> { if (result.isCancelled()) transport.cancel(false); });
        transport.whenComplete((value, error) -> {
            // Cancellation still completes the caller, without exposing stale data or old errors.
            // A terminal credential rejection keeps its explanation for the original login.
            if (!current.getAsBoolean() && !(error instanceof LinkException && tokens.isCurrent(login)))
                result.cancel(false);
            else if (error != null) result.completeExceptionally(error);
            else result.complete(value);
        });
        return result;
    }

    /** The server's reason from an already-read error body, in the shape of the envelope the caller expects, else the status. */
    private <T extends ApiEnvelope> String failureMessage(ServerError error, Class<T> responseClass, int status) {
        T envelope = null;
        if (error.json() != null) {
            try {
                envelope = gson.fromJson(error.json(), responseClass);
            } catch (RuntimeException ignored) {
                // not the expected shape
            }
        }
        String message = envelope != null ? ServerError.oneLine(envelope.getErrorMessage()) : null;
        return message != null ? message : "HTTP " + status;
    }
}

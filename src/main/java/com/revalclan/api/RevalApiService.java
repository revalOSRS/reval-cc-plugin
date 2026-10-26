package com.revalclan.api;

import com.google.gson.Gson;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.achievements.AchievementsResponse;
import com.revalclan.api.leaderboard.LeaderboardResponse;
import com.revalclan.api.admin.ActualizeRankChangeResponse;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.api.admin.AdminLoginRequest;
import com.revalclan.api.admin.PendingRankChangesResponse;
import com.revalclan.auth.ApiOrigin;
import com.revalclan.auth.LinkException;
import com.revalclan.auth.LinkTokenManager;
import com.revalclan.auth.SecureHttp;
import com.revalclan.auth.ServerError;
import com.revalclan.api.challenges.ChallengesResponse;
import com.revalclan.api.leagues.LeaguesConfigResponse;
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
import com.revalclan.util.PluginVersion;
import com.revalclan.util.BoundedImageDecoder;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for fetching data from the Reval Plugin API.
 * <p>
 * Requests are authenticated with the bearer token of the logged-in game
 * account's link ({@link LinkTokenManager}); no identity travels in any other
 * header. Mutations, admin and notification calls and every read that names an
 * account ({@code accountHash} query) require a link and fail locally, before
 * anything is sent, when there is none or when the hash is not the logged-in
 * account's. Every other read is public and anonymous. Whatever the
 * authentication, a response (or failure) is dropped if the account that made
 * the request has logged out or been switched away from in the meantime.
 * <p>
 * Only a 401 whose body says {@code invalid_plugin_credential} removes a link;
 * any other 401 keeps it.
 */
@Singleton
public class RevalApiService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** How a request authenticates. */
    private enum Auth {
        /** Public data: never a token. */
        NONE,
        /** Private data, a change, or anything that names an account: fails locally unless the logged-in account is linked. */
        REQUIRED
    }

    private final Gson gson;
    private final OkHttpClient httpClient;
    private final LinkTokenManager tokens;
    private final ApiOrigin origin;
    private volatile OkHttpClient credentialedClient;

    // Cache durations
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000;
    private static final long ACCOUNT_CACHE_DURATION_MS = 2 * 60 * 1000;

    // Cached responses
    private PointsResponse cachedPoints;
    private long lastPointsFetch = 0;
    private AccountResponse cachedAccount;
    private String cachedAccountIdentifier;
    private long lastAccountFetch = 0;
    private ActiveTeamsResponse cachedActiveTeams;
    private long lastActiveTeamsFetch = 0;
    private AchievementsResponse cachedAchievements;
    private long lastAchievementsFetch = 0;
    private DiariesResponse cachedDiaries;
    private long lastDiariesFetch = 0;
    private ChallengesResponse cachedChallenges;
    private long lastChallengesFetch = 0;

    // Leagues config - session-level cache (no TTL, cleared on logout)
    private LeaguesConfigResponse.LeaguesConfig cachedLeaguesConfig;

    @Inject
    public RevalApiService(OkHttpClient httpClient, Gson gson, LinkTokenManager tokens) {
        this(httpClient, gson, tokens, ApiOrigin.PRODUCTION);
    }

    RevalApiService(OkHttpClient httpClient, Gson gson, LinkTokenManager tokens, ApiOrigin origin) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.tokens = tokens;
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

    // ==================== POINTS API ====================

    public void fetchPoints(Consumer<PointsResponse> onSuccess, Consumer<Exception> onError) {
        if (cachedPoints != null && System.currentTimeMillis() - lastPointsFetch < CACHE_DURATION_MS) {
            onSuccess.accept(cachedPoints);
            return;
        }
        get(ApiEndpoints.POINTS, PointsResponse.class, response -> {
            cachedPoints = response;
            lastPointsFetch = System.currentTimeMillis();
            onSuccess.accept(response);
        }, onError);
    }

    // ==================== ACCOUNT API ====================

    public void fetchAccount(long accountHash, Consumer<AccountResponse> onSuccess, Consumer<Exception> onError) {
        String identifier = String.valueOf(accountHash);
        if (cachedAccount != null && identifier.equals(cachedAccountIdentifier)
            && System.currentTimeMillis() - lastAccountFetch < ACCOUNT_CACHE_DURATION_MS) {
            onSuccess.accept(cachedAccount);
            return;
        }
        get(ApiEndpoints.ACCOUNT + "?accountHash=" + accountHash, AccountResponse.class, response -> {
            cachedAccount = response;
            cachedAccountIdentifier = identifier;
            lastAccountFetch = System.currentTimeMillis();
            onSuccess.accept(response);
        }, onError);
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
    private long eventsGeneration;

    /** Successful event responses, or an empty response on session reset. Never mutate Swing directly here. */
    public void addEventsListener(Consumer<EventsResponse> listener) {
        eventsListeners.add(listener);
    }

    /** End the session without allowing an old in-flight response to restore its marks. */
    public synchronized void resetEventsSession() {
        eventsGeneration++;
        eventsListeners.forEach(listener -> listener.accept(new EventsResponse()));
    }

    public void fetchEvents(Consumer<EventsResponse> onSuccess, Consumer<Exception> onError) {
        final long generation;
        synchronized (this) { generation = ++eventsGeneration; }
        get(ApiEndpoints.EVENTS, EventsResponse.class, response -> {
            synchronized (this) {
                if (generation != eventsGeneration) return;
                eventsListeners.forEach(listener -> listener.accept(response));
                onSuccess.accept(response);
            }
        }, error -> {
            synchronized (this) {
                if (generation == eventsGeneration) onError.accept(error);
            }
        });
    }

    public void fetchActiveTeams(Consumer<ActiveTeamsResponse> onSuccess, Consumer<Exception> onError) {
        if (cachedActiveTeams != null && System.currentTimeMillis() - lastActiveTeamsFetch < CACHE_DURATION_MS) {
            onSuccess.accept(cachedActiveTeams);
            return;
        }
        get(ApiEndpoints.EVENTS_ACTIVE_TEAMS, ActiveTeamsResponse.class, response -> {
            cachedActiveTeams = response;
            lastActiveTeamsFetch = System.currentTimeMillis();
            onSuccess.accept(response);
        }, onError);
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
        getPrivate(ApiEndpoints.leaguesBingoMe(eventId, accountHash), LeaguesBingoMeResponse.class, onSuccess, onError);
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

    public void fetchAchievementDefinitions(Long accountHash,
                                           Consumer<AchievementsResponse> onSuccess, Consumer<Exception> onError) {
        if (accountHash == null && cachedAchievements != null 
            && System.currentTimeMillis() - lastAchievementsFetch < CACHE_DURATION_MS) {
            onSuccess.accept(cachedAchievements);
            return;
        }
        String endpoint = accountHash != null 
            ? ApiEndpoints.ACHIEVEMENTS + "?accountHash=" + accountHash
            : ApiEndpoints.ACHIEVEMENTS;
        get(endpoint, AchievementsResponse.class, response -> {
            if (accountHash == null) {
                cachedAchievements = response;
                lastAchievementsFetch = System.currentTimeMillis();
            }
            onSuccess.accept(response);
        }, onError);
    }

    public void fetchAchievementDefinitions(Consumer<AchievementsResponse> onSuccess, Consumer<Exception> onError) {
        fetchAchievementDefinitions(null, onSuccess, onError);
    }

    // ==================== DIARIES API ====================

    public void fetchDiaries(Long accountHash, Consumer<DiariesResponse> onSuccess, Consumer<Exception> onError) {
        if (cachedDiaries != null && accountHash == null 
            && System.currentTimeMillis() - lastDiariesFetch < CACHE_DURATION_MS) {
            onSuccess.accept(cachedDiaries);
            return;
        }
        String endpoint = accountHash != null 
            ? ApiEndpoints.DIARIES + "?accountHash=" + accountHash
            : ApiEndpoints.DIARIES;
        get(endpoint, DiariesResponse.class, response -> {
            if (accountHash == null) {
                cachedDiaries = response;
                lastDiariesFetch = System.currentTimeMillis();
            }
            onSuccess.accept(response);
        }, onError);
    }

    public void fetchDiaries(Consumer<DiariesResponse> onSuccess, Consumer<Exception> onError) {
        fetchDiaries(null, onSuccess, onError);
    }

    // ==================== CHALLENGES API ====================

    public void fetchChallenges(Long accountHash, Consumer<ChallengesResponse> onSuccess, Consumer<Exception> onError) {
        if (accountHash == null && cachedChallenges != null 
            && System.currentTimeMillis() - lastChallengesFetch < CACHE_DURATION_MS) {
            onSuccess.accept(cachedChallenges);
            return;
        }
        String endpoint = accountHash != null 
            ? ApiEndpoints.CHALLENGES + "?accountHash=" + accountHash
            : ApiEndpoints.CHALLENGES;
        get(endpoint, ChallengesResponse.class, response -> {
            if (accountHash == null) {
                cachedChallenges = response;
                lastChallengesFetch = System.currentTimeMillis();
            }
            onSuccess.accept(response);
        }, onError);
    }
    
    public void fetchChallenges(Consumer<ChallengesResponse> onSuccess, Consumer<Exception> onError) {
        fetchChallenges(null, onSuccess, onError);
    }

    // ==================== LEAGUES API ====================

    public void fetchLeaguesConfig(Consumer<LeaguesConfigResponse.LeaguesConfig> onSuccess, Consumer<Exception> onError) {
        if (cachedLeaguesConfig != null) {
            onSuccess.accept(cachedLeaguesConfig);
            return;
        }
        get(ApiEndpoints.LEAGUES_CONFIG, LeaguesConfigResponse.class, response -> {
            cachedLeaguesConfig = response.getData();
            onSuccess.accept(cachedLeaguesConfig);
        }, onError);
    }

    public LeaguesConfigResponse.LeaguesConfig getCachedLeaguesConfig() {
        return cachedLeaguesConfig;
    }

    public void clearLeaguesCache() {
        cachedLeaguesConfig = null;
    }

    // ==================== COMPETITIONS API ====================

    public void fetchFeaturedRace(Consumer<FeaturedRaceResponse> onSuccess, Consumer<Exception> onError) {
        getPublic(ApiEndpoints.publicUrl(ApiEndpoints.PUBLIC_FEATURED_RACE), FeaturedRaceResponse.class, onSuccess, onError);
    }

    /** Banner and task artwork is optional; decoding and network IO stay off the Swing thread. */
    public void fetchCompetitionArtwork(String imageUrl, Consumer<BufferedImage> onSuccess, Consumer<Exception> onError) {
        String source = imageUrl.trim();
        HttpUrl url = HttpUrl.parse(source.startsWith("/") ? "https://www.revalosrs.ee" + source : source);
        if (url == null || !"https".equals(url.scheme())) {
            onError.accept(new IOException("Invalid event image URL"));
            return;
        }
        httpClient.newBuilder().callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
            .newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) { onError.accept(error); }
            @Override public void onResponse(Call call, Response response) {
                try (Response result = response) {
                    if (!result.isSuccessful() || result.body() == null) throw new IOException("Event image unavailable");
                    if (result.body().contentLength() > BoundedImageDecoder.MAX_BYTES) throw new IOException("Event image exceeds the download limit");
                    BufferedImage image = BoundedImageDecoder.read(result.body().byteStream());
                    if (image == null) throw new IOException("Unsupported event image format");
                    onSuccess.accept(image);
                } catch (IOException error) { onError.accept(error); }
            }
        });
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
    // Authority comes from the linked account (bearer token) and the server's admin list.

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

    /** @deprecated The numeric member code no longer authenticates anything; it is ignored and never sent. */
    @Deprecated
    public void adminLogin(String ignoredMemberCode, Long accountHash, String osrsNickname,
                           Consumer<AdminAuthResponse> onSuccess, Consumer<Exception> onError) {
        adminLogin(accountHash, osrsNickname, onSuccess, onError);
    }

    /** @deprecated The numeric member code no longer authenticates anything; it is ignored and never sent. */
    @Deprecated
    public void fetchPendingRankChanges(String ignoredMemberCode,
                                        Consumer<PendingRankChangesResponse> onSuccess, Consumer<Exception> onError) {
        fetchPendingRankChanges(onSuccess, onError);
    }

    /** @deprecated The numeric member code no longer authenticates anything; it is ignored and never sent. */
    @Deprecated
    public void actualizeRankChange(String ignoredMemberCode, int rankChangeId,
                                    Consumer<ActualizeRankChangeResponse> onSuccess, Consumer<Exception> onError) {
        actualizeRankChange(rankChangeId, onSuccess, onError);
    }

    // ==================== ANNOUNCEMENTS API ====================

    public void fetchAnnouncements(Consumer<AnnouncementsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.ANNOUNCEMENTS, AnnouncementsResponse.class, onSuccess, onError);
    }

    // ==================== NOTIFICATIONS API ====================

    public void fetchNotifications(long accountHash, Consumer<NotificationsResponse> onSuccess, Consumer<Exception> onError) {
        getPrivate(ApiEndpoints.NOTIFICATIONS + "?accountHash=" + accountHash,
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
        cachedPoints = null;
        lastPointsFetch = 0;
        cachedAccount = null;
        cachedAccountIdentifier = null;
        lastAccountFetch = 0;
        cachedActiveTeams = null;
        lastActiveTeamsFetch = 0;
        cachedAchievements = null;
        lastAchievementsFetch = 0;
        cachedDiaries = null;
        lastDiariesFetch = 0;
        cachedChallenges = null;
        lastChallengesFetch = 0;
        cachedLeaguesConfig = null;
    }

    public void clearAccountCache() {
        cachedAccount = null;
        cachedAccountIdentifier = null;
        lastAccountFetch = 0;
        cachedAchievements = null;
        lastAchievementsFetch = 0;
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

    /**
     * One HTTP round-trip; callers pass a full URL and the envelope type they expect.
     *
     * @param bodyAccountHash the account hash inside the body, if any: it must be the logged-in account's
     */
    private <T extends ApiEnvelope> void request(String url, String method, String body, Auth auth,
                                                 Long bodyAccountHash, Class<T> responseClass,
                                                 Consumer<T> onSuccess, Consumer<Exception> onError) {
        HttpUrl parsedUrl = HttpUrl.parse(url);
        if (parsedUrl == null) {
            onError.accept(new IOException("Invalid request URL"));
            return;
        }

        LinkTokenManager.Grant grant = null;
        if (auth == Auth.REQUIRED) {
            try {
                grant = tokens.grantForCurrentAccount(parsedUrl, bodyAccountHash);
            } catch (LinkException e) {
                onError.accept(e);
                return;
            }
        }
        // The login this request belongs to, with or without a token: an answer for an ended login is dropped.
        final LinkTokenManager.Login login = grant != null ? grant.login() : tokens.currentLogin();

        Request.Builder requestBuilder = new Request.Builder()
            .url(parsedUrl)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", PluginVersion.userAgent())
            .addHeader("Content-Type", "application/json");

        if (grant != null) {
            grant.applyTo(requestBuilder);
        }

        if (body != null && !body.isEmpty()) {
            RequestBody requestBody = RequestBody.create(JSON, body.getBytes(StandardCharsets.UTF_8));
            if ("POST".equals(method)) {
                requestBuilder.post(requestBody);
            } else if ("DELETE".equals(method)) {
                requestBuilder.delete(requestBody);
            }
        } else {
            if ("POST".equals(method)) {
                requestBuilder.post(RequestBody.create(JSON, new byte[0]));
            } else if ("DELETE".equals(method)) {
                requestBuilder.delete();
            } else {
                requestBuilder.get();
            }
        }

        final LinkTokenManager.Grant sentWith = grant;
        OkHttpClient client = grant != null ? credentialedClient() : httpClient;
        client.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!tokens.isCurrent(login)) return;
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    tokens.observeServerTime(response);
                    if (!tokens.isCurrent(login)) return;

                    if (response.isRedirect()) {
                        onError.accept(new IOException("Unexpected redirect from the Reval API"));
                        return;
                    }

                    if (!response.isSuccessful()) {
                        ServerError error = ServerError.read(response, gson);
                        if (sentWith != null && response.code() == 401) {
                            if (error.is(ServerError.INVALID_PLUGIN_CREDENTIAL)) {
                                // The server itself says this token is revoked, expired or unknown.
                                tokens.rejected(sentWith);
                                onError.accept(new LinkException(LinkException.Kind.EXPIRED));
                            } else {
                                // Some other 401: the token may be perfectly good, so it is kept.
                                onError.accept(new LinkException(LinkException.Kind.REFUSED));
                            }
                            return;
                        }
                        onError.accept(new Exception(failureMessage(error, responseClass, response.code())));
                        return;
                    }

                    if (response.body() == null) {
                        onError.accept(new Exception("Empty response body"));
                        return;
                    }

                    String jsonResponse = response.body().string();
                    T parsedResponse = gson.fromJson(jsonResponse, responseClass);

                    if (!tokens.isCurrent(login)) return;
                    if (parsedResponse == null) {
                        onError.accept(new Exception("Failed to parse response"));
                    } else if (!parsedResponse.isSuccess()) {
                        onError.accept(new Exception(parsedResponse.getErrorMessage() != null
                            ? parsedResponse.getErrorMessage() : "Request failed"));
                    } else {
                        onSuccess.accept(parsedResponse);
                    }
                } catch (Exception e) {
                    onError.accept(e);
                } finally {
                    response.close();
                }
            }
        });
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

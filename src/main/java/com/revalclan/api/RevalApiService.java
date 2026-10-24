package com.revalclan.api;

import com.google.gson.Gson;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.achievements.AchievementsResponse;
import com.revalclan.api.leaderboard.LeaderboardResponse;
import com.revalclan.api.admin.ActualizeRankChangeResponse;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.api.admin.AdminLoginRequest;
import com.revalclan.api.admin.PendingRankChangesResponse;
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
import com.revalclan.util.RequestScope;
import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
 */
@Singleton
public class RevalApiService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Gson gson;
    private final OkHttpClient httpClient;
    private volatile RequestScope requests = new RequestScope();

    public void startUp() { requests.cancel(); requests = new RequestScope(); }
    public void shutDown() { requests.cancel(); clearCache(); resetEventsSession(); }

    // Cache durations
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000;
    private static final long ACCOUNT_CACHE_DURATION_MS = 2 * 60 * 1000;

    private final CachedFetch<PointsResponse> points = new CachedFetch<>(CACHE_DURATION_MS);
    private final CachedFetch<AccountResponse> account = new CachedFetch<>(ACCOUNT_CACHE_DURATION_MS);
    private final CachedFetch<ActiveTeamsResponse> teams = new CachedFetch<>(CACHE_DURATION_MS);
    private final CachedFetch<AchievementsResponse> achievements = new CachedFetch<>(CACHE_DURATION_MS);
    private final CachedFetch<DiariesResponse> diaries = new CachedFetch<>(CACHE_DURATION_MS);
    private final CachedFetch<ChallengesResponse> challenges = new CachedFetch<>(CACHE_DURATION_MS);
    private final CachedFetch<LeaguesConfigResponse> leagues = new CachedFetch<>(0);

    @Inject
    public RevalApiService(@Named("reval-http") OkHttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    // ==================== POINTS API ====================

    public void fetchPoints(Consumer<PointsResponse> onSuccess, Consumer<Exception> onError) {
        observe(points.get(null, () -> getFuture(ApiEndpoints.POINTS, PointsResponse.class)), onSuccess, onError);
    }

    // ==================== ACCOUNT API ====================

    public void fetchAccount(long accountHash, Consumer<AccountResponse> onSuccess, Consumer<Exception> onError) {
        observe(account.get(accountHash, () -> getFuture(ApiEndpoints.ACCOUNT + "?accountHash=" + accountHash,
            AccountResponse.class)), onSuccess, onError);
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
    private volatile RequestScope eventsScope = new RequestScope();

    /** Successful event responses, or an empty response on session reset. Never mutate Swing directly here. */
    public void addEventsListener(Consumer<EventsResponse> listener) {
        eventsListeners.add(listener);
    }

    /** End the session without allowing an old in-flight response to restore its marks. */
    public void resetEventsSession() {
        eventsScope.cancel();
        eventsScope = new RequestScope();
        eventsListeners.forEach(listener -> listener.accept(new EventsResponse()));
    }

    public void fetchEvents(Consumer<EventsResponse> onSuccess, Consumer<Exception> onError) {
        eventsScope.cancel();
        RequestScope owner = eventsScope = new RequestScope();
        observe(owner.track(getFuture(ApiEndpoints.EVENTS, EventsResponse.class)), response -> {
            if (owner.isActive()) eventsListeners.forEach(listener -> listener.accept(response));
            onSuccess.accept(response);
        }, onError);
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
        post(ApiEndpoints.leaguesBingoPick(eventId), gson.toJson(body), LeaguesBingoPickResponse.class, onSuccess, onError);
    }

    public void registerForEvent(String eventId, long accountHash,
                                 Consumer<RegistrationResponse> onSuccess, Consumer<Exception> onError) {
        post(ApiEndpoints.eventRegister(eventId), "{\"accountHash\":\"" + accountHash + "\"}", 
            RegistrationResponse.class, onSuccess, onError);
    }

    public void cancelEventRegistration(String eventId, long accountHash,
                                        Consumer<RegistrationResponse> onSuccess, Consumer<Exception> onError) {
        delete(ApiEndpoints.eventRegister(eventId), "{\"accountHash\":\"" + accountHash + "\"}",
            RegistrationResponse.class, onSuccess, onError);
    }

    // ==================== ACHIEVEMENTS API ====================

    public void fetchAchievementDefinitions(Long accountHash, Consumer<AchievementsResponse> onSuccess, Consumer<Exception> onError) {
        if (accountHash == null) {
            observe(achievements.get(null, () -> getFuture(ApiEndpoints.ACHIEVEMENTS, AchievementsResponse.class)), onSuccess, onError);
        } else {
            get(ApiEndpoints.ACHIEVEMENTS + "?accountHash=" + accountHash, AchievementsResponse.class, onSuccess, onError);
        }
    }

    public void fetchAchievementDefinitions(Consumer<AchievementsResponse> onSuccess, Consumer<Exception> onError) {
        fetchAchievementDefinitions(null, onSuccess, onError);
    }

    // ==================== DIARIES API ====================

    public void fetchDiaries(Long accountHash, Consumer<DiariesResponse> onSuccess, Consumer<Exception> onError) {
        if (accountHash == null) {
            observe(diaries.get(null, () -> getFuture(ApiEndpoints.DIARIES, DiariesResponse.class)), onSuccess, onError);
        } else {
            get(ApiEndpoints.DIARIES + "?accountHash=" + accountHash, DiariesResponse.class, onSuccess, onError);
        }
    }

    public void fetchDiaries(Consumer<DiariesResponse> onSuccess, Consumer<Exception> onError) {
        fetchDiaries(null, onSuccess, onError);
    }

    // ==================== CHALLENGES API ====================

    public void fetchChallenges(Long accountHash, Consumer<ChallengesResponse> onSuccess, Consumer<Exception> onError) {
        if (accountHash == null) {
            observe(challenges.get(null, () -> getFuture(ApiEndpoints.CHALLENGES, ChallengesResponse.class)), onSuccess, onError);
        } else {
            get(ApiEndpoints.CHALLENGES + "?accountHash=" + accountHash, ChallengesResponse.class, onSuccess, onError);
        }
    }
    
    public void fetchChallenges(Consumer<ChallengesResponse> onSuccess, Consumer<Exception> onError) {
        fetchChallenges(null, onSuccess, onError);
    }

    // ==================== LEAGUES API ====================

    public void fetchLeaguesConfig(Consumer<LeaguesConfigResponse.LeaguesConfig> onSuccess, Consumer<Exception> onError) {
        observe(leagues.get(null, () -> getFuture(ApiEndpoints.LEAGUES_CONFIG, LeaguesConfigResponse.class)),
            response -> onSuccess.accept(response.getData()), onError);
    }

    public LeaguesConfigResponse.LeaguesConfig getCachedLeaguesConfig() {
        LeaguesConfigResponse response = leagues.peek();
        return response != null ? response.getData() : null;
    }

    public void clearLeaguesCache() { leagues.clear(); }

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
        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) { onError.accept(error); }
            @Override public void onResponse(Call call, Response response) {
                try (Response result = response) {
                    if (!result.isSuccessful() || result.body() == null) throw new IOException("Event image unavailable");
                    BufferedImage image = ImageIO.read(result.body().byteStream());
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
        post(ApiEndpoints.voteCast(voteId) + "?accountHash=" + accountHash,
            "{\"optionId\":\"" + optionId + "\"}", CastVoteResponse.class, onSuccess, onError);
    }

    public void fetchMyVote(String voteId, long accountHash,
                            Consumer<MyVoteResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.voteMyVote(voteId) + "?accountHash=" + accountHash,
            MyVoteResponse.class, onSuccess, onError);
    }

    // ==================== ADMIN API ====================

    public void adminLogin(String memberCode, Long accountHash, String osrsNickname,
                           Consumer<AdminAuthResponse> onSuccess, Consumer<Exception> onError) {
        AdminLoginRequest requestBody = new AdminLoginRequest(
            accountHash != null ? String.valueOf(accountHash) : null,
            osrsNickname
        );
        postAdmin(ApiEndpoints.ADMIN_AUTH_LOGIN, gson.toJson(requestBody), memberCode,
            AdminAuthResponse.class, onSuccess, onError);
    }

    public void fetchPendingRankChanges(String memberCode,
                                        Consumer<PendingRankChangesResponse> onSuccess, Consumer<Exception> onError) {
        getAdmin(ApiEndpoints.ADMIN_RANK_CHANGES_PENDING + "?limit=100", memberCode,
            PendingRankChangesResponse.class, onSuccess, onError);
    }

    public void actualizeRankChange(String memberCode, int rankChangeId,
                                    Consumer<ActualizeRankChangeResponse> onSuccess, Consumer<Exception> onError) {
        postAdmin(ApiEndpoints.rankChangeActualize(rankChangeId), null, memberCode,
            ActualizeRankChangeResponse.class, onSuccess, onError);
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
        post(ApiEndpoints.NOTIFICATIONS_ACK, gson.toJson(body),
            NotificationAckResponse.class, onSuccess, onError);
    }

    // ==================== CACHE MANAGEMENT ====================

    public void clearCache() {
        points.clear(); account.clear(); teams.clear(); achievements.clear();
        diaries.clear(); challenges.clear(); leagues.clear();
    }

    public void clearAccountCache() { account.clear(); achievements.clear(); }

    // ==================== HTTP HELPERS ====================

    private <T extends ApiResponse> void get(String endpoint, Class<T> responseClass,
                                             Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(ApiEndpoints.url(endpoint), "GET", null, null, responseClass, onSuccess, onError);
    }

    /** GET against the public API (full URL, {success, data} envelope). */
    private <T extends PublicApiResponse> void getPublic(String url, Class<T> responseClass,
                                                         Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(url, "GET", null, null, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void getAdmin(String endpoint, String memberCode, Class<T> responseClass,
                                                  Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(ApiEndpoints.url(endpoint), "GET", null, memberCode, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void post(String endpoint, String body, Class<T> responseClass,
                                              Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(ApiEndpoints.url(endpoint), "POST", body, null, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void postAdmin(String endpoint, String body, String memberCode,
                                                   Class<T> responseClass, Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(ApiEndpoints.url(endpoint), "POST", body, memberCode, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void delete(String endpoint, String body, Class<T> responseClass,
                                                Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(ApiEndpoints.url(endpoint), "DELETE", body, null, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> CompletableFuture<T> getFuture(String endpoint, Class<T> type) {
        return requestFuture(ApiEndpoints.url(endpoint), "GET", null, null, type);
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

    private <T extends ApiEnvelope> void request(String url, String method, String body, String memberCode,
                                                 Class<T> type, Consumer<T> success, Consumer<Exception> failure) {
        observe(requestFuture(url, method, body, memberCode, type), success, failure);
    }

    private <T extends ApiEnvelope> CompletableFuture<T> requestFuture(String url, String method, String body,
                                                                      String memberCode, Class<T> type) {
        Request.Builder request = new Request.Builder().url(url)
            .header("Accept", "application/json").header("User-Agent", PluginVersion.userAgent());
        if (memberCode != null && !memberCode.isEmpty()) request.header("X-Member-Code", memberCode);
        RequestBody payload = body == null ? null : RequestBody.create(JSON, body.getBytes(StandardCharsets.UTF_8));
        if ("POST".equals(method)) request.post(payload != null ? payload : RequestBody.create(JSON, new byte[0]));
        else if ("DELETE".equals(method)) request.delete(payload);
        return requests.request(httpClient, request.build(), response -> {
            String json = response.body() == null ? null : response.body().string();
            T parsed = null;
            if (json != null && !json.isEmpty()) {
                try { parsed = gson.fromJson(json, type); }
                catch (RuntimeException error) { if (response.isSuccessful()) throw error; }
            }
            if (!response.isSuccessful()) throw new IOException(parsed != null && parsed.getErrorMessage() != null
                ? parsed.getErrorMessage() : "HTTP " + response.code());
            if (parsed == null) throw new IOException("Empty or invalid response");
            if (!parsed.isSuccess()) throw new IOException(parsed.getErrorMessage() != null ? parsed.getErrorMessage() : "Request failed");
            return parsed;
        });
    }
}

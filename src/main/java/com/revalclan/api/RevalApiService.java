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
import com.revalclan.api.competitions.*;
import com.revalclan.api.common.ApiEndpoints;
import com.revalclan.api.common.ApiResponse;
import com.revalclan.api.diaries.DiariesResponse;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.api.events.RegistrationResponse;
import com.revalclan.api.events.RegistrationStatusResponse;
import com.revalclan.api.points.PointsResponse;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Service for fetching data from the Reval Plugin API.
 */
@Singleton
public class RevalApiService {
    private static final String USER_AGENT = "RuneLite-RevalClan-Plugin";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Gson gson;
    private final OkHttpClient httpClient;

    // Cache durations
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000;
    private static final long ACCOUNT_CACHE_DURATION_MS = 2 * 60 * 1000;
    private static final long EVENTS_CACHE_DURATION_MS = 60 * 1000;

    // Cached responses
    private PointsResponse cachedPoints;
    private long lastPointsFetch = 0;
    private AccountResponse cachedAccount;
    private String cachedAccountIdentifier;
    private long lastAccountFetch = 0;
    private EventsResponse cachedEvents;
    private long lastEventsFetch = 0;
    private AchievementsResponse cachedAchievements;
    private long lastAchievementsFetch = 0;
    private DiariesResponse cachedDiaries;
    private long lastDiariesFetch = 0;
    private ChallengesResponse cachedChallenges;
    private long lastChallengesFetch = 0;

    @Inject
    public RevalApiService(OkHttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
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

    public void refreshAccount(long accountHash, Consumer<AccountResponse> onSuccess, Consumer<Exception> onError) {
        clearAccountCache();
        fetchAccount(accountHash, onSuccess, onError);
    }

    public void fetchAccountById(int osrsAccountId, Consumer<AccountResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.accountById(osrsAccountId), AccountResponse.class, onSuccess, onError);
    }

    // ==================== LEADERBOARD API ====================

    public void fetchLeaderboard(Consumer<LeaderboardResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.LEADERBOARD, LeaderboardResponse.class, onSuccess, onError);
    }

    // ==================== EVENTS API ====================

    public void fetchEvents(Consumer<EventsResponse> onSuccess, Consumer<Exception> onError) {
        if (cachedEvents != null && System.currentTimeMillis() - lastEventsFetch < EVENTS_CACHE_DURATION_MS) {
            onSuccess.accept(cachedEvents);
            return;
        }
        get(ApiEndpoints.EVENTS, EventsResponse.class, response -> {
            cachedEvents = response;
            lastEventsFetch = System.currentTimeMillis();
            onSuccess.accept(response);
        }, onError);
    }

    public void refreshEvents(Consumer<EventsResponse> onSuccess, Consumer<Exception> onError) {
        cachedEvents = null;
        lastEventsFetch = 0;
        fetchEvents(onSuccess, onError);
    }

    public void registerForEvent(String eventId, long accountHash,
                                 Consumer<RegistrationResponse> onSuccess, Consumer<Exception> onError) {
        post(ApiEndpoints.eventRegister(eventId), "{\"accountHash\":\"" + accountHash + "\"}", 
            RegistrationResponse.class, response -> {
                cachedEvents = null;
                onSuccess.accept(response);
            }, onError);
    }

    public void cancelEventRegistration(String eventId, long accountHash,
                                        Consumer<RegistrationResponse> onSuccess, Consumer<Exception> onError) {
        delete(ApiEndpoints.eventRegister(eventId), "{\"accountHash\":\"" + accountHash + "\"}",
            RegistrationResponse.class, response -> {
                cachedEvents = null;
                onSuccess.accept(response);
            }, onError);
    }

    public void checkRegistrationStatus(String eventId, long accountHash,
                                        Consumer<RegistrationStatusResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.eventRegistrationStatus(eventId) + "?accountHash=" + accountHash,
            RegistrationStatusResponse.class, onSuccess, onError);
    }

    public void checkActiveEvents(Consumer<Boolean> onResult) {
        fetchEvents(
            response -> {
                if (response.getData() != null && response.getData().getEvents() != null) {
                    boolean hasActive = response.getData().getEvents().stream()
                        .anyMatch(e -> e.isCurrentlyActive() || e.isUpcoming());
                    onResult.accept(hasActive);
                } else {
                    onResult.accept(false);
                }
            },
            error -> onResult.accept(false)
        );
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

    // ==================== COMPETITIONS API ====================

    public void fetchCompetitions(String status, Consumer<CompetitionsResponse> onSuccess, Consumer<Exception> onError) {
        String endpoint = status != null 
            ? ApiEndpoints.COMPETITIONS + "?status=" + status
            : ApiEndpoints.COMPETITIONS;
        get(endpoint, CompetitionsResponse.class, onSuccess, onError);
    }

    public void fetchCompetitions(Consumer<CompetitionsResponse> onSuccess, Consumer<Exception> onError) {
        fetchCompetitions(null, onSuccess, onError);
    }

    public void fetchScheduledCompetitions(Consumer<CompetitionsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.COMPETITIONS_SCHEDULED, CompetitionsResponse.class, onSuccess, onError);
    }

    public void fetchActiveCompetitions(Consumer<CompetitionsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.COMPETITIONS_ACTIVE, CompetitionsResponse.class, onSuccess, onError);
    }

    public void fetchCompletedCompetitions(Consumer<CompetitionsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.COMPETITIONS_COMPLETED, CompetitionsResponse.class, onSuccess, onError);
    }

    public void fetchCompetitionDetails(String competitionId, Consumer<CompetitionDetailsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.competitionById(competitionId), CompetitionDetailsResponse.class, onSuccess, onError);
    }

    public void fetchCompetitionLeaderboard(String competitionId, Consumer<CompetitionLeaderboardResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.competitionLeaderboard(competitionId), CompetitionLeaderboardResponse.class, onSuccess, onError);
    }

    public void fetchCompetitionActivity(String competitionId, Consumer<CompetitionActivityResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.competitionActivity(competitionId), CompetitionActivityResponse.class, onSuccess, onError);
    }

    public void fetchMyCompetitionProgress(String competitionId, long accountHash, 
                                           Consumer<MyProgressResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.competitionMyProgress(competitionId) + "?accountHash=" + accountHash,
            MyProgressResponse.class, onSuccess, onError);
    }

    public void fetchMyAllCompetitionsProgress(long accountHash, 
                                               Consumer<MyProgressAllResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.COMPETITIONS_MY_PROGRESS_ALL + "?accountHash=" + accountHash,
            MyProgressAllResponse.class, onSuccess, onError);
    }

    // ==================== COMPETITION VOTES API ====================

    public void fetchVotes(Consumer<VotesResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.COMPETITION_VOTES, VotesResponse.class, onSuccess, onError);
    }

    public void fetchVoteDetails(String voteId, Consumer<VoteDetailsResponse> onSuccess, Consumer<Exception> onError) {
        get(ApiEndpoints.voteById(voteId), VoteDetailsResponse.class, onSuccess, onError);
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

    public void fetchPendingRankChanges(String memberCode, int limit,
                                        Consumer<PendingRankChangesResponse> onSuccess, Consumer<Exception> onError) {
        getAdmin(ApiEndpoints.ADMIN_RANK_CHANGES_PENDING + "?limit=" + limit, memberCode,
            PendingRankChangesResponse.class, onSuccess, onError);
    }

    public void fetchPendingRankChanges(String memberCode,
                                        Consumer<PendingRankChangesResponse> onSuccess, Consumer<Exception> onError) {
        fetchPendingRankChanges(memberCode, 100, onSuccess, onError);
    }

    public void actualizeRankChange(String memberCode, int rankChangeId,
                                    Consumer<ActualizeRankChangeResponse> onSuccess, Consumer<Exception> onError) {
        postAdmin(ApiEndpoints.rankChangeActualize(rankChangeId), null, memberCode,
            ActualizeRankChangeResponse.class, onSuccess, onError);
    }

    // ==================== CACHE MANAGEMENT ====================

    public void clearCache() {
        cachedPoints = null;
        lastPointsFetch = 0;
        cachedAccount = null;
        cachedAccountIdentifier = null;
        lastAccountFetch = 0;
        cachedEvents = null;
        lastEventsFetch = 0;
        cachedAchievements = null;
        lastAchievementsFetch = 0;
        cachedDiaries = null;
        lastDiariesFetch = 0;
        cachedChallenges = null;
        lastChallengesFetch = 0;
    }

    public void clearAccountCache() {
        cachedAccount = null;
        cachedAccountIdentifier = null;
        lastAccountFetch = 0;
        cachedAchievements = null;
        lastAchievementsFetch = 0;
    }

    // ==================== HTTP HELPERS ====================

    private <T extends ApiResponse> void get(String endpoint, Class<T> responseClass,
                                             Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(endpoint, "GET", null, null, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void getAdmin(String endpoint, String memberCode, Class<T> responseClass,
                                                  Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(endpoint, "GET", null, memberCode, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void post(String endpoint, String body, Class<T> responseClass,
                                              Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(endpoint, "POST", body, null, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void postAdmin(String endpoint, String body, String memberCode,
                                                   Class<T> responseClass, Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(endpoint, "POST", body, memberCode, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void delete(String endpoint, String body, Class<T> responseClass,
                                                Consumer<T> onSuccess, Consumer<Exception> onError) {
        request(endpoint, "DELETE", body, null, responseClass, onSuccess, onError);
    }

    private <T extends ApiResponse> void request(String endpoint, String method, String body,
                                                 String memberCode, Class<T> responseClass,
                                                 Consumer<T> onSuccess, Consumer<Exception> onError) {
        Request.Builder requestBuilder = new Request.Builder()
            .url(ApiEndpoints.BASE_URL + endpoint)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/json");

        if (memberCode != null && !memberCode.isEmpty()) {
            requestBuilder.addHeader("X-Member-Code", memberCode);
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

        httpClient.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : null;
                        T errorResponse = null;
                        if (errorBody != null && !errorBody.isEmpty()) {
                            try {
                                errorResponse = gson.fromJson(errorBody, responseClass);
                            } catch (Exception ignored) {}
                        }
                        onError.accept(new Exception(errorResponse != null && errorResponse.getMessage() != null 
                            ? errorResponse.getMessage() : "HTTP " + response.code()));
                        return;
                    }

                    if (response.body() == null) {
                        onError.accept(new Exception("Empty response body"));
                        return;
                    }

                    String jsonResponse = response.body().string();
                    T parsedResponse = gson.fromJson(jsonResponse, responseClass);
                    
                    if (parsedResponse == null) {
                        onError.accept(new Exception("Failed to parse response"));
                    } else if (!parsedResponse.isSuccess()) {
                        onError.accept(new Exception(parsedResponse.getMessage() != null 
                            ? parsedResponse.getMessage() : "Request failed"));
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
}

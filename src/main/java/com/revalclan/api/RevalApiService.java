package com.revalclan.api;

import com.google.gson.Gson;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.achievements.AchievementsResponse;
import com.revalclan.api.admin.ActualizeRankChangeResponse;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.api.admin.AdminLoginRequest;
import com.revalclan.api.admin.PendingRankChangesResponse;
import com.revalclan.api.challenges.ChallengesResponse;
import com.revalclan.api.common.ApiEndpoints;
import com.revalclan.api.common.ApiResponse;
import com.revalclan.api.diaries.DiariesResponse;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.api.events.RegistrationResponse;
import com.revalclan.api.events.RegistrationStatusResponse;
import com.revalclan.api.points.PointsResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Service for fetching data from the Reval Plugin API.
 * All plugin endpoints require the RuneLite-RevalClan-Plugin User-Agent header.
 */
@Slf4j
@Singleton
public class RevalApiService {
    private static final String USER_AGENT = "RuneLite-RevalClan-Plugin";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Gson gson;
    private final OkHttpClient httpClient;

    // Cache durations
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    private static final long ACCOUNT_CACHE_DURATION_MS = 2 * 60 * 1000; // 2 minutes
    private static final long EVENTS_CACHE_DURATION_MS = 60 * 1000; // 1 minute

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

    /**
     * Fetches points configuration from the API.
     * GET /plugin/points
     */
    public void fetchPoints(Consumer<PointsResponse> onSuccess, Consumer<Exception> onError) {
        if (cachedPoints != null && System.currentTimeMillis() - lastPointsFetch < CACHE_DURATION_MS) {
            onSuccess.accept(cachedPoints);
            return;
        }

        requestAsync(ApiEndpoints.POINTS, "GET", null, null, PointsResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    cachedPoints = response;
                    lastPointsFetch = System.currentTimeMillis();
                    onSuccess.accept(response);
                } else {
                    onError.accept(new Exception(response != null ? response.getMessage() : "Unknown error"));
                }
            },
            error -> {
                log.error("Failed to fetch points", error);
                onError.accept(error);
            }
        );
    }

    // ==================== ACCOUNT API ====================

    /**
     * Fetches account data by account hash.
     * GET /plugin/account?accountHash={accountHash}
     */
    public void fetchAccount(long accountHash, Consumer<AccountResponse> onSuccess, Consumer<Exception> onError) {
        String identifier = String.valueOf(accountHash);

        if (cachedAccount != null && identifier.equals(cachedAccountIdentifier)
            && System.currentTimeMillis() - lastAccountFetch < ACCOUNT_CACHE_DURATION_MS) {
            onSuccess.accept(cachedAccount);
            return;
        }

        String endpoint = ApiEndpoints.ACCOUNT + "?accountHash=" + accountHash;
        requestAsync(endpoint, "GET", null, null, AccountResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    cachedAccount = response;
                    cachedAccountIdentifier = identifier;
                    lastAccountFetch = System.currentTimeMillis();
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Account not found", onError);
                }
            },
            error -> {
                log.error("Failed to fetch account", error);
                onError.accept(error);
            }
        );
    }

    /**
     * Force refresh account data (bypasses cache).
     */
    public void refreshAccount(long accountHash, Consumer<AccountResponse> onSuccess, Consumer<Exception> onError) {
        clearAccountCache();
        fetchAccount(accountHash, onSuccess, onError);
    }

    // ==================== EVENTS API ====================

    /**
     * Fetches all events.
     * GET /plugin/events
     */
    public void fetchEvents(Consumer<EventsResponse> onSuccess, Consumer<Exception> onError) {
        if (cachedEvents != null && System.currentTimeMillis() - lastEventsFetch < EVENTS_CACHE_DURATION_MS) {
            onSuccess.accept(cachedEvents);
            return;
        }

        requestAsync(ApiEndpoints.EVENTS, "GET", null, null, EventsResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    cachedEvents = response;
                    lastEventsFetch = System.currentTimeMillis();
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to fetch events", onError);
                }
            },
            error -> {
                log.error("Failed to fetch events", error);
                onError.accept(error);
            }
        );
    }

    /**
     * Force refresh events (bypasses cache).
     */
    public void refreshEvents(Consumer<EventsResponse> onSuccess, Consumer<Exception> onError) {
        cachedEvents = null;
        lastEventsFetch = 0;
        fetchEvents(onSuccess, onError);
    }

    /**
     * Register for an event.
     * POST /plugin/events/:eventId/register
     */
    public void registerForEvent(String eventId, long accountHash,
                                 Consumer<RegistrationResponse> onSuccess,
                                 Consumer<Exception> onError) {
        String endpoint = ApiEndpoints.eventRegister(eventId);
        String body = "{\"accountHash\":\"" + accountHash + "\"}";
        requestAsync(endpoint, "POST", body, null, RegistrationResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    cachedEvents = null; // Invalidate cache
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Registration failed", onError, "Failed to register for event " + eventId);
                }
            },
            error -> {
                log.error("Failed to register for event: {}", eventId, error);
                onError.accept(error);
            }
        );
    }

    /**
     * Cancel/withdraw event registration.
     * DELETE /plugin/events/:eventId/register
     */
    public void cancelEventRegistration(String eventId, long accountHash,
                                        Consumer<RegistrationResponse> onSuccess,
                                        Consumer<Exception> onError) {
        String endpoint = ApiEndpoints.eventRegister(eventId);
        String body = "{\"accountHash\":\"" + accountHash + "\"}";
        requestAsync(endpoint, "DELETE", body, null, RegistrationResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    cachedEvents = null; // Invalidate cache
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Cancellation failed", onError, "Failed to cancel registration for event " + eventId);
                }
            },
            error -> {
                log.error("Failed to cancel registration for event: {}", eventId, error);
                onError.accept(error);
            }
        );
    }

    /**
     * Check registration status for an event.
     * GET /plugin/events/:eventId/registration-status?accountHash={accountHash}
     */
    public void checkRegistrationStatus(String eventId, long accountHash,
                                        Consumer<RegistrationStatusResponse> onSuccess,
                                        Consumer<Exception> onError) {
        String endpoint = ApiEndpoints.eventRegistrationStatus(eventId) + "?accountHash=" + accountHash;
        requestAsync(endpoint, "GET", null, null, RegistrationStatusResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Status check failed", onError, "Failed to check registration status for event " + eventId);
                }
            },
            error -> {
                log.error("Failed to check registration status for event: {}", eventId, error);
                onError.accept(error);
            }
        );
    }

    /**
     * Check if there are active events.
     */
    public void checkActiveEvents(Consumer<Boolean> onResult) {
        fetchEvents(
            response -> {
                if (response.getData() != null && response.getData().getEvents() != null) {
                    var events = response.getData().getEvents();
                    boolean hasActive = events.stream()
                        .anyMatch(e -> e.isCurrentlyActive() || e.isUpcoming());
                    onResult.accept(hasActive);
                } else {
                    onResult.accept(false);
                }
            },
            error -> {
                log.warn("Could not check for active events: {}", error.getMessage());
                onResult.accept(false);
            }
        );
    }

    // ==================== ACHIEVEMENTS API ====================

    /**
     * Fetches achievement definitions (templates) with optional progress.
     * GET /plugin/achievements?accountHash={accountHash}
     * 
     * @param accountHash Optional account hash to include progress data. If null, returns definitions only.
     */
    public void fetchAchievementDefinitions(Long accountHash,
                                           Consumer<AchievementsResponse> onSuccess,
                                           Consumer<Exception> onError) {
        String endpoint = accountHash != null 
            ? ApiEndpoints.ACHIEVEMENTS + "?accountHash=" + accountHash
            : ApiEndpoints.ACHIEVEMENTS;
        
        requestAsync(endpoint, "GET", null, null, AchievementsResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    if (accountHash == null) {
                        cachedAchievements = response;
                        lastAchievementsFetch = System.currentTimeMillis();
                    }
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to fetch achievement definitions", onError);
                }
            },
            error -> {
                log.error("Failed to fetch achievement definitions", error);
                onError.accept(error);
            }
        );
    }

    /**
     * Fetches achievement definitions without progress.
     */
    public void fetchAchievementDefinitions(Consumer<AchievementsResponse> onSuccess,
                                            Consumer<Exception> onError) {
        fetchAchievementDefinitions(null, onSuccess, onError);
    }

    // ==================== DIARIES API ====================

    /**
     * Fetches all active diaries with optional account progress.
     * GET /plugin/diaries?accountHash={accountHash}
     * 
     * @param accountHash Optional account hash to include progress
     */
    public void fetchDiaries(Long accountHash,
                            Consumer<DiariesResponse> onSuccess, Consumer<Exception> onError) {
        if (cachedDiaries != null && accountHash == null 
            && System.currentTimeMillis() - lastDiariesFetch < CACHE_DURATION_MS) {
            onSuccess.accept(cachedDiaries);
            return;
        }

        String endpoint = accountHash != null 
            ? ApiEndpoints.DIARIES + "?accountHash=" + accountHash
            : ApiEndpoints.DIARIES;
        
        requestAsync(endpoint, "GET", null, null, DiariesResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    if (accountHash == null) {
                        cachedDiaries = response;
                        lastDiariesFetch = System.currentTimeMillis();
                    }
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to fetch diaries", onError);
                }
            },
            error -> {
                log.error("Failed to fetch diaries", error);
                onError.accept(error);
            }
        );
    }

    /**
     * Fetches all diaries without progress (definitions only).
     */
    public void fetchDiaries(Consumer<DiariesResponse> onSuccess, Consumer<Exception> onError) {
        fetchDiaries(null, onSuccess, onError);
    }

    // ==================== CHALLENGES API ====================

    /**
     * Fetches all active weekly and monthly challenges with optional progress tracking.
     * GET /plugin/challenges?accountHash={accountHash}
     * 
     * @param accountHash Optional account hash to include progress data. If null, challenges are returned without progress.
     */
    public void fetchChallenges(Long accountHash, Consumer<ChallengesResponse> onSuccess, Consumer<Exception> onError) {
        // Don't cache if accountHash is provided (progress is account-specific)
        if (accountHash == null && cachedChallenges != null && System.currentTimeMillis() - lastChallengesFetch < CACHE_DURATION_MS) {
            onSuccess.accept(cachedChallenges);
            return;
        }

        String endpoint = accountHash != null 
            ? ApiEndpoints.CHALLENGES + "?accountHash=" + accountHash
            : ApiEndpoints.CHALLENGES;
        
        requestAsync(endpoint, "GET", null, null, ChallengesResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    // Only cache if no accountHash (general challenge list)
                    if (accountHash == null) {
                        cachedChallenges = response;
                        lastChallengesFetch = System.currentTimeMillis();
                    }
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to fetch challenges", onError);
                }
            },
            error -> {
                log.error("Failed to fetch challenges", error);
                onError.accept(error);
            }
        );
    }
    
    /**
     * Fetches all active weekly and monthly challenges without progress data.
     */
    public void fetchChallenges(Consumer<ChallengesResponse> onSuccess, Consumer<Exception> onError) {
        fetchChallenges(null, onSuccess, onError);
    }

    // ==================== ADMIN API ====================

    /**
     * Authenticate admin and retrieve permissions.
     * POST /plugin/admin/auth/login
     *
     * @param memberCode Admin member code for authentication
     * @param accountHash OSRS account hash for verification (optional)
     * @param osrsNickname OSRS nickname for fallback (optional)
     */
    public void adminLogin(String memberCode, Long accountHash, String osrsNickname,
                           Consumer<AdminAuthResponse> onSuccess,
                           Consumer<Exception> onError) {
        AdminLoginRequest requestBody = new AdminLoginRequest(
            accountHash != null ? String.valueOf(accountHash) : null,
            osrsNickname
        );
        String body = gson.toJson(requestBody);

        requestAsync(ApiEndpoints.ADMIN_AUTH_LOGIN, "POST", body, memberCode, AdminAuthResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Admin login failed", onError);
                }
            },
            error -> {
                log.error("Failed to perform admin login", error);
                onError.accept(error);
            }
        );
    }

    /**
     * Fetches pending rank changes (admin only).
     * GET /plugin/admin/rank-changes/pending
     *
     * @param memberCode Admin member code for authentication
     * @param limit Maximum number of pending rank changes to return (default 100)
     */
    public void fetchPendingRankChanges(String memberCode, int limit,
                                        Consumer<PendingRankChangesResponse> onSuccess,
                                        Consumer<Exception> onError) {
        String endpoint = ApiEndpoints.ADMIN_RANK_CHANGES_PENDING + "?limit=" + limit;
        requestAsync(endpoint, "GET", null, memberCode, PendingRankChangesResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to fetch pending rank changes", onError);
                }
            },
            error -> {
                log.error("Failed to fetch pending rank changes", error);
                onError.accept(error);
            }
        );
    }

    /**
     * Fetches pending rank changes with default limit of 100.
     */
    public void fetchPendingRankChanges(String memberCode,
                                        Consumer<PendingRankChangesResponse> onSuccess,
                                        Consumer<Exception> onError) {
        fetchPendingRankChanges(memberCode, 100, onSuccess, onError);
    }

    /**
     * Mark a rank change as actualized (admin only).
     * POST /plugin/admin/rank-changes/:id/actualize
     *
     * @param memberCode Admin member code for authentication
     * @param rankChangeId The ID of the rank change to actualize
     */
    public void actualizeRankChange(String memberCode, int rankChangeId,
                                    Consumer<ActualizeRankChangeResponse> onSuccess,
                                    Consumer<Exception> onError) {
        String endpoint = ApiEndpoints.rankChangeActualize(rankChangeId);
        requestAsync(endpoint, "POST", null, memberCode, ActualizeRankChangeResponse.class,
            response -> {
                if (response != null && response.isSuccess()) {
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to actualize rank change", onError, "Failed to actualize rank change " + rankChangeId);
                }
            },
            error -> {
                log.error("Failed to actualize rank change: {}", rankChangeId, error);
                onError.accept(error);
            }
        );
    }

    // ==================== CACHE MANAGEMENT ====================

    /**
     * Clears all caches, forcing fresh fetches on next request.
     */
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

    /**
     * Clears only the account cache.
     */
    public void clearAccountCache() {
        cachedAccount = null;
        cachedAccountIdentifier = null;
        lastAccountFetch = 0;
        cachedAchievements = null;
        lastAchievementsFetch = 0;
    }

    // ==================== HTTP REQUEST HELPERS ====================

    /**
     * Makes an HTTP request to the plugin API asynchronously using OkHttp.
     *
     * @param endpoint The API endpoint (relative to base URL)
     * @param method HTTP method (GET, POST, DELETE)
     * @param body Request body for POST/DELETE requests (can be null)
     * @param memberCode Admin member code for admin endpoints (can be null)
     * @param responseClass The class to deserialize the response to
     * @param onSuccess Success callback
     * @param onError Error callback
     */
    private <T extends ApiResponse> void requestAsync(String endpoint, String method, String body,
                                                       String memberCode, Class<T> responseClass,
                                                       Consumer<T> onSuccess, Consumer<Exception> onError) {
        String fullUrl = ApiEndpoints.BASE_URL + endpoint;
        
        Request.Builder requestBuilder = new Request.Builder()
            .url(fullUrl)
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

        Request request = requestBuilder.build();

        httpClient.newCall(request).enqueue(new Callback() {
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
                            } catch (Exception e) {
                                // Ignore parsing errors for error responses
                            }
                        }
                        onError.accept(new Exception(errorResponse != null && errorResponse.getMessage() != null 
                            ? errorResponse.getMessage() 
                            : "HTTP " + response.code()));
                        return;
                    }

                    if (response.body() == null) {
                        onError.accept(new Exception("Empty response body"));
                        return;
                    }

                    String jsonResponse = response.body().string();
                    try {
                        T parsedResponse = gson.fromJson(jsonResponse, responseClass);
                        if (parsedResponse != null) {
                            onSuccess.accept(parsedResponse);
                        } else {
                            onError.accept(new Exception("Failed to parse JSON response"));
                        }
                    } catch (Exception e) {
                        onError.accept(new IOException("Failed to parse JSON response", e));
                    }
                } catch (IOException e) {
                    onError.accept(e);
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Helper method to handle API response errors consistently.
     */
    private <T extends ApiResponse> void handleError(T response, String defaultMessage, Consumer<Exception> onError) {
        handleError(response, defaultMessage, onError, null);
    }

    /**
     * Helper method to handle API response errors with optional warning log.
     */
    private <T extends ApiResponse> void handleError(T response, String defaultMessage, Consumer<Exception> onError, String warningMessage) {
        String errorMsg = response != null ? response.getMessage() : defaultMessage;
        if (warningMessage != null) {
            log.warn("{}: {}", warningMessage, errorMsg);
        }
        onError.accept(new Exception(errorMsg));
    }
}

package com.revalclan.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for fetching data from the Reval Plugin API.
 * All plugin endpoints require the RuneLite-RevalClan-Plugin User-Agent header.
 */
@Slf4j
@Singleton
public class RevalApiService {
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "RuneLite-RevalClan-Plugin";

    private final Gson gson;

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
    public RevalApiService() {
        this.gson = new GsonBuilder().create();
    }

    // ==================== POINTS API ====================

    /**
     * Fetches points configuration from the API.
     * GET /plugin/points
     */
    public void fetchPoints(Consumer<PointsResponse> onSuccess, Consumer<Exception> onError) {
        if (cachedPoints != null && System.currentTimeMillis() - lastPointsFetch < CACHE_DURATION_MS) {
            CompletableFuture.runAsync(() -> onSuccess.accept(cachedPoints));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                PointsResponse response = request(ApiEndpoints.POINTS, "GET", null, null, PointsResponse.class);
                
                if (response != null && response.isSuccess()) {
                    cachedPoints = response;
                    lastPointsFetch = System.currentTimeMillis();
                    onSuccess.accept(response);
                } else {
                    onError.accept(new Exception(response != null ? response.getMessage() : "Unknown error"));
                }
            } catch (Exception e) {
                log.error("Failed to fetch points", e);
                onError.accept(e);
            }
        });
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
            CompletableFuture.runAsync(() -> onSuccess.accept(cachedAccount));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = ApiEndpoints.ACCOUNT + "?accountHash=" + accountHash;
                AccountResponse response = request(endpoint, "GET", null, null, AccountResponse.class);
                
                if (response != null && response.isSuccess()) {
                    cachedAccount = response;
                    cachedAccountIdentifier = identifier;
                    lastAccountFetch = System.currentTimeMillis();
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Account not found", onError);
                }
            } catch (Exception e) {
                log.error("Failed to fetch account", e);
                onError.accept(e);
            }
        });
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
            CompletableFuture.runAsync(() -> onSuccess.accept(cachedEvents));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                EventsResponse response = request(ApiEndpoints.EVENTS, "GET", null, null, EventsResponse.class);
                if (response != null && response.isSuccess()) {
                    cachedEvents = response;
                    lastEventsFetch = System.currentTimeMillis();
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to fetch events", onError);
                }
            } catch (Exception e) {
                log.error("Failed to fetch events", e);
                onError.accept(e);
            }
        });
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
        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = ApiEndpoints.eventRegister(eventId);
                String body = "{\"accountHash\":\"" + accountHash + "\"}";
                RegistrationResponse response = request(endpoint, "POST", body, null, RegistrationResponse.class);

                if (response != null && response.isSuccess()) {
                    cachedEvents = null; // Invalidate cache
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Registration failed", onError, "Failed to register for event " + eventId);
                }
            } catch (Exception e) {
                log.error("Failed to register for event: {}", eventId, e);
                onError.accept(e);
            }
        });
    }

    /**
     * Cancel/withdraw event registration.
     * DELETE /plugin/events/:eventId/register
     */
    public void cancelEventRegistration(String eventId, long accountHash,
                                        Consumer<RegistrationResponse> onSuccess,
                                        Consumer<Exception> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = ApiEndpoints.eventRegister(eventId);
                String body = "{\"accountHash\":\"" + accountHash + "\"}";
                RegistrationResponse response = request(endpoint, "DELETE", body, null, RegistrationResponse.class);

                if (response != null && response.isSuccess()) {
                    cachedEvents = null; // Invalidate cache
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Cancellation failed", onError, "Failed to cancel registration for event " + eventId);
                }
            } catch (Exception e) {
                log.error("Failed to cancel registration for event: {}", eventId, e);
                onError.accept(e);
            }
        });
    }

    /**
     * Check registration status for an event.
     * GET /plugin/events/:eventId/registration-status?accountHash={accountHash}
     */
    public void checkRegistrationStatus(String eventId, long accountHash,
                                        Consumer<RegistrationStatusResponse> onSuccess,
                                        Consumer<Exception> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = ApiEndpoints.eventRegistrationStatus(eventId) + "?accountHash=" + accountHash;
                RegistrationStatusResponse response = request(endpoint, "GET", null, null, RegistrationStatusResponse.class);

                if (response != null && response.isSuccess()) {
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Status check failed", onError, "Failed to check registration status for event " + eventId);
                }
            } catch (Exception e) {
                log.error("Failed to check registration status for event: {}", eventId, e);
                onError.accept(e);
            }
        });
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
        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = accountHash != null 
                    ? ApiEndpoints.ACHIEVEMENTS + "?accountHash=" + accountHash
                    : ApiEndpoints.ACHIEVEMENTS;
                
                AchievementsResponse response = request(endpoint, "GET", null, null, AchievementsResponse.class);

                if (response != null && response.isSuccess()) {
                    if (accountHash == null) {
                        cachedAchievements = response;
                        lastAchievementsFetch = System.currentTimeMillis();
                    }
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to fetch achievement definitions", onError);
                }
            } catch (Exception e) {
                log.error("Failed to fetch achievement definitions", e);
                onError.accept(e);
            }
        });
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
            CompletableFuture.runAsync(() -> onSuccess.accept(cachedDiaries));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = accountHash != null 
                    ? ApiEndpoints.DIARIES + "?accountHash=" + accountHash
                    : ApiEndpoints.DIARIES;
                
                DiariesResponse response = request(endpoint, "GET", null, null, DiariesResponse.class);

                if (response != null && response.isSuccess()) {
                    if (accountHash == null) {
                        cachedDiaries = response;
                        lastDiariesFetch = System.currentTimeMillis();
                    }
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to fetch diaries", onError);
                }
            } catch (Exception e) {
                log.error("Failed to fetch diaries", e);
                onError.accept(e);
            }
        });
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
            CompletableFuture.runAsync(() -> onSuccess.accept(cachedChallenges));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = accountHash != null 
                    ? ApiEndpoints.CHALLENGES + "?accountHash=" + accountHash
                    : ApiEndpoints.CHALLENGES;
                
                ChallengesResponse response = request(endpoint, "GET", null, null, ChallengesResponse.class);

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
            } catch (Exception e) {
                log.error("Failed to fetch challenges", e);
                onError.accept(e);
            }
        });
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
        CompletableFuture.runAsync(() -> {
            try {
                AdminLoginRequest requestBody = new AdminLoginRequest(
                    accountHash != null ? String.valueOf(accountHash) : null,
                    osrsNickname
                );
                String body = gson.toJson(requestBody);

                AdminAuthResponse response = request(ApiEndpoints.ADMIN_AUTH_LOGIN, "POST", body, memberCode, AdminAuthResponse.class);

                if (response != null && response.isSuccess()) {
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Admin login failed", onError);
                }
            } catch (Exception e) {
                log.error("Failed to perform admin login", e);
                onError.accept(e);
            }
        });
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
        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = ApiEndpoints.ADMIN_RANK_CHANGES_PENDING + "?limit=" + limit;
                PendingRankChangesResponse response = request(endpoint, "GET", null, memberCode, PendingRankChangesResponse.class);

                if (response != null && response.isSuccess()) {
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to fetch pending rank changes", onError);
                }
            } catch (Exception e) {
                log.error("Failed to fetch pending rank changes", e);
                onError.accept(e);
            }
        });
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
        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = ApiEndpoints.rankChangeActualize(rankChangeId);
                ActualizeRankChangeResponse response = request(endpoint, "POST", null, memberCode, ActualizeRankChangeResponse.class);

                if (response != null && response.isSuccess()) {
                    onSuccess.accept(response);
                } else {
                    handleError(response, "Failed to actualize rank change", onError, "Failed to actualize rank change " + rankChangeId);
                }
            } catch (Exception e) {
                log.error("Failed to actualize rank change: {}", rankChangeId, e);
                onError.accept(e);
            }
        });
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
     * Makes an HTTP request to the plugin API.
     *
     * @param endpoint The API endpoint (relative to base URL)
     * @param method HTTP method (GET, POST, DELETE)
     * @param body Request body for POST/DELETE requests (can be null)
     * @param memberCode Admin member code for admin endpoints (can be null)
     * @param responseClass The class to deserialize the response to
     * @return The deserialized response
     */
    private <T extends ApiResponse> T request(String endpoint, String method, String body,
                                               String memberCode, Class<T> responseClass) throws IOException {
        String fullUrl = ApiEndpoints.BASE_URL + endpoint;
        URL url = URI.create(fullUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Content-Type", "application/json");

            if (memberCode != null && !memberCode.isEmpty()) {
                conn.setRequestProperty("X-Member-Code", memberCode);
            }

            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int responseCode = conn.getResponseCode();

            if (responseCode >= 400) {
                try (InputStreamReader reader = new InputStreamReader(
                    conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8)
                ) {
                    String errorResponse = readStream(reader);
                    try {
                        return gson.fromJson(errorResponse, responseClass);
                    } catch (Exception e) {
                        return null;
                    }
                }
            }

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    String jsonResponse = readStream(reader);
                    try {
                        return gson.fromJson(jsonResponse, responseClass);
                    } catch (Exception e) {
                        throw new IOException("Failed to parse JSON response", e);
                    }
                }
            }
            return null;
        } finally {
            conn.disconnect();
        }
    }

    private String readStream(InputStreamReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[1024];
        int charsRead;
        while ((charsRead = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, charsRead);
        }
        return sb.toString();
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

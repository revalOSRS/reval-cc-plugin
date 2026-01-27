package com.revalclan.api.common;

/**
 * API endpoint constants for the Reval Plugin API
 */
public final class ApiEndpoints {
    public static final String BASE_URL = "https://api.revalosrs.ee/plugin";

    // Points
    public static final String POINTS = "/points";

    // Account
    public static final String ACCOUNT = "/account";

    // Events
    public static final String EVENTS = "/events";
    public static final String EVENT_REGISTER = "/events/%s/register";
    public static final String EVENT_REGISTRATION_STATUS = "/events/%s/registration-status";

    // Achievements
    public static final String ACHIEVEMENTS = "/achievements";

    // Diaries
    public static final String DIARIES = "/diaries";

    // Challenges
    public static final String CHALLENGES = "/challenges";

    // Admin - Authentication
    public static final String ADMIN_AUTH_LOGIN = "/admin/auth/login";

    // Admin - Rank Changes
    public static final String ADMIN_RANK_CHANGES_PENDING = "/admin/rank-changes/pending";
    public static final String ADMIN_RANK_CHANGE_ACTUALIZE = "/admin/rank-changes/%d/actualize";

    /**
     * Build full URL for an endpoint
     */
    public static String url(String endpoint) {
        return BASE_URL + endpoint;
    }

    /**
     * Build event register endpoint for specific event
     */
    public static String eventRegister(String eventId) {
        return String.format(EVENT_REGISTER, eventId);
    }

    /**
     * Build event registration status endpoint for specific event
     */
    public static String eventRegistrationStatus(String eventId) {
        return String.format(EVENT_REGISTRATION_STATUS, eventId);
    }

    /**
     * Build rank change actualize endpoint for specific rank change ID
     */
    public static String rankChangeActualize(int rankChangeId) {
        return String.format(ADMIN_RANK_CHANGE_ACTUALIZE, rankChangeId);
    }
}

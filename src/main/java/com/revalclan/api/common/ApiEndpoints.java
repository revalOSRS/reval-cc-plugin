package com.revalclan.api.common;

/**
 * API endpoints for the Reval Plugin API: constants for fixed paths, builders
 * (holding their own path template) for paths with an id in them
 */
public final class ApiEndpoints {
    public static final String BASE_URL = "https://api.revalosrs.ee/plugin";

    // ── Public API (no auth, no /plugin prefix) — build with publicUrl() ──
    public static final String PUBLIC_BASE_URL = "https://api.revalosrs.ee";
    public static final String PUBLIC_FEATURED_RACE = "/races/featured";

    // Points (includes ranks)
    public static final String POINTS = "/points";

    // Account
    public static final String ACCOUNT = "/account";

    // Leaderboard
    public static final String LEADERBOARD = "/leaderboard";

    // Events
    public static final String EVENTS = "/events";
    public static final String EVENTS_ACTIVE_TEAMS = "/events/active-teams";

    // Achievements
    public static final String ACHIEVEMENTS = "/achievements";

    // Diaries
    public static final String DIARIES = "/diaries";

    // Challenges
    public static final String CHALLENGES = "/challenges";

    // Leagues
    public static final String LEAGUES_CONFIG = "/leagues/config";

    // Competitions
    public static final String COMPETITIONS_SCHEDULED = "/competitions/scheduled";
    public static final String COMPETITIONS_ACTIVE = "/competitions/active";

    // Competition Votes
    public static final String COMPETITION_VOTES = "/competitions/votes";

    // Admin - Authentication
    public static final String ADMIN_AUTH_LOGIN = "/admin/auth/login";

    // Announcements & Notifications
    public static final String ANNOUNCEMENTS = "/announcements";
    public static final String NOTIFICATIONS = "/notifications";
    public static final String NOTIFICATIONS_ACK = "/notifications/ack";

    // Admin - Rank Changes
    public static final String ADMIN_RANK_CHANGES_PENDING = "/admin/rank-changes/pending";

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
        return String.format("/events/%s/register", eventId);
    }

    /**
     * Build full URL for a public (no-auth) endpoint
     */
    public static String publicUrl(String endpoint) {
        return PUBLIC_BASE_URL + endpoint;
    }

    /**
     * Full URL of the public Leagues Bingo payload (boards, tiles, teams, progress) for an event
     */
    public static String leaguesBingoEventUrl(String eventId) {
        return publicUrl(String.format("/leagues-bingo/events/%s", eventId));
    }

    public static String leaguesBingoMe(String eventId, long accountHash) {
        return String.format("/events/%s/leagues-bingo/me?accountHash=%s", eventId, accountHash);
    }

    public static String leaguesBingoPick(String eventId) {
        return String.format("/events/%s/leagues-bingo/pick", eventId);
    }

    /**
     * Build rank change actualize endpoint for specific rank change ID
     */
    public static String rankChangeActualize(int rankChangeId) {
        return String.format("/admin/rank-changes/%d/actualize", rankChangeId);
    }

    /**
     * Build account by ID endpoint for specific OSRS account ID
     */
    public static String accountById(int osrsAccountId) {
        return String.format("/account/%d", osrsAccountId);
    }

    /**
     * Build competition by ID endpoint
     */
    public static String competitionById(String competitionId) {
        return String.format("/competitions/%s", competitionId);
    }

    /**
     * Build cast vote endpoint
     */
    public static String voteCast(String voteId) {
        return String.format("/competitions/votes/%s/cast", voteId);
    }

    /**
     * Build my vote endpoint
     */
    public static String voteMyVote(String voteId) {
        return String.format("/competitions/votes/%s/my-vote", voteId);
    }
}

package com.revalclan.api.common;

/**
 * API endpoint constants for the Reval Plugin API
 */
public final class ApiEndpoints {
    public static final String BASE_URL = "https://api.revalosrs.ee/plugin";

    // Points (includes ranks)
    public static final String POINTS = "/points";

    // Account
    public static final String ACCOUNT = "/account";
    public static final String ACCOUNT_BY_ID = "/account/%d";

    // Leaderboard
    public static final String LEADERBOARD = "/leaderboard";

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

    // Competitions
    public static final String COMPETITIONS = "/competitions";
    public static final String COMPETITIONS_SCHEDULED = "/competitions/scheduled";
    public static final String COMPETITIONS_ACTIVE = "/competitions/active";
    public static final String COMPETITIONS_COMPLETED = "/competitions/completed";
    public static final String COMPETITION_BY_ID = "/competitions/%s";
    public static final String COMPETITION_LEADERBOARD = "/competitions/%s/leaderboard";
    public static final String COMPETITION_ACTIVITY = "/competitions/%s/activity";
    public static final String COMPETITION_SIDE_QUESTS = "/competitions/%s/side-quests";
    public static final String SIDE_QUEST_BY_ID = "/competitions/side-quests/%s";
    public static final String COMPETITION_MY_PROGRESS = "/competitions/%s/my-progress";
    public static final String COMPETITIONS_MY_PROGRESS_ALL = "/competitions/my-progress/all";
    
    // Competition Votes
    public static final String COMPETITION_VOTES = "/competitions/votes";
    public static final String COMPETITION_VOTE_BY_ID = "/competitions/votes/%s";
    public static final String COMPETITION_VOTE_CAST = "/competitions/votes/%s/cast";
    public static final String COMPETITION_VOTE_MY_VOTE = "/competitions/votes/%s/my-vote";

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

    /**
     * Build account by ID endpoint for specific OSRS account ID
     */
    public static String accountById(int osrsAccountId) {
        return String.format(ACCOUNT_BY_ID, osrsAccountId);
    }

    /**
     * Build competition by ID endpoint
     */
    public static String competitionById(String competitionId) {
        return String.format(COMPETITION_BY_ID, competitionId);
    }

    /**
     * Build competition leaderboard endpoint
     */
    public static String competitionLeaderboard(String competitionId) {
        return String.format(COMPETITION_LEADERBOARD, competitionId);
    }

    /**
     * Build competition activity endpoint
     */
    public static String competitionActivity(String competitionId) {
        return String.format(COMPETITION_ACTIVITY, competitionId);
    }

    /**
     * Build competition side quests endpoint
     */
    public static String competitionSideQuests(String competitionId) {
        return String.format(COMPETITION_SIDE_QUESTS, competitionId);
    }

    /**
     * Build side quest by ID endpoint
     */
    public static String sideQuestById(String sideQuestId) {
        return String.format(SIDE_QUEST_BY_ID, sideQuestId);
    }

    /**
     * Build competition my progress endpoint
     */
    public static String competitionMyProgress(String competitionId) {
        return String.format(COMPETITION_MY_PROGRESS, competitionId);
    }

    /**
     * Build vote by ID endpoint
     */
    public static String voteById(String voteId) {
        return String.format(COMPETITION_VOTE_BY_ID, voteId);
    }

    /**
     * Build cast vote endpoint
     */
    public static String voteCast(String voteId) {
        return String.format(COMPETITION_VOTE_CAST, voteId);
    }

    /**
     * Build my vote endpoint
     */
    public static String voteMyVote(String voteId) {
        return String.format(COMPETITION_VOTE_MY_VOTE, voteId);
    }
}

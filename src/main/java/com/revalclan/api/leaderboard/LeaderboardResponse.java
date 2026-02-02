package com.revalclan.api.leaderboard;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response wrapper for GET /plugin/leaderboard endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaderboardResponse extends ApiResponse {
    private LeaderboardData data;

    @Data
    public static class LeaderboardData {
        private List<LeaderboardEntry> leaderboard;
    }

    /**
     * A single entry in the leaderboard
     */
    @Data
    public static class LeaderboardEntry {
        private int rank;
        private int osrsAccountId;
        private String osrsNickname;
        private int activityPoints;
        private String clanRank;
        private int prestigeLevel;
    }
}

package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/**
 * Response wrapper for GET /plugin/competitions/:id/leaderboard endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompetitionLeaderboardResponse extends ApiResponse {
    private LeaderboardData data;

    @Data
    public static class LeaderboardData {
        private String competitionId;
        private String competitionName;
        private String status;
        private Integer participantCount;
        private List<CompetitionsResponse.LeaderboardEntry> leaderboard;
    }
}

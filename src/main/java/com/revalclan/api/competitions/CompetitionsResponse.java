package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/**
 * Response wrapper for GET /plugin/competitions endpoints
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompetitionsResponse extends ApiResponse {
    private CompetitionsData data;

    @Data
    public static class CompetitionsData {
        private List<Competition> competitions;
    }

    @Data
    public static class Competition {
        private String id;
        private String name;
        private String description;
        private String competitionType;
        private String startDate;
        private String endDate;
        private String status;
        private Map<String, Integer> rewardConfig;
        private Integer participantCount;
        private List<LeaderboardEntry> leaderboard;
        private List<SideQuest> sideQuests;
    }

    @Data
    public static class LeaderboardEntry {
        private Integer rank;
        private String osrsNickname;
        private Long currentValue;
        private Long finalValue;
    }

    @Data
    public static class SideQuest {
        private String name;
        private String description;
        private String sideQuestType;
        private Map<String, Integer> rewardConfig;
        private List<LeaderboardEntry> leaderboard;
    }
}

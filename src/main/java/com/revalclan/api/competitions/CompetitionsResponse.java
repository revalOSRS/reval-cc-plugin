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
        private Map<String, Object> trackingConfig;
        private String startDate;
        private String endDate;
        private String status;
        private Map<String, Integer> rewardConfig;
        private String createdAt;
        private Integer participantCount;
        private List<LeaderboardEntry> leaderboard;
        private List<SideQuest> sideQuests;
        private List<Reward> rewards;
    }

    @Data
    public static class LeaderboardEntry {
        private Integer rank;
        private Integer osrsAccountId;
        private String osrsNickname;
        private Integer currentValue;
        private Integer finalValue;
        private String enrolledAt;
        private String lastUpdatedAt;
    }

    @Data
    public static class SideQuest {
        private String id;
        private String competitionId;
        private String name;
        private String description;
        private String sideQuestType;
        private Map<String, Object> trackingConfig;
        private Map<String, Integer> rewardConfig;
        private String status;
        private List<LeaderboardEntry> leaderboard;
    }

    @Data
    public static class Reward {
        private Integer osrsAccountId;
        private String osrsNickname;
        private String rewardType;
        private Integer finalRank;
        private Integer pointsAwarded;
    }
}

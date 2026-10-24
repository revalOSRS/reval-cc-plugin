package com.revalclan.api.account;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/**
 * Response wrapper for GET /plugin/account endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountResponse extends ApiResponse {
    private AccountData data;

    @Data
    public static class AccountData {
        private OsrsAccount osrsAccount;
        private PointsBreakdown pointsBreakdown;
        private Integer combatAchievementPoints;
        private Integer collectionLogUniqueObtained;
        private List<Milestone> milestones;
        private List<PointsLogEntry> pointsLog;
    }

    /**
     * OSRS account information from the plugin API
     */
    @Data
    public static class OsrsAccount {
        private String osrsNickname;
        private Double ehp;
        private Double ehb;
        private Integer activityPoints;
        private String clanRank;
    }

    /**
     * Points breakdown by category for an account
     */
    @Data
    public static class PointsBreakdown {
        private int drops;
        private int pets;
        private int milestones;
        private int events;
        private int revalDiaries;
        private int revalChallenges;
        private int total;
    }

    /**
     * Account milestone achievement
     */
    @Data
    public static class Milestone {
        private String type;
        private String description;
        private Integer pointsAwarded;
        private String achievedAt;
    }

    /**
     * Points log entry representing a single points transaction
     */
    @Data
    public static class PointsLogEntry {
        private Integer pointsChange;
        private String sourceType; // 'drop' | 'pet' | 'milestone' | 'manual' | 'event' | 'decay' | 'misc'
        private Integer itemId;
        private String sourceDescription;
        private String createdAt;
    }
}

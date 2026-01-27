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
        private Integer questPoints;
        private Integer diariesTotalCompleted;
        private Integer combatAchievementsTotalCompleted;
        private Integer collectionLogUniqueObtained;
        private Integer totalKills;
        private List<Milestone> milestones;
        private List<PointsLogEntry> pointsLog;
    }

    /**
     * OSRS account information from the plugin API
     */
    @Data
    public static class OsrsAccount {
        private Integer id;
        private String osrsNickname;
        private Integer womPlayerId;
        private String womRank;
        private Double ehp;
        private Double ehb;
        private Integer activityPoints;
        private Integer maintenancePoints;
        private String clanRank;
        private Integer prestigeLevel;
        private String lastSyncedAt;
        private String rankUpdatedAt;
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
        private Integer id;
        private Integer osrsAccountId;
        private String pointType; // 'activity' | 'maintenance'
        private Integer pointsChange;
        private Integer pointsAfter;
        private String sourceType; // 'drop' | 'pet' | 'milestone' | 'manual' | 'event' | 'prestige' | 'decay' | 'initial'
        private Integer sourceId;
        private String sourceDescription;
        private String createdAt;
    }
}

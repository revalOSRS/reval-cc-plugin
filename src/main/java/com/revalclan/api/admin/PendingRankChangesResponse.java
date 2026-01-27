package com.revalclan.api.admin;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response wrapper for GET /plugin/admin/rank-changes/pending endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PendingRankChangesResponse extends ApiResponse {
    private PendingRankChangesData data;

    @Data
    public static class PendingRankChangesData {
        private List<RankChange> pendingRankChanges;
        private int total;
    }

    @Data
    public static class RankChange {
        private int id;
        private int osrsAccountId;
        private String osrsNickname;
        private String previousRank;
        private String newRank;
        private String changeReason;
        private String changeDetails;
        private int activityPointsAtChange;
        private int maintenancePointsAtChange;
        private String changedAt;
        private boolean actualized;
        private String actualizedAt;
        private Integer actualizedBy;
    }
}

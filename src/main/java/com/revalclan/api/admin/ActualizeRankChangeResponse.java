package com.revalclan.api.admin;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response wrapper for POST /plugin/admin/rank-changes/:id/actualize endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActualizeRankChangeResponse extends ApiResponse {
    private ActualizeData data;

    @Data
    public static class ActualizeData {
        private int id;
        private int osrsAccountId;
        private String previousRank;
        private String newRank;
        private String changeReason;
        private boolean actualized;
        private String actualizedAt;
        private Integer actualizedBy;
    }
}

package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response wrapper for POST /plugin/competitions/votes/:id/cast endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CastVoteResponse extends ApiResponse {
    private CastVoteData data;

    @Data
    public static class CastVoteData {
        private String message;
        private String previousOptionId;
        private String newOptionId;
    }
}

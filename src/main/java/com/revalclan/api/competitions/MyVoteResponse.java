package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response wrapper for GET /plugin/competitions/votes/:id/my-vote endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyVoteResponse extends ApiResponse {
    private MyVoteData data;

    @Data
    public static class MyVoteData {
        private Boolean hasVoted;
        private String optionId;
        private String votedAt;
    }
}

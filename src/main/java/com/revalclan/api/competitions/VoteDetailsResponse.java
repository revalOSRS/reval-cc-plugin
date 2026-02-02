package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response wrapper for GET /plugin/competitions/votes/:id endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VoteDetailsResponse extends ApiResponse {
    private VotesResponse.Vote data;
}

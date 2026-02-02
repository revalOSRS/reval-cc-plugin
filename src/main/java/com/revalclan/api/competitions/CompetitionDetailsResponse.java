package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response wrapper for GET /plugin/competitions/:id endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompetitionDetailsResponse extends ApiResponse {
    private CompetitionsResponse.Competition data;
}

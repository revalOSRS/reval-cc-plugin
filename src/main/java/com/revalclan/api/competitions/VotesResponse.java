package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/**
 * Response wrapper for GET /plugin/competitions/votes endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VotesResponse extends ApiResponse {
    private VotesData data;

    @Data
    public static class VotesData {
        private List<Vote> votes;
    }

    @Data
    public static class Vote {
        private String id;
        private String title;
        private String description;
        private String voteEndDate;
        private Integer totalVotes;
        private List<VoteOption> options;
    }

    @Data
    public static class VoteOption {
        private String id;
        private String templateName;
        private Integer voteCount;
    }
}

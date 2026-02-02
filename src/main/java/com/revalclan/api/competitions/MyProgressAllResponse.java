package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/**
 * Response wrapper for GET /plugin/competitions/my-progress/all endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyProgressAllResponse extends ApiResponse {
    private AllProgressData data;

    @Data
    public static class AllProgressData {
        private Integer osrsAccountId;
        private List<CompetitionProgress> activeCompetitions;
    }

    @Data
    public static class CompetitionProgress {
        private String competitionId;
        private String competitionName;
        private String competitionType;
        private String endDate;
        private Boolean enrolled;
        private Integer currentValue;
        private Integer rank;
        private Integer totalParticipants;
    }
}

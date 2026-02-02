package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/**
 * Response wrapper for GET /plugin/competitions/:id/my-progress endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyProgressResponse extends ApiResponse {
    private ProgressData data;

    @Data
    public static class ProgressData {
        private String competitionId;
        private String competitionName;
        private String status;
        private Boolean enrolled;
        private Integer currentValue;
        private Integer rank;
        private Integer totalParticipants;
        private String enrolledAt;
        private String lastUpdatedAt;
        private List<SideQuestProgress> sideQuestProgress;
        private List<CompetitionsResponse.Reward> rewards;
    }

    @Data
    public static class SideQuestProgress {
        private String sideQuestId;
        private String sideQuestName;
        private Integer currentValue;
        private Integer rank;
    }
}

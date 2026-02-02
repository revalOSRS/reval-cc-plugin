package com.revalclan.api.competitions;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/**
 * Response wrapper for GET /plugin/competitions/:id/activity endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompetitionActivityResponse extends ApiResponse {
    private ActivityData data;

    @Data
    public static class ActivityData {
        private String competitionId;
        private String competitionName;
        private List<ActivityEntry> activity;
    }

    @Data
    public static class ActivityEntry {
        private String osrsNickname;
        private String eventType;
        private Integer valueChange;
        private Integer newValue;
        private String timestamp;
        private Map<String, Object> metadata;
    }
}

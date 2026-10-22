package com.revalclan.api.diaries;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response wrapper for GET /plugin/diaries endpoint
 * Returns flat list of active diaries
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DiariesResponse extends ApiResponse {
    private DiariesData data;

    @Data
    public static class DiariesData {
        private List<Diary> diaries;
    }

    /**
     * Represents a Reval custom diary structure
     */
    @Data
    public static class Diary {
        private String id;
        private String name;
        private String category;
        private int fullCompletionBonus;

        private List<DiaryTier> tiers;

        @Data
        public static class DiaryTier {
            private String tier;  // 'easy', 'medium', 'hard', 'elite', 'master', 'grandmaster'
            private int completionBonus;

            // Progress fields (if accountHash provided)
            private Integer tasksCompleted;
            private Integer tasksTotal;
            private Boolean isComplete;

            // Tasks in this tier
            private List<DiaryTask> tasks;
        }

        @Data
        public static class DiaryTask {
            private String name;
            private String description;
            private String hint;
            private boolean isHidden;
            private boolean isActive;

            // Progress (if accountHash provided)
            private TaskProgress progress;

            @Data
            public static class TaskProgress {
                private boolean isCompleted;
                private Object progressData;
                private int progressPercent;
            }
        }
    }
}

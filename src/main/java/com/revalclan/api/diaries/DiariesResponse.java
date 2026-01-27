package com.revalclan.api.diaries;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

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
        
        private DiariesSummary summary;
    }

    @Data
    public static class DiariesSummary {
        private int totalDiaries;
        private int totalTasks;
    }

    /**
     * Represents a Reval custom diary structure
     */
    @Data
    public static class Diary {
        private String id;
        private String name;
        private String description;
        private String category;
        private String icon;
        private String imageUrl;
        private boolean isActive;
        private int sortOrder;
        private int fullCompletionBonus;
        
        private List<DiaryTier> tiers;
        
        private DiarySummary summary;
        
        @Data
        public static class DiarySummary {
            private int totalTasks;
            private Map<String, Integer> taskCounts;  // Record<DiaryTier, number>
            
            // Progress (if accountHash provided)
            private DiaryProgress progress;
        }
        
        @Data
        public static class DiaryProgress {
            private int tasksCompleted;
            private int tasksTotal;
            private int pointsEarned;
            private int pointsAvailable;
            private int completionPercent;
        }

        @Data
        public static class DiaryTier {
            private String tier;  // 'easy', 'medium', 'hard', 'elite', 'master', 'grandmaster'
            private int completionBonus;
            private int taskCount;
            
            // Progress fields (if accountHash provided)
            private Integer tasksCompleted;
            private Integer tasksTotal;
            private Integer pointsEarned;
            private Integer pointsAvailable;
            private Boolean isComplete;
            private Integer completionPercent;
            
            // Tasks in this tier
            private List<DiaryTask> tasks;
        }

        @Data
        public static class DiaryTask {
            private String id;
            private String name;
            private String description;
            private String hint;
            private String icon;
            private int points;
            private Object requirement;
            private boolean isRepeatable;
            private Integer maxRepetitions;
            private Integer pointsPerRepetition;
            private boolean isHidden;
            private boolean isActive;
            private int sortOrder;
            
            // Progress (if accountHash provided)
            private TaskProgress progress;
            
            @Data
            public static class TaskProgress {
                private boolean isCompleted;
                private String completedAt;
                private Object progressData;
                private int progressPercent;
                private Integer repetitions;
            }
        }
    }
}

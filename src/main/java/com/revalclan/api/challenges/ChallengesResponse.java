package com.revalclan.api.challenges;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response wrapper for GET /plugin/challenges endpoint
 * Returns active weekly and monthly challenges
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChallengesResponse extends ApiResponse {
    private ChallengesData data;

    @Data
    public static class ChallengesData {
        private List<Challenge> challenges;
        private ChallengesSummary summary;
    }

    @Data
    public static class ChallengesSummary {
        private int weekly;
        private int monthly;
        private int total;
    }

    @Data
    public static class Challenge {
        private String id;  // UUID string
        private String definitionId;
        private String name;
        private String description;
        private String period;  // 'daily', 'weekly', or 'monthly'
        private String difficulty;
        private Integer points;
        private Object requirement;  // JSONB requirement data
        
        // Period bounds
        private String periodStart;
        private String periodEnd;
        
        private Boolean isActive;
        private String category;  // e.g., "bossing", "skilling"
        private String icon;  // Optional icon identifier
        
        // Progress fields (when accountHash provided in request)
        private Boolean isCompleted;
        private String completedAt;  // ISO timestamp
        private ChallengeProgress progress;
        private Integer progressPercent;  // 0-100
        private Integer pointsAwarded;  // Points awarded for completion (0 if not completed)
        
        public boolean isCompleted() {
            return isCompleted != null && isCompleted;
        }
        
        @Data
        public static class ChallengeProgress {
            private Integer current;
            private Integer target;
        }
    }
}

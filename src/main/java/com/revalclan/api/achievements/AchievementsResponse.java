package com.revalclan.api.achievements;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response wrapper for GET /plugin/achievements endpoint
 * Returns all achievement definitions, optionally with progress when accountHash is provided
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AchievementsResponse extends ApiResponse {
    private AchievementsData data;

    @Data
    public static class AchievementsData {
        private List<Achievement> achievements;
        private Integer count;
    }

    @Data
    public static class Achievement {
        private String id;
        private String name;
        private String description;
        private String category;
        private String difficulty;
        private String rarity;
        private String icon;
        private String imageUrl;
        private Object requirements;  // RequirementDef JSONB
        private List<String> prerequisites;
        private boolean isHidden;
        private boolean isActive;
        private String expiresAt;
        private String availableFrom;
        private Integer sortOrder;
        private String createdAt;
        private String updatedAt;
        
        // Progress data (only present when accountHash is provided in request)
        private AchievementProgress progress;
        
        @Data
        public static class AchievementProgress {
            private boolean isCompleted;
            private String completedAt;
            private Object progressData;
            private String startedAt;
            private String lastUpdatedAt;
        }
    }
}

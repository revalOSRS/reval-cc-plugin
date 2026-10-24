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
    }

    @Data
    public static class Achievement {
        private String name;
        private String description;
        private String rarity;

        // Progress data (only present when accountHash is provided in request)
        private AchievementProgress progress;

        @Data
        public static class AchievementProgress {
            private boolean isCompleted;
        }
    }
}

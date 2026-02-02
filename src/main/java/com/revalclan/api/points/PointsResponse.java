package com.revalclan.api.points;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Response wrapper for GET /plugin/points endpoint
 * Returns both point sources and ranks information
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PointsResponse extends ApiResponse {
    private PointsData data;

    @Data
    public static class PointsData {
        private Map<String, List<PointSource>> pointSources;
        private List<Rank> ranks;
    }

    @Data
    public static class Rank {
        private String name;
        private String displayName;
        private List<String> ingameRank;
        private int pointsRequired;
        private int maintenancePerMonth;
        private int prestigeRequired;
        private List<AdditionalRequirement> additionalRequirements;

        @Data
        public static class AdditionalRequirement {
            private String description;
            private List<String> anyOf;
        }
    }

    @Data
    public static class PointSource {
        private String id;
        private String name;
        private String pointsDescription; // Human-readable points description (e.g., "30 points", "1 point per 1M GP value")
        private String description;
        private boolean repeatable;
        private Integer points; // Numeric points value (null/undefined for dynamic sources, actual value for constant sources)
        private Integer threshold; // For collection log and combat achievements (moved from metadata)
        private String icon; // Icon identifier string for point sources (for display purposes, not included for dynamic sources)
        private PointSourceMetadata metadata; // Optional metadata

        public String getPointsDisplay() {
            return pointsDescription;
        }

        public int getPointsValue() {
            return points != null ? points : 0;
        }

        @Data
        public static class PointSourceMetadata {
            private String category;
            private String source;
        }
    }

}

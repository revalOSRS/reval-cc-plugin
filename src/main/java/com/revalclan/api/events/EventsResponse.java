package com.revalclan.api.events;

import com.revalclan.api.common.ApiResponse;
import com.revalclan.util.DateTimeUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Response wrapper for GET /plugin/events
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EventsResponse extends ApiResponse {
    private EventsData data;

    @Data
    public static class EventsData {
        private int count;
        private List<EventSummary> events;
    }

    @Getter
    @Setter
    public static class EventSummary {
        private String id;
        private String name;
        private String description;
        private String eventType;  // 'bingo', 'battleship_bingo', etc.
        private String status;     // 'active', 'completed'
        private String startDate;  // ISO 8601
        private String endDate;    // ISO 8601
        private List<EventRegistration> registrations;
        
        public boolean isCompleted() {
            return "completed".equals(status);
        }
        
        public boolean isCurrentlyActive() {
            return "active".equals(status) || "planning".equals(status);
        }

        public boolean isUpcoming() {
            return "scheduled".equals(status);
        }
        
        public String getFormattedStartDate() {
            try {
                return DateTimeUtil.parseToLocal(startDate)
                    .format(DateTimeFormatter.ofPattern("MMM d HH:mm"));
            } catch (Exception e) {
                return startDate;
            }
        }

        public String getFormattedEndDate() {
            try {
                return DateTimeUtil.parseToLocal(endDate)
                    .format(DateTimeFormatter.ofPattern("MMM d HH:mm"));
            } catch (Exception e) {
                return endDate;
            }
        }

        public String getDuration() {
            try {
                Instant start = DateTimeUtil.parseToInstant(startDate);
                Instant end = DateTimeUtil.parseToInstant(endDate);
                long days = ChronoUnit.DAYS.between(start, end);
                long hours = ChronoUnit.HOURS.between(start, end) % 24;

                if (days > 0) {
                    return days + " day" + (days != 1 ? "s" : "") +
                           (hours > 0 ? " " + hours + "h" : "");
                } else {
                    return hours + " hour" + (hours != 1 ? "s" : "");
                }
            } catch (Exception e) {
                return "Unknown";
            }
        }

        public String getTimeUntilStart() {
            try {
                Instant start = DateTimeUtil.parseToInstant(startDate);
                Instant now = Instant.now();

                if (now.isAfter(start)) return "Started";

                long days = ChronoUnit.DAYS.between(now, start);
                long hours = ChronoUnit.HOURS.between(now, start) % 24;

                if (days > 0) {
                    return "Starts in " + days + "d " + hours + "h";
                } else if (hours > 0) {
                    return "Starts in " + hours + "h";
                } else {
                    long minutes = ChronoUnit.MINUTES.between(now, start);
                    return "Starts in " + minutes + "m";
                }
            } catch (Exception e) {
                return "";
            }
        }

        public int getActiveRegistrationCount() {
            if (registrations == null) return 0;
            return (int) registrations.stream()
                .filter(EventRegistration::isActive)
                .count();
        }
        
        public String getEventTypeDisplay() {
            if (eventType == null) return "Event";
            switch (eventType.toLowerCase()) {
                case "bingo": return "Bingo";
                case "battleship_bingo": return "Battleship Bingo";
                case "skilling": return "Skilling Competition";
                case "boss": return "Boss Competition";
                case "pvp": return "PvP Event";
                default: return eventType.substring(0, 1).toUpperCase() + eventType.substring(1);
            }
        }
    }

    @Getter
    @Setter
    public static class EventRegistration {
        private String osrsNickname;
        private String status;
        
        public boolean isPending() {
            return "pending".equals(status);
        }
        
        public boolean isRegistered() {
            return "registered".equals(status);
        }
        
        public boolean isScheduled() {
            return "scheduled".equals(status);
        }
        
        public boolean isActive() {
            return isPending() || isRegistered() || isScheduled();
        }
    }
}


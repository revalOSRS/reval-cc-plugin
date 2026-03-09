package com.revalclan.api.announcements;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Response wrapper for GET /plugin/announcements
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementsResponse extends ApiResponse {
	private AnnouncementsData data;

	@Data
	public static class AnnouncementsData {
		private int count;
		private List<Announcement> announcements;
	}

	@Getter
	@Setter
	public static class Announcement {
		private int id;
		private String title;
		private String message;
		private String type; // "broadcast" or "chat"
		private Map<String, Object> config;
		private int priority;

		public boolean isBroadcast() {
			return "broadcast".equals(type);
		}

		public boolean isChat() {
			return "chat".equals(type);
		}

		/**
		 * Get the intervalMinutes from config, defaulting to 30 if absent.
		 */
		public int getIntervalMinutes() {
			if (config != null && config.containsKey("intervalMinutes")) {
				Object val = config.get("intervalMinutes");
				if (val instanceof Number) {
					return ((Number) val).intValue();
				}
			}
			return 30;
		}
	}
}

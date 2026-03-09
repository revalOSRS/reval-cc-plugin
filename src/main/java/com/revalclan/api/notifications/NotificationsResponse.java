package com.revalclan.api.notifications;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Response wrapper for GET /plugin/notifications
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationsResponse extends ApiResponse {
	private NotificationsData data;

	@Data
	public static class NotificationsData {
		private int count;
		private List<Notification> notifications;
	}

	@Getter
	@Setter
	public static class Notification {
		private int id;
		private String title;
		private String message;
		private Map<String, Object> metadata;
		private String createdAt;
	}
}

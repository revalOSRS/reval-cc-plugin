package com.revalclan.api.notifications;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response wrapper for POST /plugin/notifications/ack
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationAckResponse extends ApiResponse {
	// Uses inherited 'status' and 'message' fields only.
}

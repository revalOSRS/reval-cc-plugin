package com.revalclan.api.events;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response wrapper for event registration operations
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationResponse extends ApiResponse {
    private RegistrationData data;

    @Data
    public static class RegistrationData {
        private String registrationId;
        private String eventName;
        private String osrsNickname;
        private String registrationStatus;
        private String createdAt;

        // For cancellation response
        private String previousStatus;
        private String newStatus;
    }
}

package com.revalclan.api.events;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response wrapper for registration status check
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationStatusResponse extends ApiResponse {
    private RegistrationStatusData data;

    @Data
    public static class RegistrationStatusData {
        private String osrsNickname;
        private boolean isRegistered;
        private String registrationStatus;
        private String registeredAt;
    }
}

package com.revalclan.api.admin;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response wrapper for POST /plugin/admin/auth/login endpoint
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminAuthResponse extends ApiResponse {
    private AdminAuthData data;

    @Data
    public static class AdminAuthData {
        private boolean authenticated;
        private int memberId;
        private String osrsNickname;
        private List<String> permissions;
    }
}

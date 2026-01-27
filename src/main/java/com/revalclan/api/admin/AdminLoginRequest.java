package com.revalclan.api.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Request body for POST /plugin/admin/auth/login endpoint
 */
@Data
@AllArgsConstructor
public class AdminLoginRequest {
    private String accountHash;
    private String osrsNickname;
}

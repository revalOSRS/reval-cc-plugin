package com.revalclan.api.common;

import lombok.Data;

/**
 * Base API response wrapper used by all plugin API endpoints
 */
@Data
public abstract class ApiResponse {
    private String status;
    private String message;

    public boolean isSuccess() {
        return "success".equals(status);
    }

    public boolean isError() {
        return "error".equals(status);
    }
}

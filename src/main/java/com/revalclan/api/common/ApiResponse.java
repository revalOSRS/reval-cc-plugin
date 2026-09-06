package com.revalclan.api.common;

import lombok.Data;

/**
 * Base API response wrapper used by all plugin API endpoints
 */
@Data
public abstract class ApiResponse implements ApiEnvelope {
    private String status;
    private String message;

    @Override
    public boolean isSuccess() {
        return "success".equals(status);
    }

    @Override
    public String getErrorMessage() {
        return message;
    }

    public boolean isError() {
        return "error".equals(status);
    }
}

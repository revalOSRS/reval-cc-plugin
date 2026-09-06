package com.revalclan.api.common;

import lombok.Data;

/**
 * Envelope of the public (no-auth) API the homepage reads:
 * {"success": true, "data": ...} on success, {"success": false, "error": "..."} on failure.
 */
@Data
public abstract class PublicApiResponse implements ApiEnvelope {
	private Boolean success;
	private String error;

	@Override
	public boolean isSuccess() {
		return Boolean.TRUE.equals(success);
	}

	@Override
	public String getErrorMessage() {
		return error;
	}
}

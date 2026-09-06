package com.revalclan.api.common;

/** What the HTTP layer needs from any response envelope, plugin or public. */
public interface ApiEnvelope {
	boolean isSuccess();

	/** Human-readable failure reason, or null when the envelope carries none. */
	String getErrorMessage();
}

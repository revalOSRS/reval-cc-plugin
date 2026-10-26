package com.revalclan.auth;

import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Derives the client used for every request that carries a Link code or a
 * bearer token. It shares the connection pool of the RuneLite client but:
 * <ul>
 *   <li>never follows redirects, so a 3xx can never carry the credential to
 *       another host or path (a redirect surfaces as a plain HTTP error);</li>
 *   <li>refuses, before anything is sent, any request outside {@link ApiOrigin};</li>
 *   <li>has no response cache, so private responses are not written to disk;</li>
 *   <li>gives every call a finite total time, so one hung connection can never
 *       keep a pairing "in flight" (and its code unusable) forever.</li>
 * </ul>
 */
public final class SecureHttp {
	/** Pairing and ordinary API calls. */
	public static final long API_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);
	/** Events, which may carry a screenshot upload. */
	public static final long UPLOAD_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(60);

	private SecureHttp() {
	}

	public static OkHttpClient harden(OkHttpClient base, ApiOrigin origin, long callTimeoutMs) {
		OkHttpClient.Builder builder = base.newBuilder()
			.followRedirects(false)
			.followSslRedirects(false)
			.cache(null)
			.callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS);
		// First in line, ahead of anything RuneLite or a test stub installed.
		builder.interceptors().add(0, chain -> {
			if (!origin.allows(chain.request().url())) {
				throw new IOException("Blocked a credentialed request to an unexpected origin");
			}
			return chain.proceed(chain.request());
		});
		return builder.build();
	}
}

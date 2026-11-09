package com.revalclan.auth;

import okhttp3.HttpUrl;

/**
 * The single origin that may ever receive a Link code or a bearer token.
 * Scheme, host and port must all match exactly; user-info tricks such as
 * {@code https://api.revalosrs.ee@evil.example/} never match.
 */
public final class ApiOrigin {
	public static final ApiOrigin PRODUCTION = new ApiOrigin(HttpUrl.parse("https://api.revalosrs.ee"));

	private final HttpUrl origin;

	private ApiOrigin(HttpUrl origin) {
		this.origin = origin;
	}

	/** Tests only (package-private, so no other package can call it): points the credential checks at a local server. */
	static ApiOrigin unsafeForTests(String origin) {
		HttpUrl parsed = HttpUrl.parse(origin);
		if (parsed == null) throw new IllegalArgumentException("Invalid origin");
		return new ApiOrigin(parsed);
	}

	public boolean allows(HttpUrl url) {
		return url != null
			&& url.scheme().equals(origin.scheme())
			&& url.host().equals(origin.host())
			&& url.port() == origin.port()
			&& url.username().isEmpty()
			&& url.password().isEmpty();
	}

	public boolean allows(String url) {
		return allows(url == null ? null : HttpUrl.parse(url));
	}
}

package com.revalclan.auth;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiOriginTest {
	@Test
	public void onlyTheExactHttpsApiOriginIsAllowed() {
		ApiOrigin origin = ApiOrigin.PRODUCTION;
		assertTrue(origin.allows("https://api.revalosrs.ee/plugin/auth/pair"));
		assertTrue(origin.allows("https://api.revalosrs.ee/reval-webhook"));
		assertTrue(origin.allows("https://API.REVALOSRS.EE/plugin/points"));
		assertTrue(origin.allows("https://api.revalosrs.ee:443/plugin/points"));
	}

	@Test
	public void lookalikeHostsSchemesAndPortsAreRefused() {
		ApiOrigin origin = ApiOrigin.PRODUCTION;
		String[] refused = {
			"http://api.revalosrs.ee/plugin/points",
			"https://www.revalosrs.ee/plugin/points",
			"https://revalosrs.ee/plugin/points",
			"https://evil.api.revalosrs.ee/plugin/points",
			"https://api.revalosrs.ee.evil.example/plugin/points",
			"https://api-revalosrs.ee/plugin/points",
			"https://api.revalosrs.ee:8443/plugin/points",
			"https://api.revalosrs.ee./plugin/points",
			"https://evil.example/https://api.revalosrs.ee/plugin/points",
			"https://api.revalosrs.ee@evil.example/plugin/points",
			"https://api.revalosrs.ee:secret@evil.example/plugin/points",
			"https://user@api.revalosrs.ee/plugin/points",
			"ftp://api.revalosrs.ee/plugin/points",
		};
		for (String url : refused) {
			assertFalse(url, origin.allows(url));
		}
	}

	@Test
	public void unparsableOrMissingUrlsAreRefused() {
		assertFalse(ApiOrigin.PRODUCTION.allows((String) null));
		assertFalse(ApiOrigin.PRODUCTION.allows(""));
		assertFalse(ApiOrigin.PRODUCTION.allows("not a url"));
		assertFalse(ApiOrigin.PRODUCTION.allows((okhttp3.HttpUrl) null));
	}

	@Test
	public void testOriginsAreExactToo() {
		ApiOrigin local = ApiOrigin.unsafeForTests("http://127.0.0.1:4567");
		assertTrue(local.allows("http://127.0.0.1:4567/anything"));
		assertFalse(local.allows("http://127.0.0.1:4568/anything"));
		assertFalse(local.allows("https://127.0.0.1:4567/anything"));
		assertEquals("production keeps https", false, ApiOrigin.PRODUCTION.allows("http://127.0.0.1:4567/"));
	}
}

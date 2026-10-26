package com.revalclan.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Real sockets on loopback: the redirect and origin rules are properties of how
 * OkHttp actually behaves, so they are exercised against real servers rather
 * than a stub that sits above the redirect handling.
 */
public class SecureHttpTest {
	@Rule public TemporaryFolder temp = new TemporaryFolder();

	private HttpServer trusted;
	private HttpServer other;
	private final List<String> trustedHits = new CopyOnWriteArrayList<>();
	private final List<String> otherHits = new CopyOnWriteArrayList<>();
	private final List<String> otherAuthorization = new CopyOnWriteArrayList<>();
	private ApiOrigin origin;

	@Before
	public void startServers() throws IOException {
		trusted = server();
		other = server();
		other.createContext("/", exchange -> {
			otherHits.add(exchange.getRequestURI().toString());
			String auth = exchange.getRequestHeaders().getFirst("Authorization");
			if (auth != null) otherAuthorization.add(auth);
			reply(exchange, 200, "other");
		});
		trusted.createContext("/ok", exchange -> {
			trustedHits.add("/ok");
			reply(exchange, 200, "trusted");
		});
		trusted.createContext("/to-other", exchange -> {
			trustedHits.add("/to-other");
			redirect(exchange, 302, "http://127.0.0.1:" + other.getAddress().getPort() + "/stolen");
		});
		// The JDK server stamps every response with its real Date; the tests below use a clock that is 3 hours behind it.
		other.createContext("/dated", exchange -> reply(exchange, 200, "other"));
		trusted.createContext("/dated", exchange -> reply(exchange, 200, "trusted"));
		trusted.createContext("/dated-via-other", exchange -> {
			redirect(exchange, 302, "http://127.0.0.1:" + other.getAddress().getPort() + "/dated");
		});
		trusted.createContext("/to-self", exchange -> {
			trustedHits.add("/to-self");
			redirect(exchange, 307, "/ok");
		});
		trusted.start();
		other.start();
		origin = ApiOrigin.unsafeForTests("http://127.0.0.1:" + trusted.getAddress().getPort());
	}

	@After
	public void stopServers() {
		trusted.stop(0);
		other.stop(0);
	}

	private static HttpServer server() throws IOException {
		return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
	}

	private static void reply(HttpExchange exchange, int code, String body) throws IOException {
		byte[] bytes = body.getBytes("UTF-8");
		exchange.sendResponseHeaders(code, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private static void redirect(HttpExchange exchange, int code, String location) throws IOException {
		exchange.getResponseHeaders().add("Location", location);
		exchange.sendResponseHeaders(code, -1);
		exchange.close();
	}

	/** This computer's clock, 3 hours behind the (real) time the local servers stamp on their responses. */
	private final java.time.Clock behind = java.time.Clock.offset(java.time.Clock.systemUTC(), java.time.Duration.ofHours(-3));

	private LinkTokenManager manager() throws IOException {
		CredentialStore store = new CredentialStore(new TestKit.FakeConfig().manager, new com.google.gson.Gson(),
			temp.newFolder("credentials").toPath().resolve("link"));
		return new LinkTokenManager(store, new OkHttpClient(), new com.google.gson.Gson(), origin,
			trustedUrl("/plugin/auth/pair"), behind, SecureHttp.API_TIMEOUT_MS);
	}

	private Response get(OkHttpClient client, String url) throws IOException {
		return client.newCall(new Request.Builder().url(url).build()).execute();
	}

	private long observedOffsetAfter(OkHttpClient client, String url) throws IOException {
		LinkTokenManager manager = manager();
		try (Response response = get(client, url)) {
			manager.observeServerTime(response);
		}
		return manager.serverNowMillis() - behind.millis();
	}

	private String trustedUrl(String path) {
		return "http://127.0.0.1:" + trusted.getAddress().getPort() + path;
	}

	private Request.Builder withBearer(String url) {
		return new Request.Builder().url(url).header("Authorization", "Bearer " + TestKit.TOKEN_A);
	}

	@Test
	public void aRedirectToAnotherHostIsNeverFollowedAndNothingReachesIt() throws Exception {
		OkHttpClient client = SecureHttp.harden(new OkHttpClient(), origin, SecureHttp.API_TIMEOUT_MS);

		try (Response response = client.newCall(withBearer(trustedUrl("/to-other")).build()).execute()) {
			assertEquals("the 3xx is handed back as is", 302, response.code());
		}

		assertEquals(1, trustedHits.size());
		assertTrue("the redirect target was never contacted", otherHits.isEmpty());
		assertTrue(otherAuthorization.isEmpty());
	}

	@Test
	public void evenARedirectWithinTheTrustedOriginIsNotFollowed() throws Exception {
		OkHttpClient plain = new OkHttpClient();
		try (Response response = plain.newCall(withBearer(trustedUrl("/to-self")).build()).execute()) {
			assertEquals("control: an ordinary client does follow it, bearer and all", 200, response.code());
		}
		assertEquals(2, trustedHits.size());
		trustedHits.clear();

		OkHttpClient hardened = SecureHttp.harden(new OkHttpClient(), origin, SecureHttp.API_TIMEOUT_MS);
		try (Response response = hardened.newCall(withBearer(trustedUrl("/to-self")).build()).execute()) {
			assertEquals(307, response.code());
		}
		assertEquals(1, trustedHits.size());
	}

	@Test
	public void theControlClientWouldHaveFollowedTheCrossHostRedirect() throws Exception {
		// Proves the test above can fail: without the hardening the redirect is chased.
		OkHttpClient plain = new OkHttpClient();
		try (Response response = plain.newCall(new Request.Builder().url(trustedUrl("/to-other")).build()).execute()) {
			assertEquals(200, response.code());
		}
		assertEquals(1, otherHits.size());
	}

	@Test
	public void requestsToAnyOtherOriginAreBlockedBeforeTheyAreSent() throws Exception {
		OkHttpClient client = SecureHttp.harden(new OkHttpClient(), origin, SecureHttp.API_TIMEOUT_MS);
		String foreign = "http://127.0.0.1:" + other.getAddress().getPort() + "/anything";

		try {
			client.newCall(withBearer(foreign).build()).execute().close();
			fail("expected the request to be blocked");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("unexpected origin"));
			assertFalse("the message never carries a token", expected.getMessage().contains(TestKit.TOKEN_A));
		}
		assertTrue(otherHits.isEmpty());
	}

	@Test
	public void theGuardRunsBeforeAnyInterceptorTheBaseClientAlreadyHad() throws Exception {
		List<String> seen = new CopyOnWriteArrayList<>();
		OkHttpClient base = new OkHttpClient.Builder()
			.addInterceptor(chain -> {
				seen.add(chain.request().url().toString());
				return chain.proceed(chain.request());
			})
			.build();
		OkHttpClient client = SecureHttp.harden(base, origin, SecureHttp.API_TIMEOUT_MS);

		try {
			client.newCall(new Request.Builder().url("http://127.0.0.1:" + other.getAddress().getPort() + "/x").build()).execute().close();
			fail("expected the request to be blocked");
		} catch (IOException expected) {
			// blocked
		}
		assertTrue("nothing downstream saw the blocked request", seen.isEmpty());

		try (Response response = client.newCall(new Request.Builder().url(trustedUrl("/ok")).build()).execute()) {
			assertEquals(200, response.code());
		}
		assertEquals("the base client's interceptors still apply to allowed requests", 1, seen.size());
	}

	@Test
	public void theHardenedClientHasNoCacheAndDoesNotFollowRedirectsOfAnyKind() throws Exception {
		OkHttpClient base = new OkHttpClient.Builder().cache(new Cache(temp.newFolder("http-cache"), 1024 * 1024)).build();
		assertNotNull(base.cache());
		assertTrue(base.followRedirects());

		OkHttpClient hardened = SecureHttp.harden(base, origin, SecureHttp.API_TIMEOUT_MS);

		assertNull("private responses are not written to disk", hardened.cache());
		assertFalse(hardened.followRedirects());
		assertFalse(hardened.followSslRedirects());
		assertNotNull("the original client is left alone", base.cache());
		assertTrue(base.followRedirects());
	}

	// ------------------------------------------------------------------ bounded call time

	@Test
	public void callTimeoutsAreFiniteAndLongerForUploads() {
		OkHttpClient api = SecureHttp.harden(new OkHttpClient(), origin, SecureHttp.API_TIMEOUT_MS);
		OkHttpClient upload = SecureHttp.harden(new OkHttpClient(), origin, SecureHttp.UPLOAD_TIMEOUT_MS);

		assertEquals(30_000, api.callTimeoutMillis());
		assertEquals(60_000, upload.callTimeoutMillis());
		assertTrue(upload.callTimeoutMillis() > api.callTimeoutMillis());
	}

	@Test(timeout = 20_000)
	public void aServerThatNeverAnswersEndsTheCallInsteadOfHangingForever() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		HttpServer hanging = server();
		hanging.setExecutor(Executors.newCachedThreadPool());
		hanging.createContext("/hang", exchange -> {
			trustedHits.add("/hang");
			try {
				release.await(15, TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});
		hanging.start();
		String url = "http://127.0.0.1:" + hanging.getAddress().getPort() + "/hang";
		OkHttpClient hardened = SecureHttp.harden(new OkHttpClient(), ApiOrigin.unsafeForTests("http://127.0.0.1:" + hanging.getAddress().getPort()), 400);

		long started = System.nanoTime();
		try {
			hardened.newCall(withBearer(url).build()).execute().close();
			fail("expected the call to time out");
		} catch (IOException expected) {
			assertFalse("the failure never carries the token", String.valueOf(expected.getMessage()).contains(TestKit.TOKEN_A));
		} finally {
			release.countDown();
			hanging.stop(0);
		}
		assertTrue("gave up promptly", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 10);
		assertEquals(1, trustedHits.size());
	}

	// ------------------------------------------------------------------ the clock is only learned from the API origin

	@Test
	public void aDateFromTheApiOriginIsUsed() throws Exception {
		long applied = observedOffsetAfter(new OkHttpClient(), trustedUrl("/dated"));
		assertEquals("control: the trusted origin's Date counts", java.time.Duration.ofHours(3).toMillis(), applied, 2_000);
	}

	@Test
	public void aPublicReadThatWasRedirectedToAnotherHostDoesNotSetTheClock() throws Exception {
		// An ordinary client follows the redirect, exactly like RevalApiService's public reads; the final answer is the other host's.
		OkHttpClient following = new OkHttpClient();
		try (Response response = get(following, trustedUrl("/dated-via-other"))) {
			assertEquals("it really did end up on the other host", String.valueOf(other.getAddress().getPort()),
				String.valueOf(response.request().url().port()));
		}
		assertEquals("so its Date says nothing about the API's time", 0, observedOffsetAfter(following, trustedUrl("/dated-via-other")), 1_000);
	}
}

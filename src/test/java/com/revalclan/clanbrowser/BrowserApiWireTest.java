package com.revalclan.clanbrowser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;

/** The clan browser against real sockets: what the server sees, and that a redirect cannot carry the token elsewhere. */
public class BrowserApiWireTest {
	private static final String VIEW = "{\"status\":\"success\",\"data\":{\"kind\":\"clog\",\"title\":\"served\"}}";

	private HttpServer trusted;
	private HttpServer other;
	private Rig rig;
	private BrowserApi api;
	private final List<String> trustedAuthorization = new CopyOnWriteArrayList<>();
	private final List<String> trustedQueries = new CopyOnWriteArrayList<>();
	private final List<String> otherHits = new CopyOnWriteArrayList<>();
	private final List<String> otherAuthorization = new CopyOnWriteArrayList<>();
	private final List<String> titles = new CopyOnWriteArrayList<>();
	private final List<String> failures = new CopyOnWriteArrayList<>();

	@Before
	public void setUp() throws Exception {
		trusted = server();
		other = server();
		other.createContext("/", exchange -> {
			otherHits.add(exchange.getRequestURI().toString());
			String authorization = exchange.getRequestHeaders().getFirst("Authorization");
			if (authorization != null) otherAuthorization.add(authorization);
			reply(exchange, 200, VIEW);
		});
		trusted.createContext("/plugin/clan-browser/view", exchange -> {
			String authorization = exchange.getRequestHeaders().getFirst("Authorization");
			trustedAuthorization.add(authorization == null ? "" : authorization);
			trustedQueries.add(exchange.getRequestURI().getRawQuery());
			reply(exchange, 200, VIEW);
		});
		trusted.start();
		other.start();

		String origin = "http://127.0.0.1:" + trusted.getAddress().getPort();
		rig = new Rig(originFor(origin));
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.tokens.onAccount(ACCOUNT_A);
		api = new BrowserApi(new OkHttpClient(), rig.gson, rig.tokens, originFor(origin), origin + "/plugin");
	}

	@After
	public void tearDown() throws Exception {
		trusted.stop(0);
		other.stop(0);
		rig.close();
	}

	private static HttpServer server() throws IOException {
		return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
	}

	private static void reply(HttpExchange exchange, int code, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(code, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	/** Waits for the call to end: the callbacks come back on the EDT. */
	private void read(String command, long accountHash) throws Exception {
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
		api.view(BrowserCommand.parse(command), accountHash, false, view -> {
			titles.add(view.title);
			done.countDown();
		}, message -> {
			failures.add(message);
			done.countDown();
		});
		assertTrue("the call ended", done.await(10, java.util.concurrent.TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> { });
	}

	@Test
	public void theServerSeesTheBearerAndTheAccountHashOfTheLoggedInAccount() throws Exception {
		read("reval clog vorkath", ACCOUNT_A);

		assertEquals(Collections.singletonList("Bearer " + TOKEN_A), trustedAuthorization);
		assertTrue(trustedQueries.get(0), trustedQueries.get(0).contains("accountHash=" + ACCOUNT_A));
		assertEquals(Collections.singletonList("served"), titles);
		assertEquals(Collections.emptyList(), failures);
	}

	@Test
	public void aPlayersCollectionLogReachesTheServerWithoutAnyCredential() throws Exception {
		read("reval clog vorkath --player Zezima", ACCOUNT_A);

		assertEquals(Collections.singletonList(""), trustedAuthorization);
		assertFalse(trustedQueries.get(0).contains("accountHash"));
		assertEquals(Collections.singletonList("served"), titles);
	}

	@Test
	public void aRedirectOfTheOwnViewIsNotFollowedSoTheTokenNeverLeaves() throws Exception {
		trusted.removeContext("/plugin/clan-browser/view");
		trusted.createContext("/plugin/clan-browser/view", exchange -> {
			trustedAuthorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
			exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + other.getAddress().getPort() + "/stolen");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});

		read("reval clog vorkath", ACCOUNT_A);

		assertEquals("asked once", 1, trustedAuthorization.size());
		assertTrue("the redirect target was never contacted", otherHits.isEmpty());
		assertTrue(otherAuthorization.isEmpty());
		assertEquals(Collections.emptyList(), titles);
		assertEquals(1, failures.size());
		assertFalse(failures.get(0).contains(TOKEN_A));
	}

	@Test
	public void evenAFollowedRedirectOfAPublicReadCarriesNoToken() throws Exception {
		trusted.removeContext("/plugin/clan-browser/view");
		trusted.createContext("/plugin/clan-browser/view", exchange -> {
			trustedAuthorization.add(exchange.getRequestHeaders().getFirst("Authorization") == null ? "" : "present");
			exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + other.getAddress().getPort() + "/public");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});

		read("reval clog vorkath --player Zezima", ACCOUNT_A); // public: RuneLite's own client may follow this

		assertEquals(Collections.singletonList(""), trustedAuthorization);
		assertEquals(Collections.singletonList("/public"), otherHits);
		assertTrue("no bearer ever reached the other server", otherAuthorization.isEmpty());
	}
}

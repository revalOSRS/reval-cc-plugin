package com.revalclan.clanbrowser;

import com.revalclan.api.common.ApiEndpoints;
import com.revalclan.auth.ApiOrigin;
import com.revalclan.auth.LinkException;
import com.revalclan.auth.LinkTokenManager;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;

/**
 * The clan browser's reads against the backend contract: a request that names an account carries that account's
 * bearer (and only that), every other read is anonymous, and nothing private, fresh or cached, outlives the
 * login it was made in.
 */
public class BrowserApiAuthTest {
	private static final String OWN_CLOG = "reval clog vorkath";
	private static final String PLAYERS_CLOG = "reval clog vorkath --player Zezima";

	private Rig rig;
	private BrowserApi api;
	private final List<String> titles = new CopyOnWriteArrayList<>();
	private final List<String> failures = new CopyOnWriteArrayList<>();

	@Before
	public void setUp() {
		rig = new Rig();
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.link(ACCOUNT_B, TOKEN_B);
		rig.tokens.onAccount(ACCOUNT_A);
		api = new BrowserApi(rig.client, rig.gson, rig.tokens, ApiOrigin.PRODUCTION, ApiEndpoints.BASE_URL);
		// the view tells who it was served to, so a view can be traced back to the token that fetched it
		rig.http.responder = request -> json(request, 200, view(whoIs(request)));
	}

	@After
	public void tearDown() throws Exception {
		rig.close();
	}

	private static String whoIs(Request request) {
		String authorization = request.header("Authorization");
		return authorization == null ? "public" : authorization.equals("Bearer " + TOKEN_A) ? "A" : authorization.equals("Bearer " + TOKEN_B) ? "B" : "?";
	}

	private static String view(String title) {
		return "{\"status\":\"success\",\"data\":{\"kind\":\"clog\",\"title\":\"" + title + "\"}}";
	}

	private static BrowserCommand command(String text) {
		return BrowserCommand.parse(text);
	}

	private static void flushEdt() throws Exception {
		SwingUtilities.invokeAndWait(() -> { });
	}

	private void read(String text, long accountHash, boolean refresh) throws Exception {
		api.view(command(text), accountHash, refresh, view -> titles.add(view.title), failures::add);
		flushEdt();
	}

	private void edt(Runnable task) throws Exception {
		SwingUtilities.invokeAndWait(task);
		flushEdt();
	}

	/** Parks the EDT so that whatever is delivered next sits in its queue until released. */
	private CountDownLatch blockEdt() throws Exception {
		CountDownLatch parked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		SwingUtilities.invokeLater(() -> {
			parked.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		assertTrue(parked.await(10, TimeUnit.SECONDS));
		return release;
	}

	private void assertNothingDelivered() {
		assertEquals(java.util.Collections.emptyList(), titles);
		assertEquals(java.util.Collections.emptyList(), failures);
	}

	// ------------------------------------------------------------------ authorization on the wire

	@Test
	public void ownCollectionLogCarriesTheBearerAndTheLoggedInAccountHash() throws Exception {
		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals(1, rig.http.requests.size());
		Request request = rig.http.last();
		assertEquals("GET", request.method());
		assertEquals("api.revalosrs.ee", request.url().host());
		assertEquals("/plugin/clan-browser/view", request.url().encodedPath());
		assertEquals(String.valueOf(ACCOUNT_A), request.url().queryParameter("accountHash"));
		assertEquals("Bearer " + TOKEN_A, request.header("Authorization"));
		assertEquals(Arrays.asList("Accept", "Authorization", "User-Agent"), headerNames(request));
		assertFalse("the token travels in the header only",
			everything(request).replace("Authorization: Bearer " + TOKEN_A, "").contains(TOKEN_A));
		assertEquals(Arrays.asList("A"), titles);
		assertEquals(java.util.Collections.emptyList(), failures);
	}

	@Test
	public void theBearerBelongsToTheLoggedInAccountWhoeverElseIsLinked() throws Exception {
		rig.tokens.onAccount(ACCOUNT_B);

		read(OWN_CLOG, ACCOUNT_B, false);

		assertEquals("Bearer " + TOKEN_B, rig.http.last().header("Authorization"));
		assertEquals(String.valueOf(ACCOUNT_B), rig.http.last().url().queryParameter("accountHash"));
		assertEquals(Arrays.asList("B"), titles);
	}

	@Test
	public void everyPublicReadIsAnonymousEvenWhileLinked() throws Exception {
		api.catalogue(catalogue -> titles.add("catalogue"), failures::add);
		flushEdt();
		read(PLAYERS_CLOG, ACCOUNT_A, false);
		read("reval leaderboard clog", ACCOUNT_A, false);
		read("reval luck vorkath", ACCOUNT_A, false);
		read(OWN_CLOG, 0, false); // nobody logged in as far as the window knows: no account is named

		assertEquals(5, rig.http.requests.size());
		for (Request request : rig.http.requests) {
			assertNull(request.url().toString(), request.header("Authorization"));
			assertNull(request.url().queryParameter("accountHash"));
			assertEquals(Arrays.asList("Accept", "User-Agent"), headerNames(request));
			assertFalse(everything(request).contains(TOKEN_A));
		}
		assertEquals(Arrays.asList("catalogue", "public", "public", "public", "public"), titles);
	}

	@Test
	public void aPlayersCollectionLogIsPublicEvenForAnUnlinkedViewer() throws Exception {
		rig.tokens.onAccount(ACCOUNT_C); // never linked

		read(PLAYERS_CLOG, ACCOUNT_C, false);

		assertNull(rig.http.last().header("Authorization"));
		assertEquals("Zezima", rig.http.last().url().queryParameter("player"));
		assertEquals(Arrays.asList("public"), titles);
	}

	// ------------------------------------------------------------------ nothing is sent without the right account and link

	@Test
	public void anotherAccountsHashIsRefusedBeforeAnythingIsSent() throws Exception {
		read(OWN_CLOG, ACCOUNT_B, false); // A is logged in

		assertEquals(0, rig.http.requests.size());
		assertEquals(Arrays.asList(new LinkException(LinkException.Kind.ACCOUNT_MISMATCH).getMessage()), failures);
		assertEquals(java.util.Collections.emptyList(), titles);
	}

	@Test
	public void nobodyLoggedInMeansNoPrivateRequest() throws Exception {
		rig.tokens.onLoggedOut();

		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals(0, rig.http.requests.size());
		assertEquals(Arrays.asList(new LinkException(LinkException.Kind.NOT_LOGGED_IN).getMessage()), failures);
	}

	@Test
	public void anUnlinkedAccountGetsTheLinkInstructionAndNothingIsSent() throws Exception {
		rig.tokens.onAccount(ACCOUNT_C);

		read(OWN_CLOG, ACCOUNT_C, false);

		assertEquals(0, rig.http.requests.size());
		assertEquals(Arrays.asList(LinkTokenManager.LINK_INSTRUCTION), failures);
	}

	@Test
	public void aLinkThatLooksExpiredIsNotUsedAndNotDeleted() throws Exception {
		rig.clock.advance(Duration.ofDays(91));

		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals(0, rig.http.requests.size());
		assertEquals(Arrays.asList(new LinkException(LinkException.Kind.EXPIRED_LOCALLY).getMessage()), failures);
		assertTrue("a wrong clock must not erase a valid link", rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void aServerOutsideTheTrustedOriginNeverGetsTheTokenOrTheRequest() throws Exception {
		for (String base : Arrays.asList("https://evil.example/plugin", "http://api.revalosrs.ee/plugin",
			"https://api.revalosrs.ee.evil.example/plugin", "https://api.revalosrs.ee:8443/plugin")) {
			BrowserApi elsewhere = new BrowserApi(rig.client, rig.gson, rig.tokens, ApiOrigin.PRODUCTION, base);
			failures.clear();

			elsewhere.view(command(OWN_CLOG), ACCOUNT_A, false, view -> titles.add(view.title), failures::add);
			flushEdt();

			assertEquals(base, Arrays.asList(new LinkException(LinkException.Kind.UNTRUSTED_ORIGIN).getMessage()), failures);
		}
		assertEquals("nothing left this computer", 0, rig.http.requests.size());
	}

	@Test
	public void theCredentialedClientNeverFollowsRedirectsHasNoCacheAndAFiniteTimeout() throws Exception {
		okhttp3.OkHttpClient client = api.credentialedClient();

		assertFalse(client.followRedirects());
		assertFalse(client.followSslRedirects());
		assertNull(client.cache());
		assertEquals(30_000, client.callTimeoutMillis());
		try {
			client.newCall(new Request.Builder().url("https://evil.example/plugin/clan-browser/view").build()).execute();
			fail("the client must refuse another origin");
		} catch (IOException expected) {
			assertFalse(String.valueOf(expected.getMessage()).contains(TOKEN_A));
		}
		assertEquals(0, rig.http.requests.size());
	}

	// ------------------------------------------------------------------ errors

	@Test
	public void aRevokedTokenIsForgottenOnTheServersWordAndTheMessageSaysSo() throws Exception {
		rig.http.responder = request -> json(request, 401,
			"{\"status\":\"error\",\"code\":\"invalid_plugin_credential\",\"message\":\"Token revoked\"}");

		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals(Arrays.asList(new LinkException(LinkException.Kind.EXPIRED).getMessage()), failures);
		assertFalse(rig.hasCredential(ACCOUNT_A));
		assertTrue(rig.hasCredential(ACCOUNT_B));
		assertFalse(failures.get(0).contains(TOKEN_A));
	}

	@Test
	public void anyOther401KeepsTheLinkAndSaysToTryAgain() throws Exception {
		for (String body : Arrays.asList("{\"status\":\"error\",\"code\":\"plugin_link_required\"}", "{}", "", "<html>login</html>")) {
			failures.clear();
			rig.http.responder = request -> json(request, 401, body);

			read(OWN_CLOG, ACCOUNT_A, false);

			assertEquals(body, Arrays.asList(new LinkException(LinkException.Kind.REFUSED).getMessage()), failures);
			assertTrue(body, rig.hasCredential(ACCOUNT_A));
		}
	}

	@Test
	public void aTokenRejectionOnAnotherStatusDeletesNothing() throws Exception {
		rig.http.responder = request -> json(request, 403,
			"{\"status\":\"error\",\"code\":\"invalid_plugin_credential\",\"message\":\"Not for this account\"}");

		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals(Arrays.asList("Not for this account"), failures);
		assertTrue(rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void theServersReasonIsShownAndUnreadableAnswersGetAPlainMessage() throws Exception {
		rig.http.responder = request -> json(request, 500, "{\"status\":\"error\",\"message\":\"The clan browser is rebuilding\"}");
		read(OWN_CLOG, ACCOUNT_A, false);
		rig.http.responder = request -> json(request, 200, "not json at all");
		read(OWN_CLOG, ACCOUNT_A, false);
		rig.http.responder = request -> json(request, 200, "{\"status\":\"error\",\"data\":{}}");
		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals(Arrays.asList("The clan browser is rebuilding", "This view could not be read. Please refresh and try again.",
			"This view could not be loaded. Please try again."), failures);
		assertEquals(java.util.Collections.emptyList(), titles);
		assertTrue(rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void aNetworkFailureIsAPlainMessageForPrivateAndPublicReads() throws Exception {
		rig.http.responder = request -> {
			throw new IOException("offline");
		};

		read(OWN_CLOG, ACCOUNT_A, false);
		read(PLAYERS_CLOG, ACCOUNT_A, false);

		assertEquals(Arrays.asList("The clan browser could not connect. Please try again.",
			"The clan browser could not connect. Please try again."), failures);
		assertTrue(rig.hasCredential(ACCOUNT_A));
	}

	// ------------------------------------------------------------------ the login the answer belongs to

	@Test
	public void anAnswerThatArrivesAfterAnAccountSwitchIsDroppedAndNotCached() throws Exception {
		rig.http.responder = request -> {
			rig.tokens.onAccount(ACCOUNT_B); // the switch happens while the request is out
			return json(request, 200, view("A"));
		};
		read(OWN_CLOG, ACCOUNT_A, false);
		assertNothingDelivered();

		rig.http.responder = request -> json(request, 200, view(whoIs(request)));
		rig.tokens.onAccount(ACCOUNT_A);
		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals("the dropped answer was not kept: it had to be asked again", 2, rig.http.requests.size());
		assertEquals(Arrays.asList("A"), titles);
	}

	@Test
	public void aFailureThatArrivesAfterLogoutOrShutdownIsDroppedToo() throws Exception {
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			throw new IOException("offline");
		};
		read(OWN_CLOG, ACCOUNT_A, false);
		assertNothingDelivered();

		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.responder = request -> {
			rig.tokens.shutDown();
			return json(request, 500, "{\"status\":\"error\",\"message\":\"x\"}");
		};
		read(OWN_CLOG, ACCOUNT_A, false);
		assertNothingDelivered();
	}

	@Test
	public void aRevocationForAnEndedLoginForgetsNothing() throws Exception {
		rig.http.responder = request -> {
			rig.tokens.onAccount(ACCOUNT_B);
			return json(request, 401, "{\"status\":\"error\",\"code\":\"invalid_plugin_credential\"}");
		};

		read(OWN_CLOG, ACCOUNT_A, false);

		assertNothingDelivered();
		assertTrue("a stale answer must not delete a link", rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void anAnswerQueuedForTheEdtIsStillDroppedIfTheAccountSwitchesBeforeItRuns() throws Exception {
		edt(() -> {
			api.view(command(OWN_CLOG), ACCOUNT_A, false, view -> titles.add(view.title), failures::add);
			rig.tokens.onAccount(ACCOUNT_B); // the answer is already queued behind this task
		});

		assertNothingDelivered();
	}

	@Test
	public void aPublicAnswerIsNotTiedToALogin() throws Exception {
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			return json(request, 200, view("public"));
		};

		read(PLAYERS_CLOG, ACCOUNT_A, false);

		assertEquals(Arrays.asList("public"), titles);
	}

	// ------------------------------------------------------------------ the cache

	@Test
	public void aCachedOwnViewIsServedWithoutAskingAgainForTheSameLogin() throws Exception {
		read(OWN_CLOG, ACCOUNT_A, false);
		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals(1, rig.http.requests.size());
		assertEquals(Arrays.asList("A", "A"), titles);
	}

	@Test
	public void refreshAsksAgainWithTheBearerAndTheRefreshFlag() throws Exception {
		read(OWN_CLOG, ACCOUNT_A, false);
		read(OWN_CLOG, ACCOUNT_A, true);

		assertEquals(2, rig.http.requests.size());
		assertEquals("true", rig.http.last().url().queryParameter("refresh"));
		assertEquals("Bearer " + TOKEN_A, rig.http.last().header("Authorization"));
		assertEquals(String.valueOf(ACCOUNT_A), rig.http.last().url().queryParameter("accountHash"));
	}

	@Test
	public void aCachedOwnViewIsNeverShownToAnotherAccountOrForAnotherAccountsHash() throws Exception {
		read(OWN_CLOG, ACCOUNT_A, false);
		titles.clear();

		rig.tokens.onAccount(ACCOUNT_B);
		read(OWN_CLOG, ACCOUNT_A, false); // a window that still thinks it is A
		assertEquals(Arrays.asList(new LinkException(LinkException.Kind.ACCOUNT_MISMATCH).getMessage()), failures);
		assertEquals(java.util.Collections.emptyList(), titles);

		failures.clear();
		read(OWN_CLOG, ACCOUNT_B, false);
		assertEquals("B asked for itself and got B's view, fetched with B's token", Arrays.asList("B"), titles);
		assertEquals("Bearer " + TOKEN_B, rig.http.last().header("Authorization"));
	}

	@Test
	public void aCachedOwnViewDoesNotSurviveLogoutEvenForTheSameAccountLoggingBackIn() throws Exception {
		read(OWN_CLOG, ACCOUNT_A, false);
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_A);

		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals("asked again, not served from the previous login", 2, rig.http.requests.size());
		assertEquals(Arrays.asList("A", "A"), titles);
	}

	@Test
	public void aCachedOwnViewIsNotServedOnceTheLinkIsGone() throws Exception {
		read(OWN_CLOG, ACCOUNT_A, false);
		titles.clear();
		// what any request does when the server says the token is no good
		rig.tokens.rejected(rig.tokens.grantForCurrentAccount(HttpUrl.get("https://api.revalosrs.ee/plugin/x?accountHash=" + ACCOUNT_A), null));

		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals(1, rig.http.requests.size());
		assertEquals(java.util.Collections.emptyList(), titles);
		assertEquals(Arrays.asList(LinkTokenManager.LINK_INSTRUCTION), failures);
	}

	@Test
	public void aCachedOwnViewIsNotServedOnceTheLinkLooksExpired() throws Exception {
		read(OWN_CLOG, ACCOUNT_A, false);
		titles.clear();
		rig.clock.advance(Duration.ofDays(91));

		read(OWN_CLOG, ACCOUNT_A, false);

		assertEquals(java.util.Collections.emptyList(), titles);
		assertEquals(Arrays.asList(new LinkException(LinkException.Kind.EXPIRED_LOCALLY).getMessage()), failures);
	}

	@Test
	public void aCachedOwnViewQueuedForTheEdtIsDroppedIfTheAccountSwitchesBeforeItRuns() throws Exception {
		read(OWN_CLOG, ACCOUNT_A, false);
		titles.clear();

		CountDownLatch release = blockEdt();
		api.view(command(OWN_CLOG), ACCOUNT_A, false, view -> titles.add(view.title), failures::add); // a cache hit, queued
		rig.tokens.onAccount(ACCOUNT_B);
		release.countDown();
		flushEdt();

		assertNothingDelivered();
	}

	@Test
	public void aCachedOwnViewAppliedInlineOnTheEdtStillNeedsTheCurrentLogin() throws Exception {
		read(OWN_CLOG, ACCOUNT_A, false);
		titles.clear();

		edt(() -> api.view(command(OWN_CLOG), ACCOUNT_A, false, view -> titles.add(view.title), failures::add));

		assertEquals("inline, in the same EDT event", Arrays.asList("A"), titles);
	}

	@Test
	public void publicViewsStayCachedAcrossLoginsBecauseNothingPrivateIsInThem() throws Exception {
		read(PLAYERS_CLOG, ACCOUNT_A, false);
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_B);

		read(PLAYERS_CLOG, ACCOUNT_B, false);

		assertEquals(1, rig.http.requests.size());
		assertEquals(Arrays.asList("public", "public"), titles);
	}
}

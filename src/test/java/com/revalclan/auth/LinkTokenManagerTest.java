package com.revalclan.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import com.revalclan.auth.LinkTokenManager.Grant;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;

public class LinkTokenManagerTest {
	private static final HttpUrl API = HttpUrl.get("https://api.revalosrs.ee/plugin/events");
	private static final HttpUrl HOOK = HttpUrl.get("https://api.revalosrs.ee/reval-webhook");

	private Rig rig;

	@Before
	public void setUp() {
		rig = new Rig();
	}

	@After
	public void tearDown() throws Exception {
		rig.close();
	}

	private void succeedWith(long accountHash, String token) {
		rig.http.responder = request -> json(request, 200, pairResponse(accountHash, token).toString());
	}

	private Grant required(HttpUrl url, Long bodyHash) throws LinkException {
		return rig.tokens.grantForCurrentAccount(url, bodyHash);
	}

	private void assertRefused(LinkException.Kind kind, HttpUrl url, Long bodyHash) {
		try {
			required(url, bodyHash);
			fail("expected " + kind);
		} catch (LinkException e) {
			assertEquals(kind, e.getKind());
		}
	}

	// ------------------------------------------------------------------ pairing request and result

	@Test
	public void pairingSendsOnlyTheCodeAndTheAccountHashWithoutAnyBearer() throws Exception {
		rig.tokens.onAccount(ACCOUNT_A);
		succeedWith(ACCOUNT_A, TOKEN_NEW);

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals(1, rig.http.requests.size());
		Request request = rig.http.last();
		assertEquals("POST", request.method());
		assertEquals(PAIR_URL, request.url().toString());
		JsonObject body = new Gson().fromJson(bodyText(request), JsonObject.class);
		assertEquals(new HashSet<>(Arrays.asList("code", "accountHash")), body.keySet());
		assertEquals(LINK_CODE, body.get("code").getAsString());
		assertTrue("the hash is a JSON string", body.get("accountHash").getAsJsonPrimitive().isString());
		assertEquals(String.valueOf(ACCOUNT_A), body.get("accountHash").getAsString());
		assertNull(request.header("Authorization"));
		assertNull(request.header("X-Member-Code"));
		assertTrue(request.header("User-Agent").startsWith("RuneLite-RevalClan-Plugin/"));
		assertEquals("application/json; charset=utf-8", request.body().contentType().toString());
	}

	@Test
	public void successStoresActivatesAndConsumesTheCode() throws Exception {
		rig.config.values.put("linkCode", LINK_CODE);
		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.requests.clear();
		rig.messages.clear();
		succeedWith(ACCOUNT_A, TOKEN_NEW);

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals(TOKEN_NEW, rig.tokens.credentialFor(ACCOUNT_A).getToken());
		assertTrue("persisted for the account", rig.hasCredential(ACCOUNT_A));
		assertFalse("the one-use code is cleared", rig.config.values.containsKey("linkCode"));
		assertEquals(Collections.singletonList(LinkTokenManager.LINKED), rig.messages);
		assertEquals(Collections.singletonList(ACCOUNT_A), rig.linked);
		assertEquals(TOKEN_NEW, required(API, null).getCredential().getToken());
	}

	@Test
	public void aCodeTypedWhilePairingIsNotClearedByTheOldOnesSuccess() throws Exception {
		rig.config.values.put("linkCode", LINK_CODE);
		rig.tokens.onAccount(ACCOUNT_A);
		String newer = repeat('e', 64);
		rig.http.responder = request -> {
			rig.config.values.put("linkCode", newer);
			return json(request, 200, pairResponse(ACCOUNT_A, TOKEN_NEW).toString());
		};

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals(newer, rig.config.values.get("linkCode"));
		assertNotNull("the link itself still worked", rig.tokens.credentialFor(ACCOUNT_A));
	}

	@Test
	public void failuresKeepTheCodeStoreNothingAndExplainThemselves() throws Exception {
		rig.config.values.put("linkCode", LINK_CODE);
		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.requests.clear();
		rig.messages.clear();

		rig.http.responder = request -> json(request, 403, "{\"status\":\"error\",\"message\":\"This code belongs to another\\naccount.\\u0007 Ask staff.\"}");
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertEquals("This code belongs to another account. Ask staff.", rig.messages.get(0));

		rig.http.responder = request -> json(request, 403, "<html>forbidden</html>");
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertTrue(rig.messages.get(1).contains("ask clan staff"));

		rig.http.responder = request -> json(request, 429, "{}");
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertTrue(rig.messages.get(2).contains("Too many"));

		rig.http.responder = request -> json(request, 500, "oops");
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertTrue(rig.messages.get(3).contains("HTTP 500"));

		rig.http.responder = request -> { throw new IOException("connection reset"); };
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertTrue(rig.messages.get(4).contains("Could not reach Reval"));

		assertEquals(5, rig.messages.size());
		assertEquals(LINK_CODE, rig.config.values.get("linkCode"));
		assertFalse(rig.hasCredential(ACCOUNT_A));
		assertNull(rig.tokens.credentialFor(ACCOUNT_A));
		assertTrue(rig.linked.isEmpty());
		for (String message : rig.messages) {
			assertFalse("never echo the code", message.contains(LINK_CODE));
		}
	}

	@Test
	public void serverMessagesAreCappedAtOneShortLine() {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.messages.clear();
		String huge = repeat('x', 1000);
		rig.http.responder = request -> json(request, 403, "{\"message\":\"" + huge + "\"}");
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertEquals(240, rig.messages.get(0).length());
	}

	@Test
	public void aRedirectIsAFailureNotSomethingToFollow() {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.messages.clear();
		rig.http.responder = request -> {
			okhttp3.Response redirect = json(request, 302, "");
			return redirect.newBuilder().header("Location", "https://evil.example/steal").build();
		};
		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals("only the one request, nothing chased the Location", 1, rig.http.requests.size());
		assertFalse(rig.hasCredential(ACCOUNT_A));
		assertEquals(1, rig.messages.size());
	}

	@Test
	public void invalidSuccessResponsesAreNeverStored() throws Exception {
		rig.config.values.put("linkCode", LINK_CODE);
		rig.tokens.onAccount(ACCOUNT_A);
		rig.messages.clear();
		String invalidMessage = new LinkException(LinkException.Kind.INVALID_RESPONSE).getMessage();

		rig.http.responder = request -> json(request, 200, pairResponse(ACCOUNT_B, TOKEN_NEW).toString());
		rig.tokens.submitLinkCode(LINK_CODE, true);
		rig.http.responder = request -> json(request, 200, pairResponse(ACCOUNT_A, "rp_" + repeat('Z', 64)).toString());
		rig.tokens.submitLinkCode(LINK_CODE, true);
		rig.http.responder = request -> json(request, 200, "{\"status\":\"success\"}");
		rig.tokens.submitLinkCode(LINK_CODE, true);
		rig.http.responder = request -> json(request, 200, "not json at all");
		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals(Arrays.asList(invalidMessage, invalidMessage, invalidMessage, invalidMessage), rig.messages);
		assertFalse(rig.hasCredential(ACCOUNT_A));
		assertFalse("a credential for another account is not smuggled in either", rig.hasCredential(ACCOUNT_B));
		assertEquals(LINK_CODE, rig.config.values.get("linkCode"));
		assertNull(rig.tokens.credentialFor(ACCOUNT_A));
	}

	// ------------------------------------------------------------------ input checks

	@Test
	public void codesThatAreNotSixtyFourLowercaseHexNeverReachTheNetwork() {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.messages.clear();
		String[] bad = {"123456", repeat('D', 64), repeat('d', 63), repeat('d', 65), repeat('g', 64),
			repeat('d', 32) + " " + repeat('d', 31), "rp_" + repeat('d', 64)};
		for (String code : bad) {
			rig.tokens.submitLinkCode(code, true);
		}
		assertTrue(rig.http.requests.isEmpty());
		assertEquals(bad.length, rig.messages.size());
		assertEquals(LinkTokenManager.BAD_FORMAT, rig.messages.get(0));
	}

	@Test
	public void surroundingWhitespaceIsTrimmedBeforeSending() throws Exception {
		rig.tokens.onAccount(ACCOUNT_A);
		succeedWith(ACCOUNT_A, TOKEN_NEW);
		rig.tokens.submitLinkCode("  " + LINK_CODE + "\r\n", true);
		JsonObject body = new Gson().fromJson(bodyText(rig.http.last()), JsonObject.class);
		assertEquals(LINK_CODE, body.get("code").getAsString());
	}

	@Test
	public void aCodeWhileLoggedOutAsksToLogInAndSendsNothing() {
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertEquals(Collections.singletonList(LinkTokenManager.LOGIN_FIRST), rig.messages);
		assertTrue(rig.http.requests.isEmpty());

		rig.tokens.submitLinkCode("", true);
		rig.tokens.submitLinkCode(null, true);
		assertEquals("clearing the setting is not an attempt", 1, rig.messages.size());
	}

	@Test
	public void theSameCodeIsNotSentTwiceWhileOneIsInFlight() {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.responder = request -> {
			rig.tokens.submitLinkCode(LINK_CODE, true);
			return json(request, 200, pairResponse(ACCOUNT_A, TOKEN_NEW).toString());
		};
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertEquals(1, rig.http.requests.size());
	}

	@Test
	public void aSavedCodeIsTriedOncePerAccountAtLoginAndNeverLooped() throws Exception {
		rig.config.values.put("linkCode", LINK_CODE);
		rig.http.responder = request -> json(request, 403, "{\"message\":\"Wrong account\"}");

		rig.tokens.onAccount(ACCOUNT_A);
		assertEquals(1, rig.http.requests.size());
		assertEquals("Wrong account", rig.messages.get(0));

		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_A);
		assertEquals("same code, same account: not retried by itself", 1, rig.http.requests.size());

		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_B);
		assertEquals("another account is a new attempt", 2, rig.http.requests.size());
		assertEquals(String.valueOf(ACCOUNT_B),
			new Gson().fromJson(bodyText(rig.http.last()), JsonObject.class).get("accountHash").getAsString());

		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertEquals("the player asking again always goes through", 3, rig.http.requests.size());
		assertEquals(LINK_CODE, rig.config.values.get("linkCode"));
	}

	@Test
	public void aSavedCodeIsNotUsedWhenTheAccountIsAlreadyLinked() {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.config.values.put("linkCode", LINK_CODE);
		rig.tokens.onAccount(ACCOUNT_A);
		assertTrue(rig.http.requests.isEmpty());
	}

	// ------------------------------------------------------------------ logout / switch / shutdown

	@Test
	public void aPairingThatFinishesAfterLogoutIsStoredButNeverActivated() throws Exception {
		rig.config.values.put("linkCode", LINK_CODE);
		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			return json(request, 200, pairResponse(ACCOUNT_A, TOKEN_NEW).toString());
		};
		rig.messages.clear();

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertTrue("not announced to a player who already left", rig.messages.isEmpty());
		assertTrue(rig.linked.isEmpty());
		assertEquals(-1, rig.tokens.currentAccountHash());
		assertRefused(LinkException.Kind.NOT_LOGGED_IN, API, null);
		assertTrue("the spent code is not left lying around", !rig.config.values.containsKey("linkCode"));
		assertTrue("kept for its own account", rig.hasCredential(ACCOUNT_A));

		rig.tokens.onAccount(ACCOUNT_A);
		assertEquals("found again at that account's next login", TOKEN_NEW, required(API, null).getCredential().getToken());
	}

	@Test
	public void aPairingThatFinishesAfterSwitchingAccountsNeverActsForTheNewOne() throws Exception {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			rig.tokens.onAccount(ACCOUNT_B);
			return json(request, 200, pairResponse(ACCOUNT_A, TOKEN_NEW).toString());
		};
		rig.messages.clear();

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals(ACCOUNT_B, rig.tokens.currentAccountHash());
		assertNull(rig.tokens.credentialFor(ACCOUNT_B));
		assertRefused(LinkException.Kind.NOT_LINKED, API, null);
		assertFalse("B's login gets no 'linked' message for A's pairing", rig.messages.contains(LinkTokenManager.LINKED));
		assertTrue(rig.linked.isEmpty());
		assertTrue(rig.hasCredential(ACCOUNT_A));
		assertFalse(rig.hasCredential(ACCOUNT_B));
	}

	@Test
	public void shutdownInvalidatesAPairingInFlight() throws Exception {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.responder = request -> {
			rig.tokens.shutDown();
			return json(request, 200, pairResponse(ACCOUNT_A, TOKEN_NEW).toString());
		};
		rig.messages.clear();

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertTrue(rig.messages.isEmpty());
		assertTrue(rig.linked.isEmpty());
		assertEquals(-1, rig.tokens.currentAccountHash());
		assertRefused(LinkException.Kind.NOT_LOGGED_IN, API, null);
	}

	@Test
	public void aFailureAfterLogoutIsNotShownToWhoeverIsThereNow() {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			rig.tokens.onAccount(ACCOUNT_B);
			return json(request, 403, "{\"message\":\"Wrong account\"}");
		};
		rig.messages.clear();
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertTrue(rig.messages.isEmpty());
	}

	@Test
	public void grantsGoStaleOnLogoutSwitchAndShutdownButNotOnAHop() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.link(ACCOUNT_B, TOKEN_B);
		rig.tokens.onAccount(ACCOUNT_A);

		Grant first = required(API, null);
		assertTrue(rig.tokens.isCurrent(first));
		assertFalse("a world hop reports the same account again", rig.tokens.onAccount(ACCOUNT_A));
		assertTrue(rig.tokens.isCurrent(first));

		rig.tokens.onLoggedOut();
		assertFalse(rig.tokens.isCurrent(first));
		rig.tokens.onAccount(ACCOUNT_A);
		assertFalse("logging in again is a new session", rig.tokens.isCurrent(first));

		Grant second = required(API, null);
		assertTrue(rig.tokens.isCurrent(second));
		assertTrue(rig.tokens.onAccount(ACCOUNT_B));
		assertFalse("switching accounts", rig.tokens.isCurrent(second));

		Grant third = required(API, null);
		rig.tokens.shutDown();
		assertFalse("shutdown", rig.tokens.isCurrent(third));
	}

	@Test
	public void invalidAccountHashesAreIgnored() {
		assertFalse(rig.tokens.onAccount(-1));
		assertFalse(rig.tokens.onAccount(0));
		assertEquals(-1, rig.tokens.currentAccountHash());
		rig.tokens.onAccount(ACCOUNT_A);
		assertFalse(rig.tokens.onAccount(-1));
		assertEquals("only the login screen ends a session", ACCOUNT_A, rig.tokens.currentAccountHash());
	}

	// ------------------------------------------------------------------ choosing the credential

	@Test
	public void theCurrentAccountOnlyEverGetsItsOwnStoredToken() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.link(ACCOUNT_B, TOKEN_B);

		rig.tokens.onAccount(ACCOUNT_A);
		assertEquals(TOKEN_A, required(API, null).getCredential().getToken());
		rig.tokens.onLoggedOut();
		assertRefused(LinkException.Kind.NOT_LOGGED_IN, API, null);

		rig.tokens.onAccount(ACCOUNT_B);
		assertEquals(TOKEN_B, required(API, null).getCredential().getToken());
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_C);
		assertRefused(LinkException.Kind.NOT_LINKED, API, null);
	}

	@Test
	public void anAccountHashInTheQueryOrBodyMustBeTheLoggedInAccounts() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.tokens.onAccount(ACCOUNT_A);
		String base = "https://api.revalosrs.ee/plugin/notifications?accountHash=";

		assertNotNull(required(HttpUrl.get(base + ACCOUNT_A), null));
		assertNotNull(required(API, ACCOUNT_A));
		assertNotNull(required(HttpUrl.get(base + ACCOUNT_A), ACCOUNT_A));

		assertRefused(LinkException.Kind.ACCOUNT_MISMATCH, HttpUrl.get(base + ACCOUNT_B), null);
		assertRefused(LinkException.Kind.ACCOUNT_MISMATCH, API, ACCOUNT_B);
		assertRefused(LinkException.Kind.ACCOUNT_MISMATCH, HttpUrl.get(base + ACCOUNT_A + "&accountHash=" + ACCOUNT_B), null);
		assertRefused(LinkException.Kind.ACCOUNT_MISMATCH, HttpUrl.get(base + "abc"), null);
		assertRefused(LinkException.Kind.ACCOUNT_MISMATCH, HttpUrl.get(base), null);
		assertRefused(LinkException.Kind.ACCOUNT_MISMATCH, HttpUrl.get(base + ACCOUNT_A), ACCOUNT_B);
	}

	@Test
	public void credentialsAreNeverGrantedForAnotherOrigin() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.tokens.onAccount(ACCOUNT_A);
		String[] foreign = {
			"http://api.revalosrs.ee/plugin/events",
			"https://www.revalosrs.ee/plugin/events",
			"https://evil.example/plugin/events",
			"https://api.revalosrs.ee.evil.example/plugin/events",
			"https://api.revalosrs.ee:8443/plugin/events",
			"https://api.revalosrs.ee@evil.example/plugin/events",
		};
		for (String url : foreign) {
			assertRefused(LinkException.Kind.UNTRUSTED_ORIGIN, HttpUrl.get(url), null);
			assertNull(url, rig.tokens.grantForEvent(HttpUrl.get(url), ACCOUNT_A));
		}
	}

	@Test
	public void aBufferedEventKeepsTheTokenOfTheAccountItWasRecordedFor() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.link(ACCOUNT_B, TOKEN_B);
		rig.tokens.onAccount(ACCOUNT_B);

		assertEquals(TOKEN_A, rig.tokens.grantForEvent(HOOK, ACCOUNT_A).getCredential().getToken());
		assertEquals(TOKEN_B, rig.tokens.grantForEvent(HOOK, ACCOUNT_B).getCredential().getToken());
		assertNull("not linked: no token, so no telemetry", rig.tokens.grantForEvent(HOOK, ACCOUNT_C));
		assertNull(rig.tokens.grantForEvent(HOOK, -1));
		assertNull(rig.tokens.grantForEvent(HOOK, 0));
		assertEquals("looking up another account's token does not change who is current",
			ACCOUNT_B, rig.tokens.currentAccountHash());
	}

	@Test
	public void eventGrantsSurviveLogoutBecauseTheyBelongToTheEventNotTheSession() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.tokens.onAccount(ACCOUNT_A);
		Grant grant = rig.tokens.grantForEvent(HOOK, ACCOUNT_A);
		rig.tokens.onLoggedOut();
		assertTrue(rig.tokens.isCurrent(grant));
		assertNotNull("and the token is reloaded from storage for it", rig.tokens.grantForEvent(HOOK, ACCOUNT_A));
	}

	@Test
	public void aCredentialThatLooksExpiredIsNotUsedButIsKept() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.tokens.onAccount(ACCOUNT_A);
		assertNotNull(required(API, null));

		rig.clock.advance(Duration.ofDays(91));

		assertRefused(LinkException.Kind.EXPIRED_LOCALLY, API, null);
		assertNull(rig.tokens.grantForEvent(HOOK, ACCOUNT_A));
		assertNull(rig.tokens.credentialFor(ACCOUNT_A));
		assertFalse(rig.tokens.isLinked(ACCOUNT_A));
		assertTrue("not deleted: only the server may end a link", rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void aClockThatWasAheadAndIsFixedGetsTheSameCredentialBack() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.clock.advance(Duration.ofDays(3650)); // ten years ahead
		rig.tokens.onAccount(ACCOUNT_A);
		assertRefused(LinkException.Kind.EXPIRED_LOCALLY, API, null);
		assertNull(rig.tokens.credentialFor(ACCOUNT_A));

		rig.clock.set(NOW); // the player fixed the system time
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_A);

		assertEquals("a 90-day server token survived a wrong clock", TOKEN_A, required(API, null).getCredential().getToken());
	}

	@Test
	public void aClockThatIsFarOffIsNamedAsTheReasonWhenTheServersDateShowsIt() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.clock.advance(Duration.ofDays(3650));
		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.responder = request -> withDate(json(request, 403, "{}"), NOW.plus(Duration.ofDays(3650)).minus(Duration.ofDays(3650)));
		rig.tokens.submitLinkCode(LINK_CODE, true); // any answer from the API tells the server's time

		assertRefused(LinkException.Kind.CLOCK_OFF, API, null);
		rig.messages.clear();
		rig.tokens.noticeLinkRequired(ACCOUNT_A);
		assertEquals(Collections.singletonList(LinkException.Kind.CLOCK_OFF.message()), rig.messages);
		rig.tokens.noticeLinkRequired(ACCOUNT_A);
		assertEquals("once per login", 1, rig.messages.size());
		assertTrue(rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void theExpiryMessageIsGivenOncePerLoginAndKeepsTheFile() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.clock.advance(Duration.ofDays(120));
		rig.tokens.onAccount(ACCOUNT_A);

		rig.tokens.noticeLinkRequired(ACCOUNT_A);
		rig.tokens.noticeLinkRequired(ACCOUNT_A);

		assertEquals(Collections.singletonList(LinkException.Kind.EXPIRED_LOCALLY.message()), rig.messages);
		assertTrue(rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void aNewLinkReplacesAnExpiredLookingOneAndWorks() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.clock.advance(Duration.ofDays(120));
		rig.tokens.onAccount(ACCOUNT_A);
		succeedWithClock(ACCOUNT_A, TOKEN_NEW);

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals(TOKEN_NEW, required(API, null).getCredential().getToken());
		assertEquals(Collections.singletonList(ACCOUNT_A), rig.linked);
	}

	@Test
	public void aSavedLinkCodeIsTriedAtLoginWhenTheOldLinkLooksExpired() {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.clock.advance(Duration.ofDays(120));
		rig.config.values.put("linkCode", LINK_CODE);
		succeedWithClock(ACCOUNT_A, TOKEN_NEW);

		rig.tokens.onAccount(ACCOUNT_A);

		assertEquals(1, rig.http.requests.size());
		assertTrue(rig.messages.contains(LinkTokenManager.LINKED));
	}

	/** A pairing answer whose 90-day expiry is measured from the test clock as it is now (the server and the PC agree). */
	private void succeedWithClock(long accountHash, String token) {
		JsonObject body = pairResponse(accountHash, token);
		body.getAsJsonObject("data").addProperty("expiresAt", rig.clock.instant().plus(Duration.ofDays(90)).toString());
		rig.http.responder = request -> json(request, 200, body.toString());
	}

	@Test
	public void aRejectedTokenIsForgottenButANewerOneIsNot() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.tokens.onAccount(ACCOUNT_A);
		Grant old = required(API, null);

		rig.link(ACCOUNT_A, TOKEN_NEW); // re-linked meanwhile
		rig.tokens.rejected(old);

		assertTrue("the newer link is left alone", rig.hasCredential(ACCOUNT_A));
		assertEquals(TOKEN_NEW, rig.tokens.credentialFor(ACCOUNT_A).getToken());

		rig.tokens.rejected(required(API, null));
		assertFalse(rig.hasCredential(ACCOUNT_A));
		assertNull(rig.tokens.credentialFor(ACCOUNT_A));
		assertRefused(LinkException.Kind.NOT_LINKED, API, null);
	}

	@Test
	public void rejectionTellsThePlayerOncePerLoginAndNeverTheWrongPlayer() throws Exception {
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.link(ACCOUNT_B, TOKEN_B);
		rig.tokens.onAccount(ACCOUNT_A);
		Grant a = required(API, null);
		Grant eventForB = rig.tokens.grantForEvent(HOOK, ACCOUNT_B);

		rig.tokens.rejected(eventForB);
		assertTrue("B is not the one playing", rig.messages.isEmpty());
		assertFalse(rig.hasCredential(ACCOUNT_B));

		rig.tokens.rejected(a);
		assertEquals(Collections.singletonList(LinkException.Kind.EXPIRED.message()), rig.messages);
	}

	@Test
	public void theLinkInstructionIsGivenOncePerLoginAndOnlyToTheLoggedInAccount() {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.tokens.noticeLinkRequired(ACCOUNT_A);
		rig.tokens.noticeLinkRequired(ACCOUNT_A);
		rig.tokens.noticeLinkRequired(ACCOUNT_A);
		rig.tokens.noticeLinkRequired(ACCOUNT_B);
		rig.tokens.noticeLinkRequired(-1);
		assertEquals(Collections.singletonList(LinkTokenManager.LINK_INSTRUCTION), rig.messages);

		rig.tokens.onLoggedOut();
		rig.tokens.noticeLinkRequired(ACCOUNT_A);
		assertEquals("nobody is logged in", 1, rig.messages.size());

		rig.tokens.onAccount(ACCOUNT_A);
		rig.tokens.noticeLinkRequired(ACCOUNT_A);
		assertEquals("a new login hears it again", 2, rig.messages.size());
	}

	// ------------------------------------------------------------------ server time

	private void answerWithServerTime(java.time.Instant serverTime) {
		rig.http.responder = request -> withDate(json(request, 403, "{}"), serverTime);
		rig.tokens.onAccount(ACCOUNT_A);
		rig.tokens.submitLinkCode(LINK_CODE, true);
	}

	@Test
	public void aLocalClockThatIsFarOffIsCorrectedFromTheServersDateHeader() {
		assertEquals("trusted until told otherwise", NOW.toEpochMilli(), rig.tokens.serverNowMillis());

		answerWithServerTime(NOW.plus(Duration.ofMinutes(12)));
		assertEquals(NOW.plus(Duration.ofMinutes(12)).toEpochMilli(), rig.tokens.serverNowMillis());

		rig.clock.advance(Duration.ofSeconds(30));
		assertEquals("keeps counting with the local clock", NOW.plus(Duration.ofMinutes(12)).plusSeconds(30).toEpochMilli(),
			rig.tokens.serverNowMillis());
	}

	@Test
	public void aLocalClockThatIsBehindIsCorrectedToo() {
		answerWithServerTime(NOW.minus(Duration.ofMinutes(9)));
		assertEquals(NOW.minus(Duration.ofMinutes(9)).toEpochMilli(), rig.tokens.serverNowMillis());
	}

	@Test
	public void smallDifferencesAreIgnoredAndACorrectClockResetsTheOffset() {
		answerWithServerTime(NOW.plus(Duration.ofSeconds(20)));
		assertEquals("within a minute: Date has whole-second precision, trust the local clock",
			NOW.toEpochMilli(), rig.tokens.serverNowMillis());

		answerWithServerTime(NOW.plus(Duration.ofMinutes(12)));
		assertNotEquals(NOW.toEpochMilli(), rig.tokens.serverNowMillis());
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_B);
		rig.http.responder = request -> withDate(json(request, 403, "{}"), NOW);
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertEquals("the user fixed their clock", NOW.toEpochMilli(), rig.tokens.serverNowMillis());
	}

	@Test
	public void aCachedResponseSaysNothingAboutTheClock() {
		rig.http.responder = request -> {
			okhttp3.Response fromCache = withDate(json(request, 200, "{}"), NOW.minus(Duration.ofHours(5)));
			okhttp3.Response earlier = json(request, 200, "{}").newBuilder().body(null).build();
			return fromCache.newBuilder().cacheResponse(earlier).build();
		};
		rig.tokens.onAccount(ACCOUNT_A);
		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals("a Date from last night's cache entry is not the time now", NOW.toEpochMilli(), rig.tokens.serverNowMillis());
	}

	@Test
	public void responsesWithoutADateHeaderChangeNothing() {
		answerWithServerTime(NOW.plus(Duration.ofMinutes(12)));
		rig.http.responder = request -> json(request, 403, "{}");
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertEquals(NOW.plus(Duration.ofMinutes(12)).toEpochMilli(), rig.tokens.serverNowMillis());
	}

	@Test
	public void onlyAResponseThatReallyCameFromTheApiOriginTeachesTheClock() {
		rig.tokens.onAccount(ACCOUNT_A);
		for (String foreign : new String[] {
			"https://www.revalosrs.ee/some/page", "https://evil.example/api", "http://api.revalosrs.ee/plugin/events",
			"https://api.revalosrs.ee:8443/plugin/events", "https://api.revalosrs.ee.evil.example/x"}) {
			Request redirectedHere = new Request.Builder().url(foreign).build();
			rig.tokens.observeServerTime(withDate(json(redirectedHere, 200, "{}"), NOW.plus(Duration.ofMinutes(30))));
			assertEquals(foreign, NOW.toEpochMilli(), rig.tokens.serverNowMillis());
		}

		Request ours = new Request.Builder().url("https://api.revalosrs.ee/plugin/events").build();
		rig.tokens.observeServerTime(withDate(json(ours, 200, "{}"), NOW.plus(Duration.ofMinutes(30))));
		assertEquals(NOW.plus(Duration.ofMinutes(30)).toEpochMilli(), rig.tokens.serverNowMillis());
	}

	@Test
	public void aDateFromAPublicReadThatEndedOnAnotherHostIsIgnoredEvenIfItWasAskedOfTheApi() {
		Request asked = new Request.Builder().url("https://api.revalosrs.ee/plugin/events").build();
		Request ended = new Request.Builder().url("https://cdn.example/redirected").build();
		okhttp3.Response response = withDate(json(ended, 200, "{}"), NOW.plus(Duration.ofHours(5)))
			.newBuilder().request(ended).priorResponse(json(asked, 302, "").newBuilder().body(null).build()).build();

		rig.tokens.observeServerTime(response);

		assertEquals(NOW.toEpochMilli(), rig.tokens.serverNowMillis());
	}

	@Test
	public void aDifferenceOfMoreThanADayIsNotDriftAndIsNeverUsedToStampEvents() {
		for (Duration absurd : new Duration[] {Duration.ofHours(25), Duration.ofDays(400), Duration.ofDays(3650), Duration.ofDays(-3650)}) {
			answerWithServerTime(NOW.plus(absurd));
			assertEquals(absurd.toString(), NOW.toEpochMilli(), rig.tokens.serverNowMillis());
		}
	}

	@Test
	public void theLargestBelievableDriftIsStillCorrected() {
		answerWithServerTime(NOW.plus(Duration.ofHours(23)));
		assertEquals(NOW.plus(Duration.ofHours(23)).toEpochMilli(), rig.tokens.serverNowMillis());
	}

	@Test
	public void anAbsurdDateDoesNotUndoAnEarlierBelievableCorrection() {
		answerWithServerTime(NOW.plus(Duration.ofMinutes(12)));
		rig.http.responder = request -> withDate(json(request, 403, "{}"), NOW.plus(Duration.ofDays(900)));
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertEquals(NOW.plus(Duration.ofMinutes(12)).toEpochMilli(), rig.tokens.serverNowMillis());
	}

	// ------------------------------------------------------------------ a pairing answer is judged against the best clock

	@Test
	public void aPairingAnswerThatIsAlreadyOverByACorrectedClockIsInvalid() {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.messages.clear();
		// Ordinary 12 minute drift is corrected, so an expiry 5 minutes ago is still in the past
		rig.http.responder = request -> withDate(json(request, 200, withExpiry(NOW.minus(Duration.ofMinutes(5))).toString()), NOW.plus(Duration.ofMinutes(12)));

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals(Collections.singletonList(LinkException.Kind.INVALID_RESPONSE.message()), rig.messages);
		assertFalse(rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void aPairingAnswerUsesTheCorrectedClockNotTheRawOne() throws Exception {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.messages.clear();
		// The PC is 20 minutes slow: by the raw clock this expiry (10 min from "now") is valid, by the server's it is long gone
		rig.clock.set(NOW.minus(Duration.ofMinutes(20)));
		rig.http.responder = request -> withDate(json(request, 200, withExpiry(NOW.minus(Duration.ofMinutes(10))).toString()), NOW);

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertFalse("raw local time would have accepted it", rig.hasCredential(ACCOUNT_A));
		assertEquals(Collections.singletonList(LinkException.Kind.INVALID_RESPONSE.message()), rig.messages);
	}

	@Test
	public void aComputerClockTenYearsAheadIsToldToFixItsTimeInsteadOfBeingBlamedOnTheServer() {
		rig.clock.advance(Duration.ofDays(3650));
		rig.tokens.onAccount(ACCOUNT_A);
		rig.messages.clear();
		rig.http.responder = request -> withDate(json(request, 200, withExpiry(NOW.plus(Duration.ofDays(90))).toString()), NOW);

		rig.tokens.submitLinkCode(LINK_CODE, true);

		assertEquals(Collections.singletonList(LinkException.Kind.CLOCK_OFF.message()), rig.messages);
		assertFalse(rig.hasCredential(ACCOUNT_A));
		assertEquals("the code stays so the player can try again after fixing the clock", LINK_CODE, rig.config.values.getOrDefault("linkCode", LINK_CODE));
		assertTrue(rig.linked.isEmpty());
	}

	private static JsonObject withExpiry(java.time.Instant expiresAt) {
		JsonObject body = pairResponse(ACCOUNT_A, TOKEN_NEW);
		body.getAsJsonObject("data").addProperty("expiresAt", expiresAt.toString());
		return body;
	}

	// ------------------------------------------------------------------ bounded pairing

	@Test(timeout = 30_000)
	public void aPairingThatHangsGivesUpAndTheSameCodeCanBeTriedAgain() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger hits = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/plugin/auth/pair", exchange -> {
			hits.incrementAndGet();
			try {
				release.await(20, TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});
		server.start();
		try {
			String origin = "http://127.0.0.1:" + server.getAddress().getPort();
			LinkTokenManager manager = new LinkTokenManager(rig.store, new okhttp3.OkHttpClient(), rig.gson,
				ApiOrigin.unsafeForTests(origin), origin + "/plugin/auth/pair", rig.clock, 400);
			manager.start(rig.messages::add, rig.linked::add);
			manager.onAccount(ACCOUNT_A);
			rig.messages.clear();

			manager.submitLinkCode(LINK_CODE, true);
			awaitMessages(1);
			assertTrue(rig.messages.get(0), rig.messages.get(0).contains("Could not reach Reval"));

			manager.submitLinkCode(LINK_CODE, true); // the same code: no longer stuck "in flight"
			awaitMessages(2);
			assertEquals("the second attempt really went out", 2, hits.get());
		} finally {
			release.countDown();
			server.stop(0);
		}
	}

	private void awaitMessages(int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while (rig.messages.size() < count && System.nanoTime() < deadline) Thread.sleep(25);
		assertTrue("expected " + count + " message(s), got " + rig.messages, rig.messages.size() >= count);
	}

	@Test
	public void thePairingClientHasAFiniteCallTimeoutAndNoRedirects() {
		assertEquals(30_000, rig.tokens.pairClient().callTimeoutMillis());
		assertFalse(rig.tokens.pairClient().followRedirects());
		assertNull(rig.tokens.pairClient().cache());
	}

	@Test
	public void theInstructionIsHeldBackWhileALinkIsBeingSetUp() {
		rig.tokens.onAccount(ACCOUNT_A);
		rig.http.responder = request -> {
			rig.tokens.noticeLinkRequired(ACCOUNT_A);
			return json(request, 200, pairResponse(ACCOUNT_A, TOKEN_NEW).toString());
		};
		rig.messages.clear();
		rig.tokens.submitLinkCode(LINK_CODE, true);
		assertEquals(Collections.singletonList(LinkTokenManager.LINKED), rig.messages);
	}
}

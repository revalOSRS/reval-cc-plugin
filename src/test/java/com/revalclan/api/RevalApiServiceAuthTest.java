package com.revalclan.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.auth.ApiOrigin;
import com.revalclan.auth.LinkException;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;

/** What actually goes on the wire from {@link RevalApiService}, and what happens to answers that come back. */
public class RevalApiServiceAuthTest {
	private Rig rig;
	private RevalApiService api;
	private final List<Object> results = new ArrayList<>();
	private final List<Exception> errors = new ArrayList<>();

	@Before
	public void setUp() {
		rig = new Rig();
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.link(ACCOUNT_B, TOKEN_B);
		rig.tokens.onAccount(ACCOUNT_A);
		api = new RevalApiService(rig.client, rig.gson, rig.tokens, ApiOrigin.PRODUCTION);
		// satisfies both envelope shapes: the plugin API ("status") and the public API ("success")
		rig.http.responder = request -> json(request, 200, "{\"status\":\"success\",\"success\":true}");
	}

	@After
	public void tearDown() throws Exception {
		rig.close();
	}

	private <T> Consumer<T> ok() {
		return results::add;
	}

	private Consumer<Exception> failed() {
		return errors::add;
	}

	/** Every call that must be authenticated, parameterised by the account it claims to act for. */
	private Map<String, LongConsumer> authenticatedCalls() {
		Map<String, LongConsumer> calls = new LinkedHashMap<>();
		calls.put("registerForEvent", h -> api.registerForEvent("evt", h, ok(), failed()));
		calls.put("cancelEventRegistration", h -> api.cancelEventRegistration("evt", h, ok(), failed()));
		calls.put("castVote", h -> api.castVote("vote", "opt", h, ok(), failed()));
		calls.put("pickLeaguesBingoRegion", h -> api.pickLeaguesBingoRegion("evt", h, "Asgarnia", null, ok(), failed()));
		calls.put("acknowledgeNotifications", h -> api.acknowledgeNotifications(h, Arrays.asList(1, 2), ok(), failed()));
		calls.put("fetchNotifications", h -> api.fetchNotifications(h, ok(), failed()));
		calls.put("fetchLeaguesBingoMe", h -> api.fetchLeaguesBingoMe("evt", h, ok(), failed()));
		calls.put("adminLogin", h -> api.adminLogin(h, "Nick", ok(), failed()));
		return calls;
	}

	/** Calls that act for the logged-in account without naming it. */
	private Map<String, Runnable> implicitAccountCalls() {
		Map<String, Runnable> calls = new LinkedHashMap<>();
		calls.put("fetchPendingRankChanges", () -> api.fetchPendingRankChanges(ok(), failed()));
		calls.put("actualizeRankChange", () -> api.actualizeRankChange(7, ok(), failed()));
		return calls;
	}

	private static String withoutAuthorizationHeader(Request request) throws IOException {
		return everything(request).replace("Authorization: Bearer " + TOKEN_A, "");
	}

	// ------------------------------------------------------------------ authenticated calls

	@Test
	public void everyMutationAndPrivateReadCarriesTheBearerAndNoOtherIdentity() throws Exception {
		List<Runnable> all = new ArrayList<>(implicitAccountCalls().values());
		authenticatedCalls().values().forEach(call -> all.add(() -> call.accept(ACCOUNT_A)));

		for (Runnable call : all) {
			rig.http.requests.clear();
			call.run();

			assertEquals(1, rig.http.requests.size());
			Request request = rig.http.last();
			assertEquals("https", request.url().scheme());
			assertEquals("api.revalosrs.ee", request.url().host());
			assertEquals("Bearer " + TOKEN_A, request.header("Authorization"));
			assertEquals(Arrays.asList("Accept", "Authorization", "Content-Type", "User-Agent"), headerNames(request));
			assertNull(request.header("X-Member-Code"));
			assertFalse("the token travels in the header only",
				withoutAuthorizationHeader(request).contains(TOKEN_A));
		}
		assertEquals(Collections.emptyList(), errors);
		assertEquals(all.size(), results.size());
	}

	@Test
	public void theAccountHashInEveryBodyAndQueryIsTheLinkedAccount() throws Exception {
		api.registerForEvent("evt", ACCOUNT_A, ok(), failed());
		api.cancelEventRegistration("evt", ACCOUNT_A, ok(), failed());
		api.pickLeaguesBingoRegion("evt", ACCOUNT_A, "Asgarnia", "team-1", ok(), failed());
		api.acknowledgeNotifications(ACCOUNT_A, Arrays.asList(4, 5), ok(), failed());
		api.adminLogin(ACCOUNT_A, "Nick", ok(), failed());
		for (Request request : rig.http.requests) {
			JsonObject body = new Gson().fromJson(bodyText(request), JsonObject.class);
			assertEquals(request.url().toString(), String.valueOf(ACCOUNT_A), body.get("accountHash").getAsString());
		}

		rig.http.requests.clear();
		api.castVote("vote", "opt", ACCOUNT_A, ok(), failed());
		api.fetchNotifications(ACCOUNT_A, ok(), failed());
		api.fetchLeaguesBingoMe("evt", ACCOUNT_A, ok(), failed());
		for (Request request : rig.http.requests) {
			assertEquals(request.url().toString(), String.valueOf(ACCOUNT_A), request.url().queryParameter("accountHash"));
		}
	}

	@Test
	public void voteOptionsAreJsonEncodedNotConcatenated() throws Exception {
		api.castVote("vote", "a\"b\\c", ACCOUNT_A, ok(), failed());
		JsonObject body = new Gson().fromJson(bodyText(rig.http.last()), JsonObject.class);
		assertEquals("a\"b\\c", body.get("optionId").getAsString());
		assertEquals(1, body.size());
	}

	@Test
	public void anUnlinkedAccountCannotMutateOrReadPrivateDataAndNothingIsSent() {
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_C);

		authenticatedCalls().values().forEach(call -> call.accept(ACCOUNT_C));
		implicitAccountCalls().values().forEach(Runnable::run);

		assertTrue("nothing left the machine", rig.http.requests.isEmpty());
		assertTrue(results.isEmpty());
		assertEquals(10, errors.size());
		for (Exception error : errors) {
			assertEquals(LinkException.Kind.NOT_LINKED, ((LinkException) error).getKind());
			assertTrue(error.getMessage().contains("Link code"));
		}
	}

	@Test
	public void aLoggedOutPluginCannotMutateOrReadPrivateDataEither() {
		rig.tokens.onLoggedOut();

		authenticatedCalls().values().forEach(call -> call.accept(ACCOUNT_A));
		implicitAccountCalls().values().forEach(Runnable::run);

		assertTrue(rig.http.requests.isEmpty());
		assertEquals(10, errors.size());
		for (Exception error : errors) {
			assertEquals(LinkException.Kind.NOT_LOGGED_IN, ((LinkException) error).getKind());
		}
	}

	@Test
	public void actingForAnotherAccountIsRefusedBeforeAnythingIsSent() {
		authenticatedCalls().values().forEach(call -> call.accept(ACCOUNT_B));

		assertTrue("B is linked too, but A is the one logged in", rig.http.requests.isEmpty());
		assertEquals(8, errors.size());
		for (Exception error : errors) {
			assertEquals(LinkException.Kind.ACCOUNT_MISMATCH, ((LinkException) error).getKind());
		}
	}

	// ------------------------------------------------------------------ reads: which need the bearer

	/** Every read that names an account: the backend answers 401 to these without a bearer. */
	private Map<String, LongConsumer> accountScopedReads() {
		Map<String, LongConsumer> calls = new LinkedHashMap<>();
		calls.put("fetchAccount", h -> api.fetchAccount(h, ok(), failed()));
		calls.put("fetchAchievementDefinitions", h -> api.fetchAchievementDefinitions(h, ok(), failed()));
		calls.put("fetchDiaries", h -> api.fetchDiaries(h, ok(), failed()));
		calls.put("fetchChallenges", h -> api.fetchChallenges(h, ok(), failed()));
		calls.put("fetchMyVote", h -> api.fetchMyVote("vote", h, ok(), failed()));
		calls.put("fetchNotifications", h -> api.fetchNotifications(h, ok(), failed()));
		calls.put("fetchLeaguesBingoMe", h -> api.fetchLeaguesBingoMe("evt", h, ok(), failed()));
		return calls;
	}

	/** Reads of public data: no account in the request, so no bearer. */
	private List<Runnable> publicReads() {
		return Arrays.asList(
			() -> api.fetchLeaderboard(ok(), failed()),
			() -> api.fetchAccountById(5, ok(), failed()),
			() -> api.fetchEvents(ok(), failed()),
			() -> api.fetchAchievementDefinitions(ok(), failed()),
			() -> api.fetchDiaries(ok(), failed()),
			() -> api.fetchChallenges(ok(), failed()),
			() -> api.fetchAnnouncements(ok(), failed()),
			() -> api.fetchScheduledCompetitions(ok(), failed()),
			() -> api.fetchVotes(ok(), failed()),
			() -> api.fetchActiveTeams(ok(), failed()),
			() -> api.fetchFeaturedRace(ok(), failed()),
			() -> api.fetchLeaguesBingoEvent("evt", ok(), failed()));
	}

	@Test
	public void everyReadThatNamesTheAccountCarriesTheBearer() {
		for (Map.Entry<String, LongConsumer> read : accountScopedReads().entrySet()) {
			rig.http.requests.clear();
			read.getValue().accept(ACCOUNT_A);

			assertEquals(read.getKey(), 1, rig.http.requests.size());
			Request request = rig.http.last();
			assertEquals(read.getKey(), "Bearer " + TOKEN_A, request.header("Authorization"));
			assertEquals(read.getKey(), String.valueOf(ACCOUNT_A), request.url().queryParameter("accountHash"));
			assertNull(request.header("X-Account-Hash"));
		}
		assertEquals(Collections.emptyList(), errors);
	}

	@Test
	public void publicReadsGoOutWithoutAnyTokenEvenWhenTheAccountIsLinked() {
		for (Runnable read : publicReads()) {
			rig.http.requests.clear();
			read.run();

			assertEquals(1, rig.http.requests.size());
			Request request = rig.http.last();
			assertNull(request.url().toString(), request.header("Authorization"));
			assertNull(request.url().queryParameter("accountHash"));
			assertEquals(Arrays.asList("Accept", "Content-Type", "User-Agent"), headerNames(request));
		}
		assertEquals(Collections.emptyList(), errors);
	}

	@Test
	public void noRequestEverUsesTheXAccountHashHeader() {
		accountScopedReads().values().forEach(read -> read.accept(ACCOUNT_A));
		authenticatedCalls().values().forEach(call -> call.accept(ACCOUNT_A));
		implicitAccountCalls().values().forEach(Runnable::run);
		publicReads().forEach(Runnable::run);

		assertFalse(rig.http.requests.isEmpty());
		for (Request request : rig.http.requests) {
			for (String name : headerNames(request)) assertFalse(name, name.equalsIgnoreCase("X-Account-Hash"));
		}
	}

	@Test
	public void aReadAboutSomeoneElseIsRefusedBeforeAnythingIsSent() {
		accountScopedReads().values().forEach(read -> read.accept(ACCOUNT_B));

		assertTrue("B is linked too, but A is the one logged in", rig.http.requests.isEmpty());
		assertEquals(accountScopedReads().size(), errors.size());
		for (Exception error : errors) {
			assertEquals(LinkException.Kind.ACCOUNT_MISMATCH, ((LinkException) error).getKind());
		}
		assertTrue(results.isEmpty());
	}

	@Test
	public void anAccountReadWithAnUnknownHashIsRefusedLocally() {
		accountScopedReads().values().forEach(read -> read.accept(-1));
		accountScopedReads().values().forEach(read -> read.accept(0));

		assertTrue(rig.http.requests.isEmpty());
		assertEquals(accountScopedReads().size() * 2, errors.size());
	}

	@Test
	public void anUnlinkedAccountCanReadPublicDataButNotItsOwnAccountData() {
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_C);

		publicReads().forEach(Runnable::run);
		assertEquals(publicReads().size(), rig.http.requests.size());
		assertTrue(errors.isEmpty());

		rig.http.requests.clear();
		accountScopedReads().values().forEach(read -> read.accept(ACCOUNT_C));
		assertTrue("nothing left the machine", rig.http.requests.isEmpty());
		assertEquals(accountScopedReads().size(), errors.size());
		for (Exception error : errors) {
			assertEquals(LinkException.Kind.NOT_LINKED, ((LinkException) error).getKind());
			assertTrue(error.getMessage().contains("Link code"));
		}
	}

	@Test
	public void aLoggedOutPluginCanStillReadPublicData() {
		rig.tokens.onLoggedOut();

		publicReads().forEach(Runnable::run);

		assertEquals(publicReads().size(), rig.http.requests.size());
		assertEquals(publicReads().size(), results.size());
		assertTrue(errors.isEmpty());
	}

	@Test
	public void thePublicApiNeverGetsATokenEvenWhenLinked() {
		api.fetchFeaturedRace(ok(), failed());
		api.fetchLeaguesBingoEvent("evt", ok(), failed());

		assertEquals(2, rig.http.requests.size());
		for (Request request : rig.http.requests) {
			assertNull(request.url().toString(), request.header("Authorization"));
			assertEquals(Arrays.asList("Accept", "Content-Type", "User-Agent"), headerNames(request));
		}
	}

	// ------------------------------------------------------------------ rejected and stale

	private static final String INVALID_CREDENTIAL =
		"{\"status\":\"error\",\"code\":\"invalid_plugin_credential\",\"message\":\"Link this installation again in your Reval account settings.\"}";

	@Test
	public void onlyAnInvalidPluginCredentialAnswerEndsTheLinkOnAnAccountRead() {
		rig.http.responder = request -> json(request, 401, INVALID_CREDENTIAL);

		api.fetchAccount(ACCOUNT_A, ok(), failed());

		assertEquals("no anonymous retry any more: the bearer is required for this read", 1, rig.http.requests.size());
		assertTrue(results.isEmpty());
		assertEquals(LinkException.Kind.EXPIRED, ((LinkException) errors.get(0)).getKind());
		assertFalse("forgotten, not kept to fail again", rig.hasCredential(ACCOUNT_A));
		assertTrue(rig.messages.contains(LinkException.Kind.EXPIRED.message()));
	}

	@Test
	public void aRevokedTokenOnAMutationIsReportedAndNeverRetried() {
		rig.http.responder = request -> json(request, 401, INVALID_CREDENTIAL);

		api.registerForEvent("evt", ACCOUNT_A, ok(), failed());

		assertEquals(1, rig.http.requests.size());
		assertTrue(results.isEmpty());
		assertEquals(LinkException.Kind.EXPIRED, ((LinkException) errors.get(0)).getKind());
		assertFalse(rig.hasCredential(ACCOUNT_A));

		api.registerForEvent("evt", ACCOUNT_A, ok(), failed());
		assertEquals("no second attempt with a dead token", 1, rig.http.requests.size());
		assertEquals(LinkException.Kind.NOT_LINKED, ((LinkException) errors.get(1)).getKind());
	}

	@Test
	public void anyOther401KeepsTheLinkAndSaysItMayBeTemporary() {
		String[] bodies = {
			"{\"status\":\"error\"}",
			"{\"status\":\"error\",\"code\":\"plugin_link_required\",\"message\":\"Link this installation.\"}",
			"{\"status\":\"error\",\"code\":\"invalid_event_envelope\"}",
			"{\"status\":\"error\",\"code\":\"INVALID_PLUGIN_CREDENTIAL\"}",
			"{\"status\":\"error\",\"code\":\"invalid_plugin_credential_x\"}",
			"{\"status\":\"error\",\"code\":[\"invalid_plugin_credential\"]}",
			"{\"status\":\"error\",\"message\":\"invalid_plugin_credential\"}",
			"<html>401 from a proxy</html>", "", "null", "[]", "not json at all",
		};
		for (String body : bodies) {
			errors.clear();
			rig.http.responder = request -> json(request, 401, body);

			api.registerForEvent("evt", ACCOUNT_A, ok(), failed());
			api.fetchAccount(ACCOUNT_A, ok(), failed());

			assertEquals(body, 2, errors.size());
			for (Exception error : errors) {
				assertEquals(body, LinkException.Kind.REFUSED, ((LinkException) error).getKind());
				assertTrue(error.getMessage().contains("try again"));
			}
			assertTrue("[" + body + "] must not end the link", rig.hasCredential(ACCOUNT_A));
			assertNotNull(rig.tokens.credentialFor(ACCOUNT_A));
		}
		assertFalse("and the player is not told the link is gone", rig.messages.contains(LinkException.Kind.EXPIRED.message()));
	}

	@Test
	public void theCredentialCodeOnAnyStatusButUnauthorizedDoesNotEndTheLink() {
		for (int status : new int[] {400, 403, 404, 409, 429, 500, 503}) {
			errors.clear();
			rig.http.responder = request -> json(request, status, INVALID_CREDENTIAL);

			api.registerForEvent("evt", ACCOUNT_A, ok(), failed());

			assertEquals(1, errors.size());
			assertFalse("status " + status, errors.get(0) instanceof LinkException);
			assertTrue("status " + status, rig.hasCredential(ACCOUNT_A));
		}
	}

	@Test
	public void theErrorBodyIsReadBoundedSoAHugeOneCannotCarryTheCode() {
		StringBuilder padding = new StringBuilder();
		for (int i = 0; i < 40_000; i++) padding.append('x');
		String huge = "{\"status\":\"error\",\"code\":\"invalid_plugin_credential\",\"pad\":\"" + padding + "\"}";
		rig.http.responder = request -> json(request, 401, huge);

		api.registerForEvent("evt", ACCOUNT_A, ok(), failed());

		assertEquals("cut off at 16 KiB, so it is not even valid JSON: nothing is concluded from it",
			LinkException.Kind.REFUSED, ((LinkException) errors.get(0)).getKind());
		assertTrue(rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void theTokenAndTheBodyAreNeverPutIntoAnErrorMessage() {
		rig.http.responder = request -> json(request, 401, "{\"code\":\"something_else\",\"message\":\"secret body text\"}");

		api.registerForEvent("evt", ACCOUNT_A, ok(), failed());
		api.registerForEvent("evt", ACCOUNT_A, ok(), failed());

		for (Exception error : errors) {
			assertFalse(error.getMessage().contains(TOKEN_A));
			assertFalse(error.getMessage().contains("secret body text"));
		}
		for (String message : rig.messages) assertFalse(message.contains(TOKEN_A));
	}

	@Test
	public void otherRefusalsKeepTheLinkAndSurfaceTheServersMessage() {
		rig.http.responder = request -> json(request, 403, "{\"status\":\"error\",\"code\":\"account_mismatch\",\"message\":\"Admins only\"}");

		api.adminLogin(ACCOUNT_A, "Nick", ok(), failed());

		assertEquals("Admins only", errors.get(0).getMessage());
		assertTrue(rig.hasCredential(ACCOUNT_A));
		assertNotNull(rig.tokens.credentialFor(ACCOUNT_A));
	}

	@Test
	public void errorMessagesFromTheServerAreOneShortLine() {
		StringBuilder huge = new StringBuilder("line one\n");
		for (int i = 0; i < 1000; i++) huge.append('y');
		rig.http.responder = request -> json(request, 500, "{\"status\":\"error\",\"message\":\"" + huge.toString().replace("\n", "\\n") + "\"}");

		api.fetchLeaderboard(ok(), failed());

		assertEquals(240, errors.get(0).getMessage().length());
		assertTrue(errors.get(0).getMessage().startsWith("line one yyy"));
		assertEquals("HTTP 500", failedMessage(500, "oops"));
	}

	private String failedMessage(int status, String body) {
		errors.clear();
		rig.http.responder = request -> json(request, status, body);
		api.fetchLeaderboard(ok(), failed());
		return errors.get(0).getMessage();
	}

	@Test
	public void aRevokedTokenOnAPublicReadIsNotOurBusinessBecauseNoTokenWasSent() {
		rig.http.responder = request -> json(request, 401, INVALID_CREDENTIAL);

		api.fetchLeaderboard(ok(), failed());

		assertTrue("an anonymous request cannot revoke anything", rig.hasCredential(ACCOUNT_A));
		assertFalse(errors.get(0) instanceof LinkException);
		assertEquals(1, rig.http.requests.size());
	}

	@Test
	public void anAnswerForAnAccountThatLoggedOutIsDropped() {
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			return json(request, 200, "{\"status\":\"success\"}");
		};

		List<Runnable> calls = Arrays.asList(
			() -> api.fetchNotifications(ACCOUNT_A, ok(), failed()),
			() -> api.registerForEvent("evt", ACCOUNT_A, ok(), failed()),
			() -> api.fetchAccount(ACCOUNT_A, ok(), failed()),
			() -> api.fetchLeaderboard(ok(), failed()),
			() -> api.fetchEvents(ok(), failed()),
			() -> api.fetchFeaturedRace(ok(), failed()));
		for (Runnable call : calls) {
			rig.tokens.onLoggedOut();
			rig.tokens.onAccount(ACCOUNT_A);
			call.run();
		}

		assertEquals("all requests really went out", calls.size(), rig.http.requests.size());
		assertTrue("no callback ran for the logged-out account, token or not", results.isEmpty());
		assertTrue(errors.isEmpty());
	}

	@Test
	public void theSameHoldsForAnUnlinkedAccountsPublicReadsWhichCarryNoTokenAtAll() {
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_C); // not linked
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			return json(request, 200, "{\"status\":\"success\"}");
		};

		api.fetchLeaderboard(ok(), failed());

		assertEquals(1, rig.http.requests.size());
		assertNull(rig.http.last().header("Authorization"));
		assertTrue("dropping does not depend on a grant", results.isEmpty());
		assertTrue(errors.isEmpty());
	}

	@Test
	public void aPublicFailureForAnEndedLoginIsDroppedToo() {
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			throw new IOException("connection reset");
		};

		api.fetchLeaderboard(ok(), failed());
		rig.tokens.onAccount(ACCOUNT_A);
		api.fetchFeaturedRace(ok(), failed());

		assertTrue(results.isEmpty());
		assertTrue(errors.isEmpty());
	}

	@Test
	public void aPublicAnswerForALoginThatWasSwitchedAwayFromIsDropped() {
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			rig.tokens.onAccount(ACCOUNT_B);
			return json(request, 200, "{\"status\":\"success\"}");
		};

		api.fetchLeaderboard(ok(), failed());

		assertTrue(results.isEmpty());
		assertTrue(errors.isEmpty());
	}

	@Test
	public void aPublicReadStartedBeforeAnyoneLoggedInIsStillDelivered() {
		rig.tokens.onLoggedOut();
		rig.http.responder = request -> {
			rig.tokens.onAccount(ACCOUNT_A); // somebody logs in while the request is out
			return json(request, 200, "{\"status\":\"success\"}");
		};

		api.fetchLeaderboard(ok(), failed());

		assertEquals("there was no earlier login for it to belong to", 1, results.size());
	}

	@Test
	public void aPublicAnswerStillArrivesWhileTheSameAccountStaysLoggedIn() {
		api.fetchLeaderboard(ok(), failed());
		rig.tokens.onAccount(ACCOUNT_A); // a world hop reports the same account again
		api.fetchFeaturedRace(ok(), failed());

		assertEquals(2, results.size());
	}

	@Test
	public void aPublicAnswerAfterShutdownIsDropped() {
		rig.http.responder = request -> {
			rig.tokens.shutDown();
			return json(request, 200, "{\"status\":\"success\"}");
		};

		api.fetchLeaderboard(ok(), failed());

		assertTrue(results.isEmpty());
	}

	@Test
	public void anAnswerForAnAccountThatWasSwitchedAwayFromIsDropped() {
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			rig.tokens.onAccount(ACCOUNT_B);
			return json(request, 200, "{\"status\":\"success\"}");
		};

		api.fetchNotifications(ACCOUNT_A, ok(), failed());

		assertTrue(results.isEmpty());
		assertTrue(errors.isEmpty());
	}

	@Test
	public void aFailureForAnAccountThatLoggedOutIsDroppedToo() {
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			throw new IOException("connection reset");
		};

		api.fetchNotifications(ACCOUNT_A, ok(), failed());

		assertTrue(results.isEmpty());
		assertTrue(errors.isEmpty());
	}

	@Test
	public void aRejectionForALoggedOutAccountDoesNotTouchTheNextOnesState() {
		rig.http.responder = request -> {
			rig.tokens.onLoggedOut();
			rig.tokens.onAccount(ACCOUNT_B);
			return json(request, 401, INVALID_CREDENTIAL);
		};

		api.fetchNotifications(ACCOUNT_A, ok(), failed());

		assertTrue(errors.isEmpty());
		assertTrue("B's link is untouched", rig.hasCredential(ACCOUNT_B));
		assertFalse("and B was not told anything about A's token", rig.messages.contains(LinkException.Kind.EXPIRED.message()));
	}

	// ------------------------------------------------------------------ redirects, origin, legacy

	@Test
	public void credentialedRequestsUseAClientThatNeverFollowsRedirectsOrCaches() {
		assertFalse(api.credentialedClient().followRedirects());
		assertFalse(api.credentialedClient().followSslRedirects());
		assertNull(api.credentialedClient().cache());
		assertSame("built once", api.credentialedClient(), api.credentialedClient());
		assertEquals("a hung call ends", 30_000, api.credentialedClient().callTimeoutMillis());
	}

	@Test
	public void aRedirectAnswerIsAnErrorAndIsNotChased() {
		rig.http.responder = request -> json(request, 302, "").newBuilder().header("Location", "https://evil.example/").build();

		api.registerForEvent("evt", ACCOUNT_A, ok(), failed());

		assertEquals(1, rig.http.requests.size());
		assertTrue(errors.get(0).getMessage().contains("redirect"));
		assertTrue(rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void theOldNumericCodeOverloadsNeverTransmitTheCodeOrUseItAsACredential() throws Exception {
		String legacyCode = "246810";

		api.adminLogin(legacyCode, ACCOUNT_A, "Nick", ok(), failed());
		api.fetchPendingRankChanges(legacyCode, ok(), failed());
		api.actualizeRankChange(legacyCode, 7, ok(), failed());

		assertEquals(3, rig.http.requests.size());
		for (Request request : rig.http.requests) {
			assertFalse(everything(request).contains(legacyCode));
			assertNull(request.header("X-Member-Code"));
			assertEquals("Bearer " + TOKEN_A, request.header("Authorization"));
		}
	}

	@Test
	public void theOldNumericCodeOverloadsAreRefusedForAnUnlinkedAccount() {
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_C);

		api.adminLogin("246810", ACCOUNT_C, "Nick", ok(), failed());
		api.fetchPendingRankChanges("246810", ok(), failed());
		api.actualizeRankChange("246810", 7, ok(), failed());

		assertTrue("a code is no substitute for a link", rig.http.requests.isEmpty());
		assertEquals(3, errors.size());
	}
}

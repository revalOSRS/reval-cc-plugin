package com.revalclan.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.auth.ApiOrigin;
import com.revalclan.auth.LinkException;
import okhttp3.MultipartBody;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebhookServiceTest {
	private static final String HOOK_URL = "https://api.revalosrs.ee/reval-webhook";
	private static final byte[] SCREENSHOT = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};

	private Rig rig;
	private WebhookService hook;
	private final List<Runnable> scheduled = new ArrayList<>();
	private final List<JsonObject> responses = new ArrayList<>();

	@Before
	public void setUp() {
		rig = new Rig();
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.link(ACCOUNT_B, TOKEN_B);
		rig.tokens.onAccount(ACCOUNT_A);

		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
			scheduled.add(call.getArgument(0));
			return null;
		});
		hook = new WebhookService(rig.client, rig.gson, rig.tokens, executor, ApiOrigin.PRODUCTION, HOOK_URL);
	}

	@After
	public void tearDown() throws Exception {
		rig.close();
	}

	private static Map<String, Object> event(Object accountHash) {
		Map<String, Object> data = new HashMap<>();
		data.put("eventType", "LOOT");
		data.put("eventTimestamp", 1_792_000_000_000L);
		data.put("accountHash", accountHash);
		data.put("username", "Zezima");
		data.put("items", Arrays.asList("Twisted bow", "Dragon claws"));
		return data;
	}

	private boolean send(long accountHash) {
		return hook.sendDataAsync(event(accountHash), responses::add);
	}

	private static byte[] jsonPart(MultipartBody body, String name) throws IOException {
		for (MultipartBody.Part part : body.parts()) {
			if (part.headers().get("Content-Disposition").contains("name=\"" + name + "\"")) return bytes(part.body());
		}
		throw new AssertionError("no part " + name);
	}

	// ------------------------------------------------------------------ the two send paths

	@Test
	public void gzipJsonSendCarriesBearerEventIdAndSentAt() throws Exception {
		assertTrue(send(ACCOUNT_A));

		assertEquals(1, rig.http.requests.size());
		Request request = rig.http.last();
		assertEquals(HOOK_URL, request.url().toString());
		assertEquals("POST", request.method());
		assertEquals("Bearer " + TOKEN_A, request.header("Authorization"));
		assertTrue(request.header(WebhookService.EVENT_ID_HEADER), request.header(WebhookService.EVENT_ID_HEADER).matches(UUID_V4));
		assertEquals(String.valueOf(NOW.toEpochMilli()), request.header(WebhookService.SENT_AT_HEADER));
		assertEquals("gzip", request.header("Content-Encoding"));
		assertTrue(request.header("User-Agent").startsWith("RuneLite-RevalClan-Plugin/"));
		assertEquals("X-Reval-Event-Id", WebhookService.EVENT_ID_HEADER);
		assertEquals("X-Reval-Sent-At", WebhookService.SENT_AT_HEADER);
		assertEquals(Arrays.asList("Authorization", "Content-Encoding", "Content-Type", "User-Agent",
			"X-Reval-Event-Id", "X-Reval-Sent-At"), headerNames(request));

		String json = gunzip(bytes(request.body()));
		assertEquals(rig.gson.toJsonTree(event(ACCOUNT_A)), new Gson().fromJson(json, JsonObject.class));
		assertFalse("no token in the payload", json.contains(TOKEN_A));
		assertFalse(withoutAuthorization(request).contains(TOKEN_A));
	}

	@Test
	public void multipartScreenshotSendCarriesTheSameEnvelope() throws Exception {
		assertTrue(hook.sendDataAsync(event(ACCOUNT_A), SCREENSHOT, responses::add));

		Request request = rig.http.last();
		assertEquals(HOOK_URL, request.url().toString());
		assertEquals("Bearer " + TOKEN_A, request.header("Authorization"));
		assertTrue(request.header(WebhookService.EVENT_ID_HEADER).matches(UUID_V4));
		assertEquals(String.valueOf(NOW.toEpochMilli()), request.header(WebhookService.SENT_AT_HEADER));
		assertNull("the multipart body is not gzipped as a whole", request.header("Content-Encoding"));
		assertEquals(Arrays.asList("Authorization", "User-Agent", "X-Reval-Event-Id", "X-Reval-Sent-At"), headerNames(request));

		assertTrue(request.body() instanceof MultipartBody);
		MultipartBody body = (MultipartBody) request.body();
		assertEquals("multipart/form-data", body.type().type() + "/" + body.type().subtype());
		assertEquals(2, body.parts().size());
		assertTrue(gunzip(jsonPart(body, "payload")).contains("\"eventType\":\"LOOT\""));
		assertArrayEquals(SCREENSHOT, jsonPart(body, "screenshot"));
		assertFalse(withoutAuthorization(request).contains(TOKEN_A));
	}

	@Test
	public void bothPathsCarryTheEventIdAndTimestampForTheSameEventShape() throws Exception {
		hook.sendDataAsync(event(ACCOUNT_A), null, null);
		hook.sendDataAsync(event(ACCOUNT_A), SCREENSHOT, null);

		for (Request request : rig.http.requests) {
			assertNotNull(request.header(WebhookService.EVENT_ID_HEADER));
			assertNotNull(request.header(WebhookService.SENT_AT_HEADER));
			assertNotNull(request.header("Authorization"));
		}
		assertNotEquals(rig.http.requests.get(0).header(WebhookService.EVENT_ID_HEADER),
			rig.http.requests.get(1).header(WebhookService.EVENT_ID_HEADER));
	}

	@Test
	public void everyLogicalEventGetsItsOwnRandomVersion4Id() {
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			send(ACCOUNT_A);
			String id = rig.http.last().header(WebhookService.EVENT_ID_HEADER);
			assertTrue(id, id.matches(UUID_V4));
			ids.add(id);
		}
		assertEquals(200, ids.size());
	}

	@Test
	public void noIdentityHeadersBesidesTheBearer() throws Exception {
		send(ACCOUNT_A);
		hook.sendDataAsync(event(ACCOUNT_A), SCREENSHOT, null);
		for (Request request : rig.http.requests) {
			for (String name : headerNames(request)) {
				assertFalse(name, name.equalsIgnoreCase("X-Member-Code") || name.equalsIgnoreCase("X-Account-Hash")
					|| name.equalsIgnoreCase("Cookie"));
			}
		}
	}

	// ------------------------------------------------------------------ which token

	@Test
	public void aBufferedEventUsesTheTokenOfTheAccountInItsPayloadNotTheLoggedInOne() {
		assertTrue(send(ACCOUNT_B));
		assertEquals("Bearer " + TOKEN_B, rig.http.last().header("Authorization"));
		assertTrue(send(ACCOUNT_A));
		assertEquals("Bearer " + TOKEN_A, rig.http.last().header("Authorization"));

		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_B);
		assertTrue("A's session summary replayed while B is logged in", send(ACCOUNT_A));
		assertEquals("Bearer " + TOKEN_A, rig.http.last().header("Authorization"));

		rig.tokens.onLoggedOut();
		assertTrue("even with nobody logged in", send(ACCOUNT_B));
		assertEquals("Bearer " + TOKEN_B, rig.http.last().header("Authorization"));
	}

	@Test
	public void theAccountHashMayBeANumberOrAString() {
		assertTrue(hook.sendDataAsync(event(ACCOUNT_A), null));
		assertTrue(hook.sendDataAsync(event(String.valueOf(ACCOUNT_B)), null));
		assertTrue(hook.sendDataAsync(event((Object) Long.valueOf(ACCOUNT_A)), null));
		assertEquals("Bearer " + TOKEN_A, rig.http.requests.get(0).header("Authorization"));
		assertEquals("Bearer " + TOKEN_B, rig.http.requests.get(1).header("Authorization"));
		assertEquals("Bearer " + TOKEN_A, rig.http.requests.get(2).header("Authorization"));
	}

	@Test
	public void anEventForAnAccountThatIsNotLinkedIsNotSentWithSomeoneElsesToken() {
		assertFalse(send(ACCOUNT_C));

		assertTrue("A is logged in and linked, but the event is C's", rig.http.requests.isEmpty());
		assertTrue(responses.isEmpty());
	}

	@Test
	public void eventsWithoutAUsableAccountHashAreNeverSent() {
		Map<String, Object> missing = event(ACCOUNT_A);
		missing.remove("accountHash");
		assertFalse(hook.sendDataAsync(missing, null));
		assertFalse(hook.sendDataAsync(event(-1L), null));
		assertFalse(hook.sendDataAsync(event(0L), null));
		assertFalse(hook.sendDataAsync(event("not-a-number"), null));
		assertFalse(hook.sendDataAsync(event(null), null));
		assertFalse(hook.sendDataAsync(event(new Object()), null));
		assertTrue(rig.http.requests.isEmpty());
	}

	@Test
	public void anUnlinkedLoggedInPlayerGetsTheLinkingInstructionOnceAndNoTelemetryLeaves() {
		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_C);

		assertFalse(send(ACCOUNT_C));
		assertFalse(send(ACCOUNT_C));
		assertFalse(hook.sendDataAsync(event(ACCOUNT_C), SCREENSHOT, null));

		assertTrue(rig.http.requests.isEmpty());
		assertEquals(Arrays.asList(com.revalclan.auth.LinkTokenManager.LINK_INSTRUCTION), rig.messages);
	}

	@Test
	public void anUnlinkedEventForSomeoneWhoIsNotPlayingNowStaysSilent() {
		assertFalse(send(ACCOUNT_C));
		assertTrue("A is the one logged in; C hears nothing", rig.messages.isEmpty());
	}

	@Test
	public void sentAtFollowsTheServersClockWhenTheLocalOneIsFarOff() {
		java.time.Instant serverTime = NOW.plus(Duration.ofMinutes(12));
		rig.http.responder = request -> withDate(json(request, 200, "{}"), serverTime);

		send(ACCOUNT_A);
		send(ACCOUNT_A);

		assertEquals("the first event had nothing to go on", String.valueOf(NOW.toEpochMilli()),
			rig.http.requests.get(0).header(WebhookService.SENT_AT_HEADER));
		assertEquals("the next is stamped in server time, well inside the 5 minute window",
			String.valueOf(serverTime.toEpochMilli()), rig.http.requests.get(1).header(WebhookService.SENT_AT_HEADER));
	}

	@Test
	public void aRejectionAlsoTeachesTheClockSoTheRetryIsStampedCorrectly() {
		java.time.Instant serverTime = NOW.minus(Duration.ofMinutes(20));
		rig.http.responder = request -> withDate(json(request, 503, "{}"), serverTime);

		send(ACCOUNT_A);
		scheduled.get(0).run();

		assertEquals(String.valueOf(serverTime.toEpochMilli()), rig.http.requests.get(1).header(WebhookService.SENT_AT_HEADER));
		assertEquals(rig.http.requests.get(0).header(WebhookService.EVENT_ID_HEADER),
			rig.http.requests.get(1).header(WebhookService.EVENT_ID_HEADER));
	}

	// ------------------------------------------------------------------ retries

	@Test
	public void aRetryKeepsTheEventIdAndBodyAndRefreshesTheTimestamp() throws Exception {
		rig.http.responder = failFirst();

		assertTrue(send(ACCOUNT_A));
		assertEquals(1, rig.http.requests.size());
		assertEquals(1, scheduled.size());

		rig.clock.advance(Duration.ofSeconds(3));
		scheduled.get(0).run();

		assertEquals(2, rig.http.requests.size());
		Request first = rig.http.requests.get(0);
		Request second = rig.http.requests.get(1);
		assertEquals(first.header(WebhookService.EVENT_ID_HEADER), second.header(WebhookService.EVENT_ID_HEADER));
		assertEquals(Long.parseLong(first.header(WebhookService.SENT_AT_HEADER)) + 3000,
			Long.parseLong(second.header(WebhookService.SENT_AT_HEADER)));
		assertArrayEquals(bytes(first.body()), bytes(second.body()));
		assertEquals("Bearer " + TOKEN_A, second.header("Authorization"));
		assertEquals("a successful retry schedules nothing more", 1, scheduled.size());
	}

	@Test
	public void aMultipartRetryResendsTheSameScreenshotUnderTheSameId() throws Exception {
		rig.http.responder = failFirst();

		hook.sendDataAsync(event(ACCOUNT_A), SCREENSHOT, null);
		scheduled.get(0).run();

		assertEquals(2, rig.http.requests.size());
		Request second = rig.http.requests.get(1);
		assertEquals(rig.http.requests.get(0).header(WebhookService.EVENT_ID_HEADER), second.header(WebhookService.EVENT_ID_HEADER));
		assertArrayEquals(SCREENSHOT, jsonPart((MultipartBody) second.body(), "screenshot"));
	}

	@Test
	public void transientStatusesAreRetriedOnceAndOthersAreNot() {
		for (int status : new int[] {429, 502, 503, 504}) {
			rig.http.requests.clear();
			scheduled.clear();
			rig.http.responder = request -> json(request, status, "{}");
			send(ACCOUNT_A);
			assertEquals("status " + status, 1, scheduled.size());
		}
		for (int status : new int[] {400, 403, 404, 500}) {
			rig.http.requests.clear();
			scheduled.clear();
			rig.http.responder = request -> json(request, status, "{}");
			send(ACCOUNT_A);
			assertTrue("status " + status, scheduled.isEmpty());
		}
	}

	@Test
	public void retriesAreBounded() {
		rig.http.responder = request -> { throw new IOException("down"); };

		send(ACCOUNT_A);
		scheduled.get(0).run();

		assertEquals(2, rig.http.requests.size());
		assertEquals("no endless retrying", 1, scheduled.size());
	}

	@Test
	public void aRetryNeverRevivesALinkThatWasRevokedInTheMeantime() {
		rig.http.responder = failFirst();
		send(ACCOUNT_A);
		rig.tokens.rejected(rig.tokens.grantForEvent(okhttp3.HttpUrl.get(HOOK_URL), ACCOUNT_A));

		scheduled.get(0).run();

		assertEquals("dropped instead of sent unauthenticated", 1, rig.http.requests.size());
	}

	@Test
	public void aRetryStillUsesTheEventsAccountAfterTheLoginChanged() {
		rig.http.responder = failFirst();
		send(ACCOUNT_A);

		rig.tokens.onLoggedOut();
		rig.tokens.onAccount(ACCOUNT_B);
		scheduled.get(0).run();

		assertEquals("Bearer " + TOKEN_A, rig.http.requests.get(1).header("Authorization"));
	}

	// ------------------------------------------------------------------ answers

	private static final String INVALID_CREDENTIAL =
		"{\"status\":\"error\",\"code\":\"invalid_plugin_credential\",\"message\":\"Link this installation in your Reval account settings.\"}";
	private static final String INVALID_ENVELOPE =
		"{\"status\":\"error\",\"code\":\"invalid_event_envelope\",\"message\":\"The event identifier or send time is invalid. Refresh the connection and retry.\"}";

	@Test
	public void anInvalidPluginCredentialAnswerForgetsTheLinkAndStopsFurtherSends() {
		rig.http.responder = request -> json(request, 401, INVALID_CREDENTIAL);

		send(ACCOUNT_A);

		assertTrue("not retried", scheduled.isEmpty());
		assertTrue(responses.isEmpty());
		assertFalse(rig.hasCredential(ACCOUNT_A));
		assertFalse("and nothing unauthenticated goes out afterwards", send(ACCOUNT_A));
		assertEquals(1, rig.http.requests.size());
		assertTrue(rig.messages.contains(LinkException.Kind.EXPIRED.message()));
	}

	@Test
	public void anyOther401KeepsTheLinkAndTheNextEventIsStillSent() {
		String[] bodies = {"{\"error\":\"bad token\"}", "{}", "", "<html>proxy</html>",
			"{\"code\":\"plugin_link_required\"}", "{\"code\":\"invalid_plugin_credential_x\"}"};
		for (String body : bodies) {
			rig.http.requests.clear();
			rig.http.responder = request -> json(request, 401, body);

			assertTrue(send(ACCOUNT_A));

			assertTrue("[" + body + "]", scheduled.isEmpty());
			assertTrue("[" + body + "] must not end the link", rig.hasCredential(ACCOUNT_A));
			assertTrue("still sendable", send(ACCOUNT_A));
			assertEquals(2, rig.http.requests.size());
			assertEquals("Bearer " + TOKEN_A, rig.http.last().header("Authorization"));
		}
		assertFalse(rig.messages.contains(LinkException.Kind.EXPIRED.message()));
		assertTrue("the player hears once per login that Reval did not accept it", rig.messages.contains(LinkException.Kind.REFUSED.message()));
		assertEquals(1, rig.messages.stream().filter(LinkException.Kind.REFUSED.message()::equals).count());
	}

	@Test
	public void theInvalidCredentialCodeOnAnotherStatusDoesNotEndTheLink() {
		for (int status : new int[] {400, 403, 409, 500}) {
			rig.http.responder = request -> json(request, status, INVALID_CREDENTIAL);
			send(ACCOUNT_A);
			assertTrue("status " + status, rig.hasCredential(ACCOUNT_A));
		}
	}

	@Test
	public void anEnvelopeRefusalKeepsTheLinkAndRetriesOnceWithTheSameIdAndAFreshTimestamp() throws Exception {
		java.time.Instant serverTime = NOW.plus(Duration.ofMinutes(20));
		int[] attempts = {0};
		rig.http.responder = request -> {
			if (attempts[0]++ == 0) return withDate(json(request, 400, INVALID_ENVELOPE), serverTime);
			return json(request, 200, "{\"ok\":true}");
		};

		assertTrue(send(ACCOUNT_A));
		assertEquals("one retry is scheduled, not an immediate resend", 1, scheduled.size());
		scheduled.get(0).run();

		assertEquals(2, rig.http.requests.size());
		Request first = rig.http.requests.get(0);
		Request second = rig.http.requests.get(1);
		assertEquals(first.header(WebhookService.EVENT_ID_HEADER), second.header(WebhookService.EVENT_ID_HEADER));
		assertEquals("first used the raw clock", String.valueOf(NOW.toEpochMilli()), first.header(WebhookService.SENT_AT_HEADER));
		assertEquals("the retry is stamped from the Date the refusal carried",
			String.valueOf(serverTime.toEpochMilli()), second.header(WebhookService.SENT_AT_HEADER));
		assertArrayEquals(bytes(first.body()), bytes(second.body()));
		assertEquals("Bearer " + TOKEN_A, second.header("Authorization"));
		assertTrue("the link was never in question", rig.hasCredential(ACCOUNT_A));
		assertFalse(rig.messages.contains(LinkException.Kind.EXPIRED.message()));
		assertEquals(1, scheduled.size());
	}

	@Test
	public void anOlderBackendsUnauthorizedEnvelopeRefusalIsTreatedTheSameWay() {
		int[] attempts = {0};
		rig.http.responder = request -> attempts[0]++ == 0 ? json(request, 401, INVALID_ENVELOPE) : json(request, 200, "{}");

		send(ACCOUNT_A);
		scheduled.get(0).run();

		assertEquals(2, rig.http.requests.size());
		assertTrue("a 401 carrying invalid_event_envelope must not delete the credential", rig.hasCredential(ACCOUNT_A));
		assertFalse(rig.messages.contains(LinkException.Kind.EXPIRED.message()));
	}

	@Test
	public void anEnvelopeRefusalIsRetriedOnlyOnce() {
		rig.http.responder = request -> json(request, 400, INVALID_ENVELOPE);

		send(ACCOUNT_A);
		scheduled.get(0).run();

		assertEquals(2, rig.http.requests.size());
		assertEquals("no third attempt", 1, scheduled.size());
		assertTrue(rig.hasCredential(ACCOUNT_A));
		assertTrue("and no clock blame when the clock is fine", rig.messages.isEmpty());
	}

	@Test
	public void anEnvelopeRefusalCannotBeAttributedToAnAbsurdlyWrongClockMeansTheClockIsNamed() {
		rig.http.responder = request -> withDate(json(request, 400, INVALID_ENVELOPE), NOW.plus(Duration.ofDays(3000)));

		send(ACCOUNT_A);
		scheduled.get(0).run();

		assertEquals("a difference this big is not used to restamp events", String.valueOf(NOW.toEpochMilli()),
			rig.http.requests.get(1).header(WebhookService.SENT_AT_HEADER));
		assertEquals(java.util.Collections.singletonList(LinkException.Kind.CLOCK_OFF.message()), rig.messages);
		assertTrue(rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void aRefusalSharesTheOneRetryWithTransientErrors() {
		int[] attempts = {0};
		rig.http.responder = request -> attempts[0]++ == 0 ? json(request, 503, "{}") : json(request, 400, INVALID_ENVELOPE);

		send(ACCOUNT_A);
		scheduled.get(0).run();

		assertEquals(2, rig.http.requests.size());
		assertEquals("still at most one retry in total", 1, scheduled.size());
	}

	@Test
	public void anEnvelopeCodeOnAnotherStatusIsJustAnError() {
		for (int status : new int[] {403, 409, 500}) {
			scheduled.clear();
			rig.http.responder = request -> json(request, status, INVALID_ENVELOPE);
			send(ACCOUNT_A);
			assertTrue("status " + status, scheduled.isEmpty());
			assertTrue(rig.hasCredential(ACCOUNT_A));
		}
	}

	@Test
	public void theRefusalBodyIsNeverLoggedOrShownBeyondTheFixedMessages() {
		rig.http.responder = request -> json(request, 401, "{\"code\":\"odd\",\"message\":\"secret server text\"}");
		send(ACCOUNT_A);
		for (String message : rig.messages) {
			assertFalse(message.contains("secret server text"));
			assertFalse(message.contains(TOKEN_A));
		}
	}

	@Test
	public void theWebhookClientHasAFiniteLongerCallTimeout() {
		assertEquals(60_000, hook.client().callTimeoutMillis());
	}

	@Test
	public void theResponseConsumerOnlySeesParsedSuccessBodies() {
		rig.http.responder = request -> json(request, 200, "{\"sessionStored\":\"stored\"}");
		send(ACCOUNT_A);
		assertEquals(1, responses.size());
		assertEquals("stored", responses.get(0).get("sessionStored").getAsString());

		rig.http.responder = request -> json(request, 500, "{\"sessionStored\":\"stored\"}");
		send(ACCOUNT_A);
		rig.http.responder = request -> json(request, 200, "not json");
		send(ACCOUNT_A);
		rig.http.responder = request -> json(request, 200, "null");
		send(ACCOUNT_A);
		assertEquals(1, responses.size());
	}

	@Test
	public void aFailingResponseConsumerCannotBreakTheService() {
		hook.sendDataAsync(event(ACCOUNT_A), body -> { throw new IllegalStateException("boom"); });
		assertTrue(send(ACCOUNT_A));
	}

	@Test
	public void theClientNeverFollowsRedirectsOrCaches() {
		assertFalse(hook.client().followRedirects());
		assertFalse(hook.client().followSslRedirects());
		assertNull(hook.client().cache());
	}

	@Test
	public void aWebhookUrlOutsideTheTrustedOriginCanNeverBeSent() {
		WebhookService misconfigured = new WebhookService(rig.client, rig.gson, rig.tokens, mock(ScheduledExecutorService.class),
			ApiOrigin.PRODUCTION, "https://evil.example/reval-webhook");

		assertFalse(misconfigured.sendDataAsync(event(ACCOUNT_A), null));
		assertTrue(rig.http.requests.isEmpty());
	}

	// ------------------------------------------------------------------ tracked sends: how an event ends

	private final List<String> ended = new ArrayList<>();

	private boolean sendTracked(long accountHash) {
		return hook.sendDataTracked(event(accountHash), body -> ended.add("answered"), () -> ended.add("not delivered"));
	}

	@Test
	public void aTrackedSendAnsweredWithJsonReportsTheAnswerOnceAndNeverTheFailure() {
		rig.http.responder = request -> json(request, 200, "{\"sessionStored\":\"stored\"}");

		assertTrue(sendTracked(ACCOUNT_A));

		assertEquals(Arrays.asList("answered"), ended);
	}

	@Test
	public void aTrackedSendThatFailedFirstAndSucceededOnItsRetryOnlyReportsTheSuccess() {
		rig.http.responder = failFirst();

		assertTrue(sendTracked(ACCOUNT_A));
		assertEquals("the first failure is not the end: a retry is pending", Collections.emptyList(), ended);
		scheduled.remove(0).run();

		assertEquals(Arrays.asList("answered"), ended);
	}

	@Test
	public void everyWayAnEventCanEndWithoutAnAnswerIsReportedExactlyOnceAfterTheLastAttempt() {
		Map<String, Responder> endings = new java.util.LinkedHashMap<>();
		endings.put("network", request -> { throw new IOException("down"); });
		endings.put("503", request -> json(request, 503, "{}"));
		endings.put("429", request -> json(request, 429, "{}"));
		endings.put("500", request -> json(request, 500, "{}"));
		endings.put("403", request -> json(request, 403, "{}"));
		endings.put("302", request -> json(request, 302, ""));
		endings.put("envelope", request -> json(request, 400, INVALID_ENVELOPE));
		endings.put("legacy envelope", request -> json(request, 401, INVALID_ENVELOPE));
		endings.put("unknown 401", request -> json(request, 401, "{}"));
		endings.put("not json", request -> json(request, 200, "<html>proxy</html>"));
		endings.put("empty", request -> json(request, 200, ""));

		for (Map.Entry<String, Responder> ending : endings.entrySet()) {
			ended.clear();
			scheduled.clear();
			rig.http.responder = ending.getValue();

			assertTrue(ending.getKey(), sendTracked(ACCOUNT_A));
			while (!scheduled.isEmpty()) {
				assertEquals(ending.getKey() + ": no verdict while a retry is pending", Collections.emptyList(), ended);
				scheduled.remove(0).run();
			}

			assertEquals(ending.getKey(), Arrays.asList("not delivered"), ended);
			assertTrue(ending.getKey() + " keeps the link", rig.hasCredential(ACCOUNT_A));
		}
	}

	@Test
	public void aRejectedLinkEndsTheTrackedSendAtOnceWithoutRetry() {
		rig.http.responder = request -> json(request, 401, INVALID_CREDENTIAL);

		assertTrue(sendTracked(ACCOUNT_A));

		assertEquals(Arrays.asList("not delivered"), ended);
		assertTrue(scheduled.isEmpty());
		assertFalse(rig.hasCredential(ACCOUNT_A));
	}

	@Test
	public void aLinkLostBeforeTheRetryEndsTheTrackedSend() {
		rig.http.responder = failFirst();
		sendTracked(ACCOUNT_A);
		rig.tokens.rejected(rig.tokens.grantForEvent(okhttp3.HttpUrl.get(HOOK_URL), ACCOUNT_A));

		scheduled.remove(0).run();

		assertEquals(Arrays.asList("not delivered"), ended);
		assertEquals("nothing unauthenticated went out", 1, rig.http.requests.size());
	}

	@Test
	public void aRetryThatCannotBeScheduledEndsTheTrackedSend() {
		ScheduledExecutorService refusing = mock(ScheduledExecutorService.class);
		when(refusing.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenThrow(new java.util.concurrent.RejectedExecutionException());
		WebhookService noRetry = new WebhookService(rig.client, rig.gson, rig.tokens, refusing, ApiOrigin.PRODUCTION, HOOK_URL);
		rig.http.responder = request -> json(request, 503, "{}");

		assertTrue(noRetry.sendDataTracked(event(ACCOUNT_A), body -> ended.add("answered"), () -> ended.add("not delivered")));

		assertEquals(Arrays.asList("not delivered"), ended);
	}

	@Test
	public void aRetryThatBlowsUpEndsTheTrackedSendInsteadOfLeavingItPendingForever() {
		com.revalclan.auth.LinkTokenManager failing = org.mockito.Mockito.spy(rig.tokens);
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
			scheduled.add(call.getArgument(0));
			return null;
		});
		WebhookService service = new WebhookService(rig.client, rig.gson, failing, executor, ApiOrigin.PRODUCTION, HOOK_URL);
		rig.http.responder = request -> json(request, 503, "{}");
		assertTrue(service.sendDataTracked(event(ACCOUNT_A), body -> ended.add("answered"), () -> ended.add("not delivered")));
		assertEquals(Collections.emptyList(), ended);

		org.mockito.Mockito.doThrow(new IllegalStateException("unexpected")).when(failing).grantForEvent(any(), anyLong());
		scheduled.remove(0).run();

		assertEquals(Arrays.asList("not delivered"), ended);
	}

	@Test
	public void aTrackedSendForAnUnlinkedAccountReportsNothingBecauseNothingWasSent() {
		assertFalse(sendTracked(ACCOUNT_C));

		assertEquals(Collections.emptyList(), ended);
		assertTrue(rig.http.requests.isEmpty());
	}

	@Test
	public void aFailingCompletionHandlerCannotBreakTheService() {
		rig.http.responder = request -> json(request, 500, "{}");

		assertTrue(hook.sendDataTracked(event(ACCOUNT_A), body -> { }, () -> { throw new IllegalStateException("boom"); }));
		assertTrue(send(ACCOUNT_A));
	}

	@Test
	public void aResponseHandlerThatThrowsIsNotAlsoReportedAsAFailure() {
		rig.http.responder = request -> json(request, 200, "{\"sessionStored\":\"stored\"}");

		assertTrue(hook.sendDataTracked(event(ACCOUNT_A), body -> { throw new IllegalStateException("boom"); }, () -> ended.add("not delivered")));

		assertEquals("the answer did arrive; what the caller did with it is its own business", Collections.emptyList(), ended);
	}

	@Test
	public void aTrackedSendNeedsBothCallbacks() {
		try {
			hook.sendDataTracked(event(ACCOUNT_A), body -> { }, null);
			fail("an event nobody can learn the end of is not a tracked event");
		} catch (NullPointerException expected) {
			assertTrue(rig.http.requests.isEmpty());
		}
	}

	// ------------------------------------------------------------------ helpers

	/** First attempt fails like a dropped connection, later ones succeed. */
	private Responder failFirst() {
		int[] attempts = {0};
		return request -> {
			if (attempts[0]++ == 0) throw new IOException("connection reset");
			return json(request, 200, "{\"ok\":true}");
		};
	}

	private static String withoutAuthorization(Request request) throws IOException {
		return everything(request).replace("Authorization: Bearer " + TOKEN_A, "").replace("Authorization: Bearer " + TOKEN_B, "");
	}
}

package com.revalclan.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;

/** The pairing response is the root of trust for everything stored later, so it is validated strictly. */
public class LinkCredentialTest {
	private static final Instant LATER = NOW.plus(Duration.ofDays(90));

	private static JsonObject response() {
		return pairResponse(ACCOUNT_A, TOKEN_A);
	}

	private static JsonObject data(JsonObject response) {
		return response.getAsJsonObject("data");
	}

	private static void assertRejected(String why, JsonObject body) {
		try {
			LinkCredential.fromPairResponse(body, ACCOUNT_A);
			fail("accepted: " + why);
		} catch (LinkException expected) {
			assertEquals(why, LinkException.Kind.INVALID_RESPONSE, expected.getKind());
		}
	}

	private static JsonObject parse(String json) {
		return new Gson().fromJson(json, JsonObject.class);
	}

	@Test
	public void acceptsTheContractResponse() throws Exception {
		LinkCredential credential = LinkCredential.fromPairResponse(response(), ACCOUNT_A);
		assertEquals(TOKEN_A, credential.getToken());
		assertEquals("3f2b8c1e-5d4a-4e6b-9a7c-1d2e3f4a5b6c", credential.getCredentialId());
		assertEquals(42, credential.getAccountId());
		assertEquals(ACCOUNT_A, credential.getAccountHash());
		assertEquals(LATER, credential.getExpiresAt());
		assertFalse(credential.isExpired(NOW));
		assertTrue(credential.isExpired(LATER));
	}

	@Test
	public void acceptsJavascriptTimestampsAndOffsets() throws Exception {
		JsonObject body = response();
		data(body).addProperty("expiresAt", "2027-01-24T12:00:00.000Z");
		assertEquals(Instant.parse("2027-01-24T12:00:00Z"),
			LinkCredential.fromPairResponse(body, ACCOUNT_A).getExpiresAt());
		data(body).addProperty("expiresAt", "2027-01-24T14:00:00+02:00");
		assertEquals(Instant.parse("2027-01-24T12:00:00Z"),
			LinkCredential.fromPairResponse(body, ACCOUNT_A).getExpiresAt());
	}

	@Test
	public void ignoresUnknownExtraFields() throws Exception {
		JsonObject body = response();
		body.addProperty("message", "ok");
		data(body).addProperty("futureField", true);
		assertNotNull(LinkCredential.fromPairResponse(body, ACCOUNT_A));
	}

	@Test
	public void rejectsAnythingButAnExactSuccessEnvelope() {
		JsonObject failed = response();
		failed.addProperty("status", "error");
		assertRejected("status error", failed);

		JsonObject noStatus = response();
		noStatus.remove("status");
		assertRejected("no status", noStatus);

		JsonObject upper = response();
		upper.addProperty("status", "SUCCESS");
		assertRejected("status case", upper);

		JsonObject noData = response();
		noData.remove("data");
		assertRejected("no data", noData);

		JsonObject arrayData = response();
		arrayData.add("data", new com.google.gson.JsonArray());
		assertRejected("data is array", arrayData);

		assertRejected("null body", null);
		assertRejected("empty body", new JsonObject());
	}

	@Test
	public void rejectsMalformedTokens() {
		String[] bad = {
			"", "rp_", "rq_" + repeat('a', 64), "RP_" + repeat('a', 64),
			"rp_" + repeat('a', 63), "rp_" + repeat('a', 65),
			"rp_" + repeat('A', 64), "rp_" + repeat('g', 64),
			TOKEN_A + "\n", " " + TOKEN_A, TOKEN_A + " ", repeat('a', 64), "Bearer " + TOKEN_A,
		};
		for (String token : bad) {
			JsonObject body = response();
			data(body).addProperty("token", token);
			assertRejected("token [" + token + "]", body);
		}
		JsonObject missing = response();
		data(missing).remove("token");
		assertRejected("no token", missing);

		JsonObject number = response();
		data(number).addProperty("token", 12345);
		assertRejected("numeric token", number);
	}

	@Test
	public void rejectsBadCredentialIds() {
		for (String id : new String[] {"", "not-a-uuid", "3f2b8c1e5d4a4e6b9a7c1d2e3f4a5b6c", "3f2b8c1e-5d4a-4e6b-9a7c-1d2e3f4a5b6c0"}) {
			JsonObject body = response();
			data(body).addProperty("credentialId", id);
			assertRejected("credentialId [" + id + "]", body);
		}
		JsonObject missing = response();
		data(missing).remove("credentialId");
		assertRejected("no credentialId", missing);
	}

	@Test
	public void rejectsAccountIdsThatAreNotPositiveWholeInts() {
		String[] bad = {"0", "-5", "1.5", "\"7\"", "2147483648", "null", "true", "[]", "1e400"};
		for (String value : bad) {
			JsonObject body = parse("{\"status\":\"success\",\"data\":" + dataWithAccountId(value) + "}");
			assertRejected("accountId " + value, body);
		}
		JsonObject missing = response();
		data(missing).remove("accountId");
		assertRejected("no accountId", missing);
	}

	private static String dataWithAccountId(String rawAccountId) {
		JsonObject data = TestKit.data(ACCOUNT_A, TOKEN_A, LATER);
		data.remove("accountId");
		String json = data.toString();
		return json.substring(0, json.length() - 1) + ",\"accountId\":" + rawAccountId + "}";
	}

	@Test
	public void rejectsACredentialForAnotherAccount() {
		JsonObject other = response();
		data(other).addProperty("accountHash", String.valueOf(ACCOUNT_B));
		assertRejected("other account", other);

		JsonObject numeric = response();
		data(numeric).remove("accountHash");
		data(numeric).addProperty("accountHash", ACCOUNT_A);
		assertRejected("accountHash must be a string", numeric);

		JsonObject padded = response();
		data(padded).addProperty("accountHash", " " + ACCOUNT_A);
		assertRejected("padded accountHash", padded);

		JsonObject missing = response();
		data(missing).remove("accountHash");
		assertRejected("no accountHash", missing);
	}

	@Test
	public void rejectsAnUnreadableExpiry() {
		for (String expiry : new String[] {"tomorrow", "", "2027-13-45T00:00:00Z", "1792000000000"}) {
			JsonObject body = response();
			data(body).addProperty("expiresAt", expiry);
			assertRejected("expiresAt [" + expiry + "]", body);
		}
		JsonObject missing = response();
		data(missing).remove("expiresAt");
		assertRejected("no expiresAt", missing);

		JsonObject number = response();
		data(number).remove("expiresAt");
		data(number).addProperty("expiresAt", 1_792_000_000_000L);
		assertRejected("numeric expiresAt", number);
	}

	@Test
	public void aPastExpiryIsStillAWellFormedCredentialAndOnlyReportsItselfExpired() throws Exception {
		// Whether it counts as expired depends on which clock is believed, so shape and expiry are separate questions.
		for (String expiry : new String[] {NOW.toString(), NOW.minusSeconds(1).toString(), "2020-01-01T00:00:00Z"}) {
			JsonObject body = response();
			data(body).addProperty("expiresAt", expiry);
			LinkCredential credential = LinkCredential.fromPairResponse(body, ACCOUNT_A);
			assertTrue(expiry, credential.isExpired(NOW));
			assertFalse("a clock that is behind may still use it", credential.isExpired(Instant.parse("2019-01-01T00:00:00Z")));
		}
	}

	@Test
	public void storedFormRoundTripsThroughTheSameValidation() throws Exception {
		LinkCredential original = LinkCredential.fromPairResponse(response(), ACCOUNT_A);
		LinkCredential restored = LinkCredential.fromData(original.toJson(), ACCOUNT_A);
		assertEquals(original, restored);
		assertEquals(original.getExpiresAt(), restored.getExpiresAt());
		try {
			LinkCredential.fromData(original.toJson(), ACCOUNT_B);
			fail("a credential must not load for a different account");
		} catch (LinkException expected) {
			assertEquals(LinkException.Kind.INVALID_RESPONSE, expected.getKind());
		}
	}

	@Test
	public void theTokenNeverAppearsInToString() throws Exception {
		LinkCredential credential = LinkCredential.fromPairResponse(response(), ACCOUNT_A);
		assertFalse(credential.toString().contains(TOKEN_A));
		assertFalse(credential.toString().contains(repeat('a', 16)));
	}
}

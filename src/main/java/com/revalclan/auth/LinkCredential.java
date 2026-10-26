package com.revalclan.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A validated plugin credential for one game account: {@code rp_} plus 64
 * lowercase hex characters, valid until {@code expiresAt}. The token is a
 * secret: {@link #toString()} redacts it and nothing here logs it.
 */
public final class LinkCredential {
	private static final Pattern TOKEN = Pattern.compile("rp_[0-9a-f]{64}");
	private static final Pattern UUID = Pattern.compile(
		"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	private final String token;
	private final String credentialId;
	private final int accountId;
	private final long accountHash;
	private final Instant expiresAt;

	private LinkCredential(String token, String credentialId, int accountId, long accountHash, Instant expiresAt) {
		this.token = token;
		this.credentialId = credentialId;
		this.accountId = accountId;
		this.accountHash = accountHash;
		this.expiresAt = expiresAt;
	}

	public String getToken() {
		return token;
	}

	public String getCredentialId() {
		return credentialId;
	}

	public int getAccountId() {
		return accountId;
	}

	public long getAccountHash() {
		return accountHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	/** The {@code data} object shape of the pairing response, also used for local storage. */
	public JsonObject toJson() {
		JsonObject data = new JsonObject();
		data.addProperty("token", token);
		data.addProperty("credentialId", credentialId);
		data.addProperty("accountId", accountId);
		data.addProperty("accountHash", String.valueOf(accountHash));
		data.addProperty("expiresAt", expiresAt.toString());
		return data;
	}

	/**
	 * Validates a full {@code /plugin/auth/pair} response body. Anything that
	 * does not match the contract exactly, including a credential for a
	 * different account than the one submitted, is rejected. Whether the
	 * expiry is still in the future is a separate question ({@link #isExpired}):
	 * it depends on which clock is believed.
	 */
	public static LinkCredential fromPairResponse(JsonObject body, long expectedAccountHash) throws LinkException {
		if (body == null || !"success".equals(string(body, "status"))) throw invalid();
		JsonElement data = body.get("data");
		if (data == null || !data.isJsonObject()) throw invalid();
		return fromData(data.getAsJsonObject(), expectedAccountHash);
	}

	/**
	 * Validates the shape of the {@code data} object; used for pairing responses and for what was stored
	 * earlier. An expiry in the past is not an error here: a computer whose clock is far ahead must not
	 * make a perfectly good 90-day token unreadable (see {@link #isExpired}).
	 */
	public static LinkCredential fromData(JsonObject data, long expectedAccountHash) throws LinkException {
		String token = string(data, "token");
		String credentialId = string(data, "credentialId");
		String accountHash = string(data, "accountHash");
		String expires = string(data, "expiresAt");
		Integer accountId = positiveInt(data.get("accountId"));
		if (token == null || !TOKEN.matcher(token).matches()) throw invalid();
		if (credentialId == null || !UUID.matcher(credentialId).matches()) throw invalid();
		if (accountId == null) throw invalid();
		if (accountHash == null || !accountHash.equals(String.valueOf(expectedAccountHash))) throw invalid();
		if (expires == null) throw invalid();
		Instant expiresAt;
		try {
			expiresAt = OffsetDateTime.parse(expires).toInstant();
		} catch (RuntimeException e) {
			throw invalid();
		}
		return new LinkCredential(token, credentialId, accountId, expectedAccountHash, expiresAt);
	}

	private static LinkException invalid() {
		return new LinkException(LinkException.Kind.INVALID_RESPONSE);
	}

	private static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
			? element.getAsString() : null;
	}

	/** A whole JSON number above zero that fits an int; fractions, strings and overflow are rejected. */
	private static Integer positiveInt(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) return null;
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (!primitive.isNumber()) return null;
		try {
			int value = new BigDecimal(primitive.getAsString()).intValueExact();
			return value > 0 ? value : null;
		} catch (ArithmeticException | NumberFormatException e) {
			return null;
		}
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof LinkCredential)) return false;
		LinkCredential that = (LinkCredential) other;
		return accountHash == that.accountHash && token.equals(that.token);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accountHash, token);
	}

	@Override
	public String toString() {
		return "LinkCredential{accountHash=" + accountHash + ", credentialId=" + credentialId + ", token=<redacted>}";
	}
}

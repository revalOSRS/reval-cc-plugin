package com.revalclan.auth;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.Response;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * What the backend says when it refuses a request: {@code {"status":"error","code":"...","message":"..."}}.
 * <p>
 * The body is read once, from at most {@link #MAX_BODY_BYTES} bytes, and never throws: anything that is
 * not a small JSON object simply has no code and no message. Neither the body nor any header is ever
 * logged. Only the machine-readable {@code code} decides what happens to a credential; the message is
 * for the player.
 */
public final class ServerError {
	/** The token is unknown, expired or revoked: the only answer that ends a link. */
	public static final String INVALID_PLUGIN_CREDENTIAL = "invalid_plugin_credential";
	/** The event id or send time was refused (clock skew, malformed id). The link itself is fine. */
	public static final String INVALID_EVENT_ENVELOPE = "invalid_event_envelope";

	static final int MAX_BODY_BYTES = 16 * 1024;
	static final int MAX_MESSAGE = 240;
	private static final Pattern CODE = Pattern.compile("[a-z0-9_]{1,64}");
	private static final ServerError NONE = new ServerError(null, null, null);

	private final String code;
	private final String message;
	private final JsonObject json;

	private ServerError(String code, String message, JsonObject json) {
		this.code = code;
		this.message = message;
		this.json = json;
	}

	/** Reads the (bounded) body of {@code response} without consuming it. */
	public static ServerError read(Response response, Gson gson) {
		if (response.body() == null) return NONE;
		try {
			return parse(response.peekBody(MAX_BODY_BYTES).string(), gson);
		} catch (IOException | RuntimeException e) {
			return NONE;
		}
	}

	static ServerError parse(String text, Gson gson) {
		if (text == null || text.isEmpty()) return NONE;
		try {
			JsonObject object = gson.fromJson(text, JsonObject.class);
			if (object == null) return NONE;
			String code = stringField(object, "code");
			if (code != null && !CODE.matcher(code).matches()) code = null;
			return new ServerError(code, oneLine(stringField(object, "message")), object);
		} catch (RuntimeException e) {
			return NONE;
		}
	}

	/** Only a real JSON string counts: {@code getAsString} would also accept a one-element array or a number. */
	private static String stringField(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
	}

	/** The machine-readable reason, or null when the body had none (or an odd one). */
	public String code() {
		return code;
	}

	public boolean is(String expectedCode) {
		return code != null && code.equals(expectedCode);
	}

	/** The server's explanation as one short line, or null. */
	public String message() {
		return message;
	}

	/** The parsed body, or null when it was not a JSON object. */
	public JsonObject json() {
		return json;
	}

	/** A single line of at most {@link #MAX_MESSAGE} characters, or null when nothing readable is left. */
	public static String oneLine(String text) {
		if (text == null) return null;
		String line = text.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
		if (line.length() > MAX_MESSAGE) line = line.substring(0, MAX_MESSAGE);
		return line.isEmpty() ? null : line;
	}
}

package com.revalclan.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.auth.ApiOrigin;
import com.revalclan.auth.LinkTokenManager;
import com.revalclan.auth.SecureHttp;
import com.revalclan.auth.ServerError;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

/**
 * Sends game events to {@code /reval-webhook}. Every request is authenticated
 * with the bearer token of the account the event was recorded for (the
 * {@code accountHash} inside the payload, never whoever is logged in when the
 * request goes out) and carries an envelope the backend uses to receipt the
 * event exactly once:
 * <ul>
 *   <li>{@code X-Reval-Event-Id}: a random UUIDv4, one per logical event and kept
 *       if the send is retried;</li>
 *   <li>{@code X-Reval-Sent-At}: epoch milliseconds, refreshed on every attempt.</li>
 * </ul>
 * An event for an account that is not linked is not sent at all.
 * <p>
 * What a refusal means is decided by the body's {@code code}, not by the status alone: only
 * {@code invalid_plugin_credential} (401) removes the link. {@code invalid_event_envelope} (400, or 401 from
 * an older backend) is about the event id or timestamp, so the link is kept and the event is retried once
 * with the same id and a fresh timestamp, taken after the refusal's {@code Date} header was observed. Any
 * other 401 keeps the link too.
 * <p>
 * A caller that must know how an event ended (a session that stays on disk until the server acknowledges
 * it) uses {@link #sendDataTracked}: exactly one of its two callbacks runs, once, when the event is over.
 */
@Slf4j
@Singleton
public class WebhookService {
	public static final String EVENT_ID_HEADER = "X-Reval-Event-Id";
	public static final String SENT_AT_HEADER = "X-Reval-Sent-At";

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType GZIP = MediaType.parse("application/gzip");
	private static final MediaType JPEG = MediaType.parse("image/jpeg");
	private static final String WEBHOOK_URL = "https://api.revalosrs.ee/reval-webhook";
	private static final int MAX_ATTEMPTS = 2;
	private static final long RETRY_DELAY_MS = 3000;

	private final Gson gson;
	private final LinkTokenManager tokens;
	private final ScheduledExecutorService executor;
	private final HttpUrl webhookUrl;
	private final OkHttpClient httpClient;

	@Inject
	public WebhookService(OkHttpClient httpClient, Gson gson, LinkTokenManager tokens, ScheduledExecutorService executor) {
		this(httpClient, gson, tokens, executor, ApiOrigin.PRODUCTION, WEBHOOK_URL);
	}

	WebhookService(OkHttpClient httpClient, Gson gson, LinkTokenManager tokens, ScheduledExecutorService executor,
	               ApiOrigin origin, String webhookUrl) {
		this.gson = gson;
		this.tokens = tokens;
		this.executor = executor;
		this.webhookUrl = HttpUrl.get(webhookUrl);
		this.httpClient = SecureHttp.harden(httpClient, origin, SecureHttp.UPLOAD_TIMEOUT_MS);
	}

	/** The client every event goes through: no redirects, no cache, trusted origin only. */
	OkHttpClient client() {
		return httpClient;
	}

	/** One logical event: fixed id and body, fresh timestamp per attempt. */
	private static final class Event {
		final String id = UUID.randomUUID().toString();
		final long accountHash;
		final RequestBody body;
		/** Called when the event ended without an answer to hand over; null for fire-and-forget. */
		final Runnable onNotDelivered;
		/** Set by whichever ending comes first, so a tracked event is reported exactly once. */
		private final AtomicBoolean ended = new AtomicBoolean();

		Event(long accountHash, RequestBody body, Runnable onNotDelivered) {
			this.accountHash = accountHash;
			this.body = body;
			this.onNotDelivered = onNotDelivered;
		}
	}

	/**
	 * Async send; hands the parsed JSON response to the consumer on success
	 * (null consumer = fire and forget).
	 * Consumer runs on the HTTP thread — do not touch the client from it.
	 *
	 * @return false when nothing was sent (no linked account for this event)
	 */
	public boolean sendDataAsync(Map<String, Object> data, Consumer<JsonObject> onResponse) {
		return sendDataAsync(data, null, onResponse);
	}

	/**
	 * Async send with an optional JPEG screenshot. Without one the body is the
	 * gzipped JSON. With one the request is multipart: the gzipped JSON in a
	 * {@code payload} part and the image as raw bytes in a {@code screenshot}
	 * part, which saves the third that base64 inside the JSON would add.
	 *
	 * @param data The event data; its {@code accountHash} selects the credential
	 * @param screenshotJpeg JPEG bytes, or null
	 * @param onResponse Optional consumer for the parsed JSON response body
	 * @return false when nothing was sent (no linked account for this event)
	 */
	public boolean sendDataAsync(Map<String, Object> data, byte[] screenshotJpeg, Consumer<JsonObject> onResponse) {
		return sendEvent(data, screenshotJpeg, onResponse, null);
	}

	/**
	 * Async send of an event whose outcome the caller has to know. Once this returns true, exactly one of the
	 * callbacks runs, once, after the last attempt (the one retry included, never in between), on the HTTP or
	 * executor thread:
	 * <ul>
	 *   <li>{@code onResponse}: Reval answered 2xx with a JSON body;</li>
	 *   <li>{@code onNotDelivered}: it ended without that: network failure, a refusal (including a rejected or
	 *       unknown link), a server error, an answer that is not JSON, or the account lost its link meanwhile.
	 *       Nothing is deleted or forgotten because of this; a later send of the same data is a new event.</li>
	 * </ul>
	 *
	 * @return false when nothing was sent (no linked account for this event); neither callback runs then
	 */
	public boolean sendDataTracked(Map<String, Object> data, Consumer<JsonObject> onResponse, Runnable onNotDelivered) {
		return sendEvent(data, null, Objects.requireNonNull(onResponse), Objects.requireNonNull(onNotDelivered));
	}

	private boolean sendEvent(Map<String, Object> data, byte[] screenshotJpeg, Consumer<JsonObject> onResponse, Runnable onNotDelivered) {
		try {
			long accountHash = accountHashOf(data);
			if (tokens.grantForEvent(webhookUrl, accountHash) == null) {
				log.debug("Event not sent: its account is not linked to Reval");
				tokens.noticeLinkRequired(accountHash);
				return false;
			}

			byte[] payload = gzip(gson.toJson(data).getBytes(StandardCharsets.UTF_8));
			RequestBody body = screenshotJpeg == null
				? RequestBody.create(JSON, payload)
				: new MultipartBody.Builder()
					.setType(MultipartBody.FORM)
					.addFormDataPart("payload", "payload.json.gz", RequestBody.create(GZIP, payload))
					.addFormDataPart("screenshot", "screenshot.jpg", RequestBody.create(JPEG, screenshotJpeg))
					.build();
			send(new Event(accountHash, body, onNotDelivered), screenshotJpeg == null, 1, onResponse);
			return true;
		} catch (IOException e) {
			log.error("Failed to prepare webhook data: {}", e.getMessage());
		} catch (Exception e) {
			log.error("Unexpected error preparing webhook", e);
		}
		return false;
	}

	private void send(Event event, boolean gzipJson, int attempt, Consumer<JsonObject> onResponse) {
		// Looked up per attempt: the link may have been revoked since the last one.
		LinkTokenManager.Grant grant = tokens.grantForEvent(webhookUrl, event.accountHash);
		if (grant == null) {
			log.debug("Event dropped: its account is no longer linked to Reval");
			notDelivered(event);
			return;
		}

		Request.Builder request = new Request.Builder()
			.url(webhookUrl)
			.header("User-Agent", PluginVersion.userAgent())
			.header(EVENT_ID_HEADER, event.id)
			.header(SENT_AT_HEADER, String.valueOf(tokens.serverNowMillis()))
			.post(event.body);
		if (gzipJson) {
			request.header("Content-Type", "application/json").header("Content-Encoding", "gzip");
		}
		grant.applyTo(request);

		httpClient.newCall(request.build()).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				if (retry(event, gzipJson, attempt, onResponse)) return;
				log.error("Failed to send data to webhook: {}", e.getMessage());
				notDelivered(event);
			}

			@Override
			public void onResponse(Call call, Response response) {
				try {
					tokens.observeServerTime(response);
					int code = response.code();
					if (code == 400 || code == 401) {
						ServerError error = ServerError.read(response, gson);
						if (code == 401 && error.is(ServerError.INVALID_PLUGIN_CREDENTIAL)) {
							tokens.rejected(grant);
							log.warn("Webhook rejected the Reval link ({})", ServerError.INVALID_PLUGIN_CREDENTIAL);
							notDelivered(event);
							return;
						}
						if (error.is(ServerError.INVALID_EVENT_ENVELOPE)) {
							// The link is fine; the id or timestamp was not. The Date header above has just corrected the clock.
							if (!retry(event, gzipJson, attempt, onResponse)) {
								tokens.envelopeRefused(event.accountHash);
								log.warn("Webhook refused the event envelope (HTTP {})", code);
								notDelivered(event);
							}
							return;
						}
						if (code == 401) {
							tokens.refused(grant);
							log.warn("Webhook answered HTTP 401 without rejecting the Reval link");
							notDelivered(event);
							return;
						}
					}
					if (isTransient(code) && retry(event, gzipJson, attempt, onResponse)) return;
					if (!response.isSuccessful()) {
						log.warn("Webhook returned non-successful status: {}", code);
						notDelivered(event);
						return;
					}
					if (onResponse == null) return;
					JsonObject parsed = response.body() == null ? null : parseJsonOrNull(response);
					if (parsed == null) {
						notDelivered(event);
						return;
					}
					if (!event.ended.compareAndSet(false, true)) return;
					try {
						onResponse.accept(parsed);
					} catch (Exception e) {
						log.warn("Webhook response handler failed: {}", e.getMessage());
					}
				} catch (RuntimeException e) {
					log.warn("Webhook response could not be processed: {}", e.toString());
					notDelivered(event);
				} finally {
					response.close();
				}
			}
		});
	}

	/** Same event id, new timestamp, a moment later. */
	private boolean retry(Event event, boolean gzipJson, int attempt, Consumer<JsonObject> onResponse) {
		if (attempt >= MAX_ATTEMPTS) return false;
		try {
			executor.schedule(() -> {
				try {
					send(event, gzipJson, attempt + 1, onResponse);
				} catch (RuntimeException e) {
					log.warn("Webhook retry failed: {}", e.toString());
					notDelivered(event);
				}
			}, RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** The event is over and there is no answer to hand over. Reported once; the caller keeps whatever it has stored. */
	private void notDelivered(Event event) {
		if (event.onNotDelivered == null || !event.ended.compareAndSet(false, true)) return;
		try {
			event.onNotDelivered.run();
		} catch (RuntimeException e) {
			log.warn("Webhook completion handler failed: {}", e.toString());
		}
	}

	private static boolean isTransient(int code) {
		return code == 429 || code == 502 || code == 503 || code == 504;
	}

	/** The account the event was recorded for, or -1 when the payload carries none. */
	private static long accountHashOf(Map<String, Object> data) {
		Object value = data.get("accountHash");
		try {
			if (value instanceof Number) return ((Number) value).longValue();
			if (value instanceof String) return Long.parseLong((String) value);
		} catch (NumberFormatException ignored) {
			// treated as no account
		}
		return -1;
	}

	private static byte[] gzip(byte[] bytes) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
			gzipStream.write(bytes);
		}
		return byteStream.toByteArray();
	}

	private JsonObject parseJsonOrNull(Response response) {
		try {
			return gson.fromJson(response.body().string(), JsonObject.class);
		} catch (Exception e) {
			log.warn("Failed to parse webhook response: {}", e.getMessage());
			return null;
		}
	}
}

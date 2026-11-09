package com.revalclan.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.auth.ApiOrigin;
import com.revalclan.auth.LinkException;
import com.revalclan.auth.LinkTokenManager;
import com.revalclan.auth.SecureHttp;
import com.revalclan.auth.ServerError;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * Authenticated, bounded event delivery. Detached payload preparation runs on the shared worker.
 * Each event keeps its recorded account and UUID across one retry, with a fresh server-adjusted
 * timestamp and credential per attempt. Only an explicit invalid_plugin_credential rejects a link.
 * Admission covers queued preparation, delayed retries, and transports until their terminal callback,
 * even when caller cancellation or plugin shutdown completes the result earlier.
 */
@Slf4j
@Singleton
public class WebhookService {
	public static final String EVENT_ID_HEADER = "X-Reval-Event-Id";
	public static final String SENT_AT_HEADER = "X-Reval-Sent-At";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String WEBHOOK_URL = "https://api.revalosrs.ee/reval-webhook";
	private final OkHttpClient httpClient;
	private final Gson gson;
	private final LinkTokenManager tokens;
	private final ScheduledExecutorService executor;
	private final HttpUrl webhookUrl;
	private final WebhookCapacity capacity = new WebhookCapacity(() ->
		log.warn("Webhook backlog is full; rejecting new events until capacity is available"));
	private volatile RequestScope scope = new RequestScope();

	@Inject
	public WebhookService(@Named("reval-http") OkHttpClient httpClient, Gson gson, LinkTokenManager tokens,
	                      ScheduledExecutorService executor) {
		this(httpClient, gson, tokens, executor, ApiOrigin.PRODUCTION, WEBHOOK_URL);
	}

	WebhookService(OkHttpClient httpClient, Gson gson, LinkTokenManager tokens, ScheduledExecutorService executor,
	               ApiOrigin origin, String webhookUrl) {
		this.httpClient = SecureHttp.harden(httpClient, origin, SecureHttp.UPLOAD_TIMEOUT_MS);
		this.gson = gson;
		this.tokens = tokens;
		this.executor = executor;
		this.webhookUrl = HttpUrl.get(webhookUrl);
	}

	OkHttpClient client() { return httpClient; }
	public void startUp() { scope.cancel(); scope = new RequestScope(); }
	public void shutDown() { scope.cancel(); }

	/** Callers transfer ownership of data and must not mutate it after sending. */
	public CompletableFuture<JsonObject> sendDataAsync(Map<String, Object> data) {
		RequestScope owner = scope;
		if (!owner.isActive()) return owner.track(new CompletableFuture<>());
		boolean critical = WebhookCapacity.isCritical(data.get("eventType"));
		if (!capacity.acquire(critical)) return CompletableFuture.failedFuture(new RejectedExecutionException("Webhook backlog is full"));
		WebhookDelivery delivery = new WebhookDelivery(owner, () -> capacity.release(critical));
		CompletableFuture<JsonObject> result = delivery.result();
		result.whenComplete((response, error) -> {
			if (error != null && !(error instanceof CancellationException))
				log.warn("Webhook delivery failed: {}", error.getClass().getSimpleName());
		});
		if (result.isDone()) { delivery.finishPreparation(); return result; }
		try {
			executor.execute(() -> {
				try {
					if (result.isDone()) { delivery.finishPreparation(); return; }
					long hash = accountHashOf(data);
					Event event = new Event(hash, RequestBody.create(JSON, gzip(gson.toJson(data).getBytes(StandardCharsets.UTF_8))));
					send(delivery, event, 1);
				} catch (Exception error) { finish(delivery, null, error); }
			});
		} catch (RuntimeException error) { finish(delivery, null, error); }
		return result;
	}

	private static final class Event {
		final String id = UUID.randomUUID().toString();
		final long accountHash;
		final RequestBody body;
		Event(long accountHash, RequestBody body) { this.accountHash = accountHash; this.body = body; }
	}

	private void send(WebhookDelivery delivery, Event event, int attempt) {
		if (delivery.result().isDone()) { delivery.finishPreparation(); return; }
		try {
			LinkTokenManager.Grant grant = tokens.grantForEvent(webhookUrl, event.accountHash);
			if (grant == null) {
				tokens.noticeLinkRequired(event.accountHash);
				finish(delivery, null, new LinkException(LinkException.Kind.NOT_LINKED));
				return;
			}
			Request.Builder request = new Request.Builder().url(webhookUrl)
				.header("User-Agent", PluginVersion.userAgent()).header("Content-Type", "application/json")
				.header("Content-Encoding", "gzip").header(EVENT_ID_HEADER, event.id)
				.header(SENT_AT_HEADER, String.valueOf(tokens.serverNowMillis())).post(event.body);
			grant.applyTo(request);
			delivery.send(httpClient, request.build(), response -> read(response, grant)).whenComplete((response, error) -> {
				if (!delivery.result().isDone() && retryable(error) && attempt < 2) {
					try {
						executor.schedule(() -> send(delivery, event, attempt + 1), 3000, TimeUnit.MILLISECONDS);
						return;
					} catch (RuntimeException rejected) { error = rejected; }
				}
				if (error instanceof WebhookFailure && ((WebhookFailure) error).envelope)
					tokens.envelopeRefused(event.accountHash);
				finish(delivery, response, error);
			});
		} catch (RuntimeException error) { finish(delivery, null, error); }
	}

	private JsonObject read(Response response, LinkTokenManager.Grant grant) throws IOException {
		tokens.observeServerTime(response);
		int code = response.code();
		if (code == 400 || code == 401) {
			ServerError error = ServerError.read(response, gson);
			if (code == 401 && error.is(ServerError.INVALID_PLUGIN_CREDENTIAL)) {
				tokens.rejected(grant);
				throw new LinkException(LinkException.Kind.EXPIRED);
			}
			if (error.is(ServerError.INVALID_EVENT_ENVELOPE)) throw new WebhookFailure(code, true, true);
			if (code == 401) tokens.refused(grant);
			throw new WebhookFailure(code, false, false);
		}
		if (!response.isSuccessful()) throw new WebhookFailure(code, code == 429 || code == 502 || code == 503 || code == 504, false);
		if (response.body() == null) throw new WebhookFailure(code, false, false);
		JsonObject parsed = gson.fromJson(response.body().string(), JsonObject.class);
		if (parsed == null) throw new WebhookFailure(code, false, false);
		return parsed;
	}

	private static boolean retryable(Throwable error) {
		return error instanceof WebhookFailure ? ((WebhookFailure) error).retryable
			: error instanceof IOException && !(error instanceof LinkException);
	}

	private static final class WebhookFailure extends IOException {
		final boolean retryable, envelope;
		WebhookFailure(int code, boolean retryable, boolean envelope) {
			super("HTTP " + code);
			this.retryable = retryable;
			this.envelope = envelope;
		}
	}

	private static void finish(WebhookDelivery delivery, JsonObject response, Throwable error) {
		if (error == null) delivery.result().complete(response);
		else delivery.result().completeExceptionally(error);
		delivery.finishPreparation();
	}

	private static long accountHashOf(Map<String, Object> data) {
		Object value = data.get("accountHash");
		try {
			if (value instanceof Number) return ((Number) value).longValue();
			if (value instanceof String) return Long.parseLong((String) value);
		} catch (NumberFormatException ignored) { }
		return -1;
	}

	private static byte[] gzip(byte[] bytes) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (GZIPOutputStream stream = new GZIPOutputStream(output)) { stream.write(bytes); }
		return output.toByteArray();
	}
}

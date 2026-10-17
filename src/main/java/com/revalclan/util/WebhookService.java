package com.revalclan.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.zip.GZIPOutputStream;

/**
 * Prepares detached notification payloads on RuneLite's shared single worker.
 * A slow preparation can delay other scheduled work; no client state is read here.
 * A send completes with a parsed response, or exceptionally on failure/cancellation.
 * Successful responses without version hints remain distinct from failed delivery.
 */
@Slf4j
@Singleton
public class WebhookService {
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType GZIP = MediaType.parse("application/gzip");
	private static final MediaType JPEG = MediaType.parse("image/jpeg");
	private static final String WEBHOOK_URL = "https://api.revalosrs.ee/reval-webhook";
	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private volatile RequestScope scope = new RequestScope();

	@Inject
	public WebhookService(@Named("reval-http") OkHttpClient httpClient, Gson gson, ScheduledExecutorService executor) {
		this.httpClient = httpClient;
		this.gson = gson;
		this.executor = executor;
	}

	public void startUp() { scope.cancel(); scope = new RequestScope(); }
	public void shutDown() { scope.cancel(); }

	public CompletableFuture<JsonObject> sendDataAsync(Map<String, Object> data) {
		return sendDataAsync(data, (byte[]) null);
	}

	/** Callers transfer ownership of data and must not mutate it after sending. */
	public CompletableFuture<JsonObject> sendDataAsync(Map<String, Object> data, byte[] screenshot) {
		RequestScope owner = scope;
		CompletableFuture<JsonObject> result = owner.track(new CompletableFuture<>());
		result.whenComplete((response, error) -> {
			if (error != null && !(error instanceof CancellationException)) log.warn("Webhook delivery failed: {}", error.toString());
		});
		if (result.isDone()) return result;
		try {
			executor.execute(() -> {
				if (result.isDone()) return;
				try {
					Request request = prepare(data, screenshot);
					if (result.isDone()) return;
					CompletableFuture<JsonObject> delivery = owner.request(httpClient, request, response -> {
						if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
						if (response.body() == null) throw new IOException("Empty webhook response");
						JsonObject parsed = gson.fromJson(response.body().string(), JsonObject.class);
						if (parsed == null) throw new IOException("Invalid webhook response");
						return parsed;
					});
					result.whenComplete((response, error) -> { if (result.isCancelled()) delivery.cancel(false); });
					delivery.whenComplete((response, error) -> {
						if (error == null) result.complete(response); else result.completeExceptionally(error);
					});
				} catch (Exception error) { result.completeExceptionally(error); }
			});
		} catch (RuntimeException error) { result.completeExceptionally(error); }
		return result;
	}

	private Request prepare(Map<String, Object> data, byte[] screenshot) throws IOException {
		byte[] payload = gzip(gson.toJson(data).getBytes(StandardCharsets.UTF_8));
		Request.Builder request = new Request.Builder().url(WEBHOOK_URL).header("User-Agent", PluginVersion.userAgent());
		if (screenshot == null) {
			request.post(RequestBody.create(JSON, payload)).header("Content-Encoding", "gzip");
		} else {
			request.post(new MultipartBody.Builder().setType(MultipartBody.FORM)
				.addFormDataPart("payload", "payload.json.gz", RequestBody.create(GZIP, payload))
				.addFormDataPart("screenshot", "screenshot.jpg", RequestBody.create(JPEG, screenshot)).build());
		}
		return request.build();
	}

	private static byte[] gzip(byte[] bytes) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (GZIPOutputStream stream = new GZIPOutputStream(output)) { stream.write(bytes); }
		return output.toByteArray();
	}
}

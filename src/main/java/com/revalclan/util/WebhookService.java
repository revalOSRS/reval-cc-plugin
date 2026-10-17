package com.revalclan.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Singleton
public class WebhookService {
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType GZIP = MediaType.parse("application/gzip");
	private static final MediaType JPEG = MediaType.parse("image/jpeg");
	private static final String WEBHOOK_URL = "https://api.revalosrs.ee/reval-webhook";
	private static final int MAX_PENDING = 64;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	// Includes preparation and queued/running HTTP calls, not just compression tasks.
	private final Semaphore capacity = new Semaphore(MAX_PENDING);
	private final Set<Delivery> pending = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean backlogWarning = new AtomicBoolean();
	private volatile boolean running = true;

	@Inject
	public WebhookService(OkHttpClient httpClient, Gson gson, ScheduledExecutorService executor) {
		this.httpClient = httpClient;
		this.gson = gson;
		this.executor = executor;
	}

	public void startUp() {
		running = true;
	}

	/** Cancel only Reval's deliveries; RuneLite owns the shared HTTP client and executor. */
	public void shutDown() {
		running = false;
		for (Delivery delivery : pending) delivery.cancel();
	}

	/**
	 * Async send; the consumer receives the parsed response or null on failure,
	 * rejection or cancellation (null consumer = fire and forget). Callers hand
	 * over a detached payload and must not mutate it after calling this method.
	 * Consumer runs off the client thread, except immediate rejection: never
	 * touch the client directly from it.
	 */
	public void sendDataAsync(Map<String, Object> data, Consumer<JsonObject> onResponse) {
		sendDataAsync(data, null, onResponse);
	}

	/**
	 * Async send with an optional JPEG screenshot. Without one the body is the
	 * gzipped JSON. With one the request is multipart: the gzipped JSON in a
	 * {@code payload} part and the image as raw bytes in a {@code screenshot}
	 * part, which saves the third that base64 inside the JSON would add.
	 *
	 * @param data The event data
	 * @param screenshotJpeg JPEG bytes, or null
	 * @param onResponse Optional consumer for the parsed JSON response body
	 */
	public void sendDataAsync(Map<String, Object> data, byte[] screenshotJpeg, Consumer<JsonObject> onResponse) {
		if (!running) {
			notifyResponse(onResponse, null);
			return;
		}
		if (!capacity.tryAcquire()) {
			if (backlogWarning.compareAndSet(false, true)) log.warn("Webhook backlog full; dropping new events until deliveries finish");
			notifyResponse(onResponse, null);
			return;
		}
		Delivery delivery = new Delivery(data, screenshotJpeg, onResponse);
		pending.add(delivery);
		if (!running) {
			delivery.cancel();
			return;
		}
		try {
			executor.execute(delivery::send);
		} catch (RuntimeException error) {
			delivery.finish(null);
			log.warn("Could not schedule webhook", error);
		}
	}

	private final class Delivery {
		private Map<String, Object> data;
		private byte[] screenshot;
		private Consumer<JsonObject> onResponse;
		private final AtomicBoolean finished = new AtomicBoolean();
		private volatile Call call;

		private Delivery(Map<String, Object> data, byte[] screenshot, Consumer<JsonObject> onResponse) {
			this.data = data;
			this.screenshot = screenshot;
			this.onResponse = onResponse;
		}

		private void send() {
			if (finished.get()) return;
			try {
				Map<String, Object> snapshot = data;
				byte[] image = screenshot;
				if (finished.get()) return;
				byte[] payload = gzip(gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8));

				Request.Builder request = new Request.Builder()
					.url(WEBHOOK_URL)
					.addHeader("User-Agent", PluginVersion.userAgent());

				if (image == null) {
					request.post(RequestBody.create(JSON, payload))
						.addHeader("Content-Type", "application/json")
						.addHeader("Content-Encoding", "gzip");
				} else {
					request.post(new MultipartBody.Builder()
						.setType(MultipartBody.FORM)
						.addFormDataPart("payload", "payload.json.gz", RequestBody.create(GZIP, payload))
						.addFormDataPart("screenshot", "screenshot.jpg", RequestBody.create(JPEG, image))
						.build());
				}
				data = null;
				screenshot = null;

				call = httpClient.newCall(request.build());
				call.timeout().timeout(30, TimeUnit.SECONDS);
				if (finished.get()) {
					call.cancel();
					return;
				}
				call.enqueue(new Callback() {
					@Override
					public void onFailure(Call call, IOException e) {
						if (!call.isCanceled()) log.warn("Failed to send data to webhook: {}", e.getMessage());
						finish(null);
					}

					@Override
					public void onResponse(Call call, Response response) {
						JsonObject parsed = null;
						try (Response closed = response) {
							if (!response.isSuccessful()) {
								log.warn("Webhook returned non-successful status: {}", response.code());
								return;
							}
							if (onResponse != null && closed.body() != null) parsed = parseJsonOrNull(closed);
						} finally {
							finish(parsed);
						}
					}
				});
			} catch (Exception error) {
				finish(null);
				log.warn("Failed to prepare webhook", error);
			}
		}

		private void cancel() {
			finish(null);
			Call current = call;
			if (current != null) current.cancel();
		}

		private void finish(JsonObject response) {
			if (!finished.compareAndSet(false, true)) return;
			Consumer<JsonObject> callback = onResponse;
			data = null;
			screenshot = null;
			onResponse = null;
			pending.remove(this);
			capacity.release();
			backlogWarning.set(false);
			notifyResponse(callback, response);
		}
	}

	private void notifyResponse(Consumer<JsonObject> callback, JsonObject response) {
		if (callback == null) return;
		try {
			callback.accept(response);
		} catch (Exception error) {
			log.warn("Webhook response handler failed: {}", error.getMessage());
		}
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

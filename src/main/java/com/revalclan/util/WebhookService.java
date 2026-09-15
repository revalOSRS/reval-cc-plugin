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
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Singleton
public class WebhookService {
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType GZIP = MediaType.parse("application/gzip");
	private static final MediaType JPEG = MediaType.parse("image/jpeg");
	private static final String WEBHOOK_URL = "https://api.revalosrs.ee/reval-webhook";

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	/**
	 * Async send; hands the parsed JSON response to the consumer on success
	 * (null consumer = fire and forget).
	 * Consumer runs on the HTTP thread — do not touch the client from it.
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
		try {
			byte[] payload = gzip(gson.toJson(data).getBytes(StandardCharsets.UTF_8));

			Request.Builder request = new Request.Builder()
				.url(WEBHOOK_URL)
				.addHeader("User-Agent", PluginVersion.userAgent());

			if (screenshotJpeg == null) {
				request.post(RequestBody.create(JSON, payload))
					.addHeader("Content-Type", "application/json")
					.addHeader("Content-Encoding", "gzip");
			} else {
				request.post(new MultipartBody.Builder()
					.setType(MultipartBody.FORM)
					.addFormDataPart("payload", "payload.json.gz", RequestBody.create(GZIP, payload))
					.addFormDataPart("screenshot", "screenshot.jpg", RequestBody.create(JPEG, screenshotJpeg))
					.build());
			}

			httpClient.newCall(request.build()).enqueue(new Callback() {
				@Override
				public void onFailure(Call call, IOException e) {
					log.error("Failed to send data to webhook: {}", e.getMessage());
				}

				@Override
				public void onResponse(Call call, Response response) {
					try {
						if (!response.isSuccessful()) {
							log.warn("Webhook returned non-successful status: {}", response.code());
							return;
						}
						if (onResponse == null || response.body() == null) return;
						JsonObject parsed = parseJsonOrNull(response);
						if (parsed == null) return;
						try {
							onResponse.accept(parsed);
						} catch (Exception e) {
							log.warn("Webhook response handler failed: {}", e.getMessage());
						}
					} finally {
						response.close();
					}
				}
			});
		} catch (IOException e) {
			log.error("Failed to prepare webhook data: {}", e.getMessage());
		} catch (Exception e) {
			log.error("Unexpected error preparing webhook", e);
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

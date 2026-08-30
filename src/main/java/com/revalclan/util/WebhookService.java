package com.revalclan.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Singleton
public class WebhookService {
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
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
		sendDataAsync(WEBHOOK_URL, data, onResponse);
	}

	/**
	 * Sends player data to a specific webhook URL asynchronously
	 *
	 * @param webhookUrl The webhook endpoint URL
	 * @param data The player data to send
	 * @param onResponse Optional consumer for the parsed JSON response body
	 */
	private void sendDataAsync(String webhookUrl, Map<String, Object> data, Consumer<JsonObject> onResponse) {
		if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
			return;
		}

		try {
			String json = gson.toJson(data);
			byte[] jsonBytes = json.getBytes("UTF-8");
			
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
				gzipStream.write(jsonBytes);
			}
			byte[] compressedData = byteStream.toByteArray();
			
			RequestBody body = RequestBody.create(JSON, compressedData);
			
			Request request = new Request.Builder()
				.url(webhookUrl)
				.post(body)
				.addHeader("Content-Type", "application/json")
				.addHeader("Content-Encoding", "gzip")
				.addHeader("User-Agent", PluginVersion.userAgent())
				.build();

			httpClient.newCall(request).enqueue(new Callback() {
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

	private JsonObject parseJsonOrNull(Response response) {
		try {
			return gson.fromJson(response.body().string(), JsonObject.class);
		} catch (Exception e) {
			log.warn("Failed to parse webhook response: {}", e.getMessage());
			return null;
		}
	}
}


package com.revalclan.util;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
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
	 * Sends player data to webhook asynchronously
	 */
	public void sendDataAsync(Map<String, Object> data) {
		sendDataAsync(WEBHOOK_URL, data);
	}

	/**
	 * Sends player data to a specific webhook URL asynchronously
	 * 
	 * @param webhookUrl The webhook endpoint URL
	 * @param data The player data to send
	 */
	private void sendDataAsync(String webhookUrl, Map<String, Object> data) {
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
				.addHeader("User-Agent", "RuneLite-RevalClan-Plugin")
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
}


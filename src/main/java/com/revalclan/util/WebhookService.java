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
	 * Sends player data to the webhook URL with gzip compression
	 * 
	 * @param data The player data to send
	 * @return true if successful, false otherwise
	 */
	public boolean sendData(Map<String, Object> data) {
		return sendData(WEBHOOK_URL, data);
	}

	/**
	 * Sends player data to a specific webhook URL with gzip compression
	 * (Internal method for flexibility)
	 * 
	 * @param webhookUrl The webhook endpoint URL
	 * @param data The player data to send
	 * @return true if successful, false otherwise
	 */
	private boolean sendData(String webhookUrl, Map<String, Object> data) {
		if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
			return false;
		}

		try {
			String json = gson.toJson(data);
			byte[] jsonBytes = json.getBytes("UTF-8");
			
			// Compress the JSON with gzip
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

			try (Response response = httpClient.newCall(request).execute()) {
				if (response.isSuccessful()) return true;
				else return false;
			}
		}
		catch (IOException e) {
			log.error("Failed to send data to webhook: {}", e.getMessage());
			return false;
		} catch (Exception e) {
			log.error("Unexpected error sending webhook", e);
			return false;
		}
	}

	/**
	 * Sends player data to webhook asynchronously
	 */
	public void sendDataAsync(Map<String, Object> data) {
		new Thread(() -> sendData(data), "RevalClan-Webhook").start();
	}
}


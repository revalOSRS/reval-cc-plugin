package com.revalclan.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Protocol;
import okhttp3.MultipartBody;
import okio.Buffer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WebhookServiceTest {
	private final Queue<Runnable> tasks = new ArrayDeque<>();
	private final OkHttpClient http = mock(OkHttpClient.class);
	private final Call call = mock(Call.class);
	private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
	private WebhookService service;

	@Before public void setUp() {
		when(http.newCall(any(Request.class))).thenReturn(call);
		when(call.timeout()).thenReturn(new okio.Timeout());
		doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(executor).execute(any(Runnable.class));
		service = new WebhookService(http, new Gson(), executor);
	}

	@Test public void payloadPreparationIsDeferred() {
		service.sendDataAsync(Map.of("eventType", "SYNC"));
		verifyNoInteractions(http);
		drain();
		verify(http).newCall(any(Request.class));
	}

	@Test public void failedDeliveryReleasesTheSessionRecoveryCallback() {
		CompletableFuture<JsonObject> result;
		result = service.sendDataAsync(Map.of("eventType", "SESSION_SUMMARY"));
		drain();
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		callback.getValue().onFailure(call, new IOException("offline"));
		assertTrue(result.isCompletedExceptionally());
	}


	@Test public void shutdownDropsQueuedPayloadsAndAllowsRestart() {
		CompletableFuture<JsonObject> result;
		result = service.sendDataAsync(Map.of("eventType", "SESSION_SUMMARY"));
		service.shutDown();
		drain();
		verifyNoInteractions(http);
		assertTrue(result.isCompletedExceptionally());
		service.startUp();
		service.sendDataAsync(Map.of("eventType", "CHAT"));
		drain();
		verify(http).newCall(any(Request.class));
	}

	@Test public void shutdownCancelsOnlyOwnedRequestsAndCompletesOnce() {
		CompletableFuture<JsonObject> result;
		result = service.sendDataAsync(Map.of("eventType", "SESSION_SUMMARY"));
		drain();
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		service.shutDown();
		callback.getValue().onFailure(call, new IOException("canceled"));
		verify(call).cancel();
		assertTrue(result.isCancelled());
		verify(http).newCall(any(Request.class));
		verifyNoMoreInteractions(http);
	}


	@Test public void payloadStillUsesTheExistingGzipWireFormat() throws Exception {
		service.sendDataAsync(Map.of("eventType", "SYNC", "quantity", 12));
		drain();
		ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
		verify(http).newCall(request.capture());
		assertEquals("gzip", request.getValue().header("Content-Encoding"));
		Buffer buffer = new Buffer();
		request.getValue().body().writeTo(buffer);
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(buffer.readByteArray()))) {
			JsonObject payload = new Gson().fromJson(new String(gzip.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
			assertEquals("SYNC", payload.get("eventType").getAsString());
			assertEquals(12, payload.get("quantity").getAsInt());
		}
	}

	@Test public void screenshotsRemainRawMultipartBytes() throws Exception {
		byte[] screenshot = {1, 2, 3, 4};
		service.sendDataAsync(Map.of("eventType", "PET"), screenshot);
		drain();
		ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
		verify(http).newCall(request.capture());
		MultipartBody body = (MultipartBody) request.getValue().body();
		assertEquals(2, body.size());
		Buffer buffer = new Buffer();
		body.part(1).body().writeTo(buffer);
		assertArrayEquals(screenshot, buffer.readByteArray());
	}

	@Test public void unsuccessfulOrInvalidResponsesStillReleaseRecovery() throws Exception {
		for (int status : new int[] {500, 200}) {
			clearInvocations(call);
			CompletableFuture<JsonObject> result;
			result = service.sendDataAsync(Map.of("eventType", "SESSION_SUMMARY"));
			drain();
			ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
			verify(call).enqueue(callback.capture());
			callback.getValue().onResponse(call, new Response.Builder()
				.request(new Request.Builder().url("https://example.invalid").build())
				.protocol(Protocol.HTTP_1_1).code(status).message("test")
				.body(ResponseBody.create(null, "invalid json")).build());
			assertTrue(result.isCompletedExceptionally());
		}
	}

	@Test public void rejectedExecutorCompletesTheCallback() {
		doThrow(new java.util.concurrent.RejectedExecutionException()).when(executor).execute(any(Runnable.class));
		CompletableFuture<JsonObject> result;
		result = service.sendDataAsync(Map.of("eventType", "SESSION_SUMMARY"));
		assertTrue(result.isCompletedExceptionally());
		verifyNoInteractions(http);
	}

	@Test public void successfulResponsesWithoutHintsAreDifferentFromDeliveryFailure() throws Exception {
		CompletableFuture<JsonObject> result = service.sendDataAsync(Map.of("eventType", "LOGIN"));
		drain();
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		callback.getValue().onResponse(call, new Response.Builder()
			.request(new Request.Builder().url("https://example.invalid").build())
			.protocol(Protocol.HTTP_1_1).code(200).message("OK")
			.body(ResponseBody.create(null, "{}")).build());
		assertNotNull(result.join());
		assertEquals(0, result.join().size());
	}

	private void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
	@Test public void offlineBurstsRetainAtMostSixtyFourPayloads() {
		for (int i = 0; i < 64; i++) assertFalse(service.sendDataAsync(Map.of("eventType", "SESSION_HEARTBEAT")).isDone());
		for (int i = 0; i < 1000; i++) assertTrue(service.sendDataAsync(Map.of("eventType", "SESSION_HEARTBEAT")).isCompletedExceptionally());
		assertEquals(64, tasks.size());
		service.shutDown();
		drain();
	}

	@Test public void networkFailuresReleaseTheDeliveryLimit() {
		for (int i = 0; i < 64; i++) service.sendDataAsync(Map.of("eventType", "SESSION_HEARTBEAT"));
		drain();
		ArgumentCaptor<Callback> callbacks = ArgumentCaptor.forClass(Callback.class);
		verify(call, times(64)).enqueue(callbacks.capture());
		for (Callback callback : callbacks.getAllValues()) callback.onFailure(call, new IOException("offline"));
		for (int i = 0; i < 64; i++) assertFalse(service.sendDataAsync(Map.of("eventType", "SESSION_HEARTBEAT")).isDone());
		assertEquals(64, tasks.size());
		service.shutDown();
		drain();
	}

	@Test public void repeatedRestartsCannotAccumulatePayloadsOnABlockedWorker() {
		for (int i = 0; i < 1000; i++) {
			service.sendDataAsync(Map.of("eventType", "LOGIN"));
			service.shutDown();
			service.startUp();
		}
		assertEquals(64, tasks.size());
		drain();
		verifyNoInteractions(http);
		assertFalse(service.sendDataAsync(Map.of("eventType", "LOGIN")).isDone());
		service.shutDown();
		drain();
	}
}

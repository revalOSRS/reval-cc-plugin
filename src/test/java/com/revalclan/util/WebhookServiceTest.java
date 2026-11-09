package com.revalclan.util;

import com.google.gson.Gson;
import com.revalclan.auth.TestKit.Rig;
import static com.revalclan.auth.TestKit.ACCOUNT_A;
import static com.revalclan.auth.TestKit.TOKEN_A;
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
	private Rig rig;
	@org.junit.Before public void linkAccount() {
		rig = new Rig();
		rig.link(ACCOUNT_A, TOKEN_A);
	}
	@org.junit.After public void closeAccount() throws Exception { rig.close(); }

	private final Queue<Runnable> tasks = new ArrayDeque<>();
	private final Queue<Runnable> retries = new ArrayDeque<>();
	private final OkHttpClient http = mock(OkHttpClient.class);
	private final Call call = mock(Call.class);
	private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
	private WebhookService service;

	@Before public void setUp() {
		com.revalclan.auth.TestKit.deriveMockClient(http);
		when(http.newCall(any(Request.class))).thenReturn(call);
		when(call.timeout()).thenReturn(new okio.Timeout());
		doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(executor).execute(any(Runnable.class));
		when(executor.schedule(any(Runnable.class), anyLong(), any(java.util.concurrent.TimeUnit.class)))
			.thenAnswer(invocation -> { retries.add(invocation.getArgument(0)); return null; });
		service = new WebhookService(http, new Gson(), rig.tokens, executor);
		clearInvocations(http);
	}

	@Test public void payloadPreparationIsDeferred() {
		service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SYNC"));
		verifyNoInteractions(http);
		drain();
		verify(http).newCall(any(Request.class));
	}

	@Test public void failedDeliveryReleasesTheSessionRecoveryCallback() {
		CompletableFuture<JsonObject> result;
		result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SESSION_SUMMARY"));
		drain();
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		callback.getValue().onFailure(call, new IOException("offline"));
		assertFalse("the one retry is still pending", result.isDone());
		retries.remove().run();
		verify(call, times(2)).enqueue(callback.capture());
		callback.getValue().onFailure(call, new IOException("still offline"));
		assertTrue(result.isCompletedExceptionally());
	}


	@Test public void shutdownDropsQueuedPayloadsAndAllowsRestart() {
		CompletableFuture<JsonObject> result;
		result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SESSION_SUMMARY"));
		service.shutDown();
		drain();
		verifyNoInteractions(http);
		assertTrue(result.isCompletedExceptionally());
		service.startUp();
		service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT"));
		drain();
		verify(http).newCall(any(Request.class));
	}

	@Test public void shutdownCancelsOnlyOwnedRequestsAndCompletesOnce() {
		CompletableFuture<JsonObject> result;
		result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SESSION_SUMMARY"));
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
		service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SYNC", "quantity", 12));
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

	@Test public void unsuccessfulOrInvalidResponsesStillReleaseRecovery() throws Exception {
		for (int status : new int[] {500, 200}) {
			clearInvocations(call);
			CompletableFuture<JsonObject> result;
			result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SESSION_SUMMARY"));
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
		result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SESSION_SUMMARY"));
		assertTrue(result.isCompletedExceptionally());
		verifyNoInteractions(http);
	}

	@Test public void preparationFailuresReleaseCapacityAndNeverRunSuccessCallbacks() {
		Runnable success = mock(Runnable.class);
		for (int i = 0; i < 128; i++) {
			CompletableFuture<JsonObject> result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN", "value", Double.NaN));
			result.thenRun(success);
			drain();
			assertTrue(result.isCompletedExceptionally());
		}
		verifyNoInteractions(success, http);
		assertFullCapacityAvailable();
	}

	@Test public void repeatedExecutorRejectionDoesNotLeakAdmissionSlots() {
		doThrow(new java.util.concurrent.RejectedExecutionException()).when(executor).execute(any(Runnable.class));
		for (int i = 0; i < 128; i++) {
			assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isCompletedExceptionally());
		}
		doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(executor).execute(any(Runnable.class));
		assertFullCapacityAvailable();
	}

	@Test public void repeatedEnqueueRejectionDoesNotLeakAdmissionSlots() {
		doThrow(new java.util.concurrent.RejectedExecutionException()).when(call).enqueue(any());
		for (int i = 0; i < 128; i++) {
			CompletableFuture<JsonObject> result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN"));
			drain();
			assertTrue(result.isCompletedExceptionally());
		}
		assertFullCapacityAvailable();
	}

	@Test public void successfulResponsesWithoutHintsAreDifferentFromDeliveryFailure() throws Exception {
		CompletableFuture<JsonObject> result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN"));
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
		for (int i = 0; i < 64; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SESSION_HEARTBEAT")).isDone());
		for (int i = 0; i < 1000; i++) assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SESSION_HEARTBEAT")).isCompletedExceptionally());
		assertEquals(64, tasks.size());
		service.shutDown();
		drain();
	}

	@Test public void networkFailuresReleaseTheDeliveryLimit() {
		for (int i = 0; i < 64; i++) service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SESSION_HEARTBEAT"));
		drain();
		ArgumentCaptor<Callback> callbacks = ArgumentCaptor.forClass(Callback.class);
		verify(call, times(64)).enqueue(callbacks.capture());
		for (Callback callback : callbacks.getAllValues()) callback.onFailure(call, new IOException("offline"));
		while (!retries.isEmpty()) retries.remove().run();
		ArgumentCaptor<Callback> secondAttempts = ArgumentCaptor.forClass(Callback.class);
		verify(call, times(128)).enqueue(secondAttempts.capture());
		for (Callback callback : secondAttempts.getAllValues().subList(64, 128))
			callback.onFailure(call, new IOException("still offline"));
		for (int i = 0; i < 64; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SESSION_HEARTBEAT")).isDone());
		assertEquals(64, tasks.size());
		service.shutDown();
		drain();
	}

	@Test public void repeatedRestartsCannotAccumulatePayloadsOnABlockedWorker() {
		for (int i = 0; i < 1000; i++) {
			service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN"));
			service.shutDown();
			service.startUp();
		}
		assertEquals(64, tasks.size());
		drain();
		verifyNoInteractions(http);
		assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isDone());
		service.shutDown();
		drain();
	}
	@Test public void ordinaryBurstsLeaveSixteenSlotsForSessionAndSyncEvents() {
		for (int i = 0; i < 48; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isDone());
		for (int i = 0; i < 1000; i++) assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isCompletedExceptionally());
		for (int i = 0; i < 16; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isDone());
		assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SYNC")).isCompletedExceptionally());
		assertEquals(64, tasks.size());
		drain();
		verify(http, times(64)).newCall(any(Request.class));
		service.shutDown();
	}

	@Test public void failedRequestsReleaseCapacityForTheNextBurst() {
		for (int i = 0; i < 48; i++) service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT"));
		drain();
		ArgumentCaptor<Callback> callbacks = ArgumentCaptor.forClass(Callback.class);
		verify(call, times(48)).enqueue(callbacks.capture());
		for (Callback callback : callbacks.getAllValues()) callback.onFailure(call, new IOException("offline"));
		while (!retries.isEmpty()) retries.remove().run();
		ArgumentCaptor<Callback> secondAttempts = ArgumentCaptor.forClass(Callback.class);
		verify(call, times(96)).enqueue(secondAttempts.capture());
		for (Callback callback : secondAttempts.getAllValues().subList(48, 96))
			callback.onFailure(call, new IOException("still offline"));
		for (int i = 0; i < 48; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isDone());
		assertEquals(48, tasks.size());
		service.shutDown();
		drain();
	}


	@Test public void callerCancelledCallsReleaseEachSlotOnlyOnceOnRepeatedTerminalCallbacks() {
		Runnable success = mock(Runnable.class);
		for (int i = 0; i < 64; i++) {
			CompletableFuture<JsonObject> result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN"));
			result.thenRun(success);
			drain();
			assertTrue(result.cancel(false));
		}
		ArgumentCaptor<Callback> callbacks = ArgumentCaptor.forClass(Callback.class);
		verify(call, times(64)).enqueue(callbacks.capture());
		verify(call, times(64)).cancel();
		assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isCompletedExceptionally());
		for (Callback callback : callbacks.getAllValues()) {
			callback.onFailure(call, new IOException("cancelled"));
			callback.onFailure(call, new IOException("duplicate terminal callback"));
		}
		verifyNoInteractions(success);
		assertFullCapacityAvailable();
	}

	private void assertFullCapacityAvailable() {
		for (int i = 0; i < 64; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isDone());
		assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isCompletedExceptionally());
		assertEquals(64, tasks.size());
		service.shutDown();
		drain();
	}
}

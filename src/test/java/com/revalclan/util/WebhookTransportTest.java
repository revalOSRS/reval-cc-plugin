package com.revalclan.util;

import com.google.gson.Gson;
import com.revalclan.auth.TestKit.Rig;
import static com.revalclan.auth.TestKit.ACCOUNT_A;
import static com.revalclan.auth.TestKit.TOKEN_A;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class WebhookTransportTest {
	private Rig rig;
	@org.junit.Before public void linkAccount() {
		rig = new Rig();
		rig.link(ACCOUNT_A, TOKEN_A);
	}
	@org.junit.After public void closeAccount() throws Exception { rig.close(); }

	@Test public void restartsRetainAtMostSixtyFourQueuedTransportsUntilTheirTerminalCallbacks() {
		HeldExecutor transport = new HeldExecutor();
		Dispatcher dispatcher = new Dispatcher(transport);
		dispatcher.setMaxRequests(1);
		dispatcher.setMaxRequestsPerHost(1);
		OkHttpClient http = localHttp(dispatcher, new AtomicReference<>());
		// This real call occupies the dispatcher's running slot without running
		// network work, so subsequent real calls retain their bodies in its queue.
		http.newCall(new Request.Builder().url("https://api.revalosrs.ee/blocker").build()).enqueue(new Callback() {
			@Override public void onFailure(Call call, IOException error) { fail(error.toString()); }
			@Override public void onResponse(Call call, Response response) { response.close(); }
		});
		WebhookService service = new WebhookService(http, new Gson(), rig.tokens, directPreparation());
		try {
			for (int i = 0; i < 256; i++) {
				CompletableFuture<JsonObject> result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN", "value", i));
				service.shutDown();
				assertTrue("Shutdown must finish callers immediately", result.isDone());
				service.startUp();
			}
			assertEquals(64, dispatcher.queuedCallsCount());
			assertEquals(64, dispatcher.queuedCalls().stream().filter(Call::isCanceled).count());
			assertEquals(64, dispatcher.queuedCalls().stream().filter(call -> call.request().body() != null).count());
			assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isCompletedExceptionally());

			transport.runNext(); // Finish the blocker; the first cancelled webhook is only promoted.
			assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isCompletedExceptionally());
			transport.runNext(); // The first webhook now delivers its actual terminal callback.
			assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isDone());
		} finally {
			service.shutDown();
			transport.drain();
		}
		assertEquals(0, dispatcher.queuedCallsCount());
		assertEquals(0, dispatcher.runningCallsCount());
	}

	@Test public void ordinaryDeliveryUsesGzipAndParsesSuccessfulResponseWithTheRealDispatcher() throws Exception {
		HeldExecutor transport = new HeldExecutor();
		Dispatcher dispatcher = new Dispatcher(transport);
		AtomicReference<Request> sent = new AtomicReference<>();
		WebhookService service = new WebhookService(localHttp(dispatcher, sent), new Gson(), rig.tokens, directPreparation());
		CompletableFuture<JsonObject> result = service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SYNC", "quantity", 12));
		assertFalse(result.isDone());
		transport.drain();
		assertEquals("accepted", result.join().get("status").getAsString());
		assertEquals("gzip", sent.get().header("Content-Encoding"));
		Buffer buffer = new Buffer();
		sent.get().body().writeTo(buffer);
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(buffer.readByteArray()))) {
			JsonObject payload = new Gson().fromJson(new String(gzip.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
			assertEquals("SYNC", payload.get("eventType").getAsString());
			assertEquals(12, payload.get("quantity").getAsInt());
		}
		assertEquals(0, dispatcher.runningCallsCount());
	}

	@Test public void cancelledOrdinaryTransportsKeepReservedSlotsAndReleaseTheCorrectCategory() {
		HeldExecutor transport = new HeldExecutor();
		Dispatcher dispatcher = new Dispatcher(transport);
		dispatcher.setMaxRequests(1);
		dispatcher.setMaxRequestsPerHost(1);
		OkHttpClient http = localHttp(dispatcher, new AtomicReference<>());
		http.newCall(new Request.Builder().url("https://api.revalosrs.ee/blocker").build()).enqueue(new Callback() {
			@Override public void onFailure(Call call, IOException error) { fail(error.toString()); }
			@Override public void onResponse(Call call, Response response) { response.close(); }
		});
		WebhookService service = new WebhookService(http, new Gson(), rig.tokens, directPreparation());
		try {
			for (int i = 0; i < 48; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isDone());
			service.shutDown();
			service.startUp();
			assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isCompletedExceptionally());
			for (int i = 0; i < 16; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isDone());
			assertEquals(64, dispatcher.queuedCallsCount());
			service.shutDown();
			service.startUp();
			assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isCompletedExceptionally());
			transport.runNext(); // The unrelated blocker releases no Reval admission lease.
			assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isCompletedExceptionally());
			transport.runNext(); // A cancelled ordinary call now acknowledges termination.
			assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isDone());
			assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isCompletedExceptionally());
			transport.drain();
			assertEquals(0, dispatcher.queuedCallsCount());
			assertEquals(0, dispatcher.runningCallsCount());
			// Releasing priority calls must not decrement the ordinary counter.
			for (int i = 0; i < 48; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isDone());
			assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "CHAT")).isCompletedExceptionally());
			for (int i = 0; i < 16; i++) assertFalse(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "LOGIN")).isDone());
			assertTrue(service.sendDataAsync(Map.of("accountHash", ACCOUNT_A, "eventType", "SYNC")).isCompletedExceptionally());
		} finally {
			service.shutDown();
			transport.drain();
		}
		assertEquals(0, dispatcher.queuedCallsCount());
		assertEquals(0, dispatcher.runningCallsCount());
	}

	private static OkHttpClient localHttp(Dispatcher dispatcher, AtomicReference<Request> sent) {
		return new OkHttpClient.Builder().dispatcher(dispatcher).callTimeout(30, TimeUnit.SECONDS)
			.addInterceptor(chain -> {
				sent.set(chain.request());
				return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
					.code(200).message("OK").body(ResponseBody.create(null, "{\"status\":\"accepted\"}")).build();
			}).build();
	}

	private static ScheduledExecutorService directPreparation() {
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		doAnswer(invocation -> { invocation.<Runnable>getArgument(0).run(); return null; })
			.when(executor).execute(any(Runnable.class));
		return executor;
	}

	private static final class HeldExecutor extends AbstractExecutorService {
		private final Queue<Runnable> tasks = new ArrayDeque<>();
		@Override public void execute(Runnable task) { tasks.add(task); }
		void runNext() { tasks.remove().run(); }
		void drain() { while (!tasks.isEmpty()) runNext(); }
		@Override public void shutdown() { }
		@Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
		@Override public boolean isShutdown() { return false; }
		@Override public boolean isTerminated() { return false; }
		@Override public boolean awaitTermination(long timeout, TimeUnit unit) { return false; }
	}
}

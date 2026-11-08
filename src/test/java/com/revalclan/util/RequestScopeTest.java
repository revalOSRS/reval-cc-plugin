package com.revalclan.util;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RequestScopeTest {
	@Test public void cancellationCompletesOnceCancelsOnlyOwnedCallsAndClosesLateResponses() throws Exception {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(http.newCall(any())).thenReturn(call);
		RequestScope scope = new RequestScope();
		CompletableFuture<String> result = scope.request(http, request(), response -> { fail("Cancelled parser"); return null; });
		AtomicInteger completions = new AtomicInteger();
		result.whenComplete((data, error) -> completions.incrementAndGet());
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		scope.cancel();
		assertTrue(result.isCancelled());
		verify(call).cancel();
		ResponseBody body = mock(ResponseBody.class);
		callback.getValue().onResponse(call, new Response.Builder().request(request()).protocol(Protocol.HTTP_1_1)
			.code(200).message("OK").body(body).build());
		callback.getValue().onFailure(call, new IOException("cancelled"));
		verify(body).close();
		assertEquals(1, completions.get());
		verify(http).newCall(any());
		verifyNoMoreInteractions(http);
	}

	@Test public void queuedUiCallbacksCheckTheirScopeWhenTheyRun() {
		RequestScope scope = new RequestScope();
		Runnable callback = mock(Runnable.class);
		Runnable queued = scope.guard(callback);
		scope.cancel();
		queued.run();
		verifyNoInteractions(callback);
		assertTrue(scope.track(new CompletableFuture<>()).isCancelled());
	}

	@Test public void cancelledCallerKeepsTransportOwnershipUntilLateResponseCloses() throws Exception {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(http.newCall(any())).thenReturn(call);
		RequestScope scope = new RequestScope();
		RequestScope.Exchange<String> exchange = scope.exchange(http, request(), response -> {
			fail("Cancelled responses must not be parsed");
			return null;
		});
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		AtomicBoolean closed = new AtomicBoolean();
		ResponseBody body = mock(ResponseBody.class);
		doAnswer(invocation -> { closed.set(true); return null; }).when(body).close();
		AtomicInteger finishes = new AtomicInteger();
		exchange.finished().thenRun(() -> {
			assertTrue("Response closure precedes terminal ownership release", closed.get());
			finishes.incrementAndGet();
		});
		scope.cancel();
		assertTrue(exchange.result().isCancelled());
		assertFalse(exchange.finished().isDone());
		callback.getValue().onResponse(call, response(body));
		callback.getValue().onFailure(call, new IOException("late cancellation"));
		assertTrue(exchange.finished().isDone());
		assertEquals(1, finishes.get());
		verify(body).close();
	}

	@Test public void responsesCloseBeforeTransportFinishesOnSuccessAndReaderFailure() throws Exception {
		for (boolean failRead : new boolean[] {false, true}) {
			OkHttpClient http = mock(OkHttpClient.class);
			Call call = mock(Call.class);
			when(http.newCall(any())).thenReturn(call);
			RequestScope.Exchange<String> exchange = new RequestScope().exchange(http, request(), response -> {
				if (failRead) throw new IOException("invalid body");
				return "parsed";
			});
			ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
			verify(call).enqueue(callback.capture());
			ResponseBody body = mock(ResponseBody.class);
			doAnswer(invocation -> {
				assertFalse("Transport ownership must include response closure", exchange.finished().isDone());
				return null;
			}).when(body).close();
			callback.getValue().onResponse(call, response(body));
			verify(body).close();
			assertTrue(exchange.finished().isDone());
			assertEquals(failRead, exchange.result().isCompletedExceptionally());
			if (!failRead) assertEquals("parsed", exchange.result().join());
		}
	}

	@Test public void cancellationDuringResponseReadingWaitsForReaderExitAndClosure() throws Exception {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(http.newCall(any())).thenReturn(call);
		RequestScope scope = new RequestScope();
		CountDownLatch reading = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		RequestScope.Exchange<String> exchange = scope.exchange(http, request(), response -> {
			reading.countDown();
			assertTrue("Reader should be released", resume.await(5, TimeUnit.SECONDS));
			return "parsed";
		});
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		ResponseBody body = mock(ResponseBody.class);
		CompletableFuture<Void> response = CompletableFuture.runAsync(() -> {
			try { callback.getValue().onResponse(call, response(body)); }
			catch (IOException error) { throw new AssertionError(error); }
		});
		try {
			assertTrue(reading.await(5, TimeUnit.SECONDS));
			scope.cancel();
			assertTrue(exchange.result().isCancelled());
			assertFalse(exchange.finished().isDone());
			verify(body, never()).close();
		} finally {
			resume.countDown();
			response.get(5, TimeUnit.SECONDS);
		}
		verify(body).close();
		assertTrue(exchange.finished().isDone());
	}

	@Test public void cancellationBeforeCallCreationFinishesWithoutEnqueuing() {
		OkHttpClient http = mock(OkHttpClient.class);
		RequestScope scope = new RequestScope();
		scope.cancel();
		RequestScope.Exchange<String> exchange = scope.exchange(http, request(), response -> "unused");
		assertTrue(exchange.result().isCancelled());
		assertTrue(exchange.finished().isDone());
		verifyNoInteractions(http);
	}

	@Test public void cancellationDuringCallCreationFinishesWithoutEnqueuing() {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		RequestScope scope = new RequestScope();
		when(http.newCall(any())).thenAnswer(invocation -> { scope.cancel(); return call; });
		RequestScope.Exchange<String> exchange = scope.exchange(http, request(), response -> "unused");
		assertTrue(exchange.result().isCancelled());
		assertTrue(exchange.finished().isDone());
		verify(call).cancel();
		verify(call, never()).enqueue(any());
	}

	@Test public void callCreationAndEnqueueRejectionFinishTransportOwnership() {
		for (boolean failCreation : new boolean[] {false, true}) {
			OkHttpClient http = mock(OkHttpClient.class);
			Call call = mock(Call.class);
			if (failCreation) when(http.newCall(any())).thenThrow(new IllegalStateException("unavailable client"));
			else {
				when(http.newCall(any())).thenReturn(call);
				doThrow(new RejectedExecutionException("unavailable dispatcher")).when(call).enqueue(any());
			}
			RequestScope.Exchange<String> exchange = new RequestScope().exchange(http, request(), response -> "unused");
			assertTrue(exchange.result().isCompletedExceptionally());
			assertTrue(exchange.finished().isDone());
		}
	}

	private Response response(ResponseBody body) {
		return new Response.Builder().request(request()).protocol(Protocol.HTTP_1_1)
			.code(200).message("OK").body(body).build();
	}

	private Request request() { return new Request.Builder().url("https://example.invalid").build(); }
}

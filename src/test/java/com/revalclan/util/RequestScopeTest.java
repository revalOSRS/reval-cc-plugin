package com.revalclan.util;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
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

	private Request request() { return new Request.Builder().url("https://example.invalid").build(); }
}

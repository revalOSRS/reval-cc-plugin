package com.revalclan.api;

import com.google.gson.Gson;
import com.revalclan.api.points.PointsResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RevalApiServiceTest {
	@Test public void shutdownCancelsOwnedCallsAndSuppressesTheirLateResponses() throws IOException {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(call.timeout()).thenReturn(new okio.Timeout());
		when(http.newCall(any(Request.class))).thenReturn(call);
		AtomicBoolean canceled = new AtomicBoolean();
		doAnswer(invocation -> { canceled.set(true); return null; }).when(call).cancel();
		when(call.isCanceled()).thenAnswer(invocation -> canceled.get());
		RevalApiService api = new RevalApiService(http, new Gson());
		Consumer<PointsResponse> success = mock(Consumer.class);
		Consumer<Exception> failure = mock(Consumer.class);
		api.fetchPoints(success, failure);
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		api.shutDown();
		verify(call).cancel();
		callback.getValue().onResponse(call, new Response.Builder()
			.request(new Request.Builder().url("https://example.invalid").build())
			.protocol(Protocol.HTTP_1_1).code(200).message("OK")
			.body(ResponseBody.create(null, "{\"status\":\"success\",\"data\":{}}")).build());
		callback.getValue().onFailure(call, new IOException("canceled"));
		verifyNoInteractions(success);
		verify(failure).accept(isA(java.util.concurrent.CancellationException.class));
		verifyNoMoreInteractions(failure);
		verify(http).newCall(any(Request.class));
		verifyNoMoreInteractions(http);
	}

	@Test public void callbacksRunWithoutHoldingTheApiServiceMonitor() throws IOException {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(call.timeout()).thenReturn(new okio.Timeout());
		when(http.newCall(any(Request.class))).thenReturn(call);
		RevalApiService api = new RevalApiService(http, new Gson());
		AtomicBoolean delivered = new AtomicBoolean();
		api.fetchPoints(response -> {
			assertFalse("A service lock in a callback can deadlock the game tick", Thread.holdsLock(api));
			delivered.set(true);
		}, error -> fail(error.toString()));
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(call).enqueue(callback.capture());
		callback.getValue().onResponse(call, new Response.Builder()
			.request(new Request.Builder().url("https://example.invalid").build())
			.protocol(Protocol.HTTP_1_1).code(200).message("OK")
			.body(ResponseBody.create(null, "{\"status\":\"success\",\"data\":{}}")).build());
		assertTrue(delivered.get());
	}
	@Test public void allSharedDefinitionsCoalesceAndBackOffAtTheApiBoundary() {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(http.newCall(any(Request.class))).thenReturn(call);
		RevalApiService api = new RevalApiService(http, new Gson());
		Runnable fetch = () -> {
			api.fetchPoints(response -> {}, error -> {});
			api.fetchAccount(123L, response -> {}, error -> {});
			api.fetchActiveTeams(response -> {}, error -> {});
			api.fetchAchievementDefinitions(response -> {}, error -> {});
			api.fetchDiaries(response -> {}, error -> {});
			api.fetchChallenges(response -> {}, error -> {});
			api.fetchLeaguesConfig(response -> {}, error -> {});
		};
		for (int i = 0; i < 1000; i++) fetch.run();
		ArgumentCaptor<Callback> callbacks = ArgumentCaptor.forClass(Callback.class);
		verify(call, times(7)).enqueue(callbacks.capture());
		for (Callback callback : callbacks.getAllValues()) callback.onFailure(call, new IOException("offline"));
		for (int i = 0; i < 1000; i++) fetch.run();
		verify(http, times(7)).newCall(any());
	}

}

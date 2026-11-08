package com.revalclan.api;

import com.google.gson.Gson;
import com.revalclan.api.points.PointsResponse;
import com.revalclan.api.events.EventsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.runelite.client.callback.ClientThread;
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
		RevalApiService api = new RevalApiService(http, new Gson(), mock(ClientThread.class));
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
		RevalApiService api = new RevalApiService(http, new Gson(), mock(ClientThread.class));
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
	@Test public void shutdownCancelsCompetitionArtworkAndCompletesItsConsumer() {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(http.newCall(any(Request.class))).thenReturn(call);
		RevalApiService api = new RevalApiService(http, new Gson(), mock(ClientThread.class));
		Consumer<java.awt.image.BufferedImage> success = mock(Consumer.class);
		Consumer<Exception> failure = mock(Consumer.class);
		api.fetchCompetitionArtwork("/race/banner.png", success, failure);
		api.shutDown();
		verify(call).cancel();
		verify(failure).accept(isA(java.util.concurrent.CancellationException.class));
		verifyNoInteractions(success);
	}

	@Test public void allSharedDefinitionsCoalesceAndBackOffAtTheApiBoundary() {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(http.newCall(any(Request.class))).thenReturn(call);
		RevalApiService api = new RevalApiService(http, new Gson(), mock(ClientThread.class));
		Runnable fetch = () -> {
			api.fetchPoints(response -> {}, error -> {});
			api.fetchAccount(123L, response -> {}, error -> {});
			api.fetchActiveTeams(response -> {}, error -> {});
			api.fetchDiaries(null, response -> {}, error -> {});
			api.fetchLeaguesConfig(response -> {}, error -> {});
		};
		for (int i = 0; i < 1000; i++) fetch.run();
		ArgumentCaptor<Callback> callbacks = ArgumentCaptor.forClass(Callback.class);
		verify(call, times(5)).enqueue(callbacks.capture());
		for (Callback callback : callbacks.getAllValues()) callback.onFailure(call, new IOException("offline"));
		for (int i = 0; i < 1000; i++) fetch.run();
		verify(http, times(5)).newCall(any());
	}

	@Test public void eventPublicationAndCompletionWaitForTheClientThread() throws Exception {
		EventsFixture fixture = new EventsFixture();
		fixture.api.addEventsListener(response -> {
			assertTrue("Event state must have one execution owner", fixture.runningClientTask);
			assertFalse(Thread.holdsLock(fixture.api));
		});
		PendingEvents pending = fixture.fetch();
		pending.respond("Current event");
		assertTrue(fixture.publications.isEmpty());
		assertTrue(fixture.successes.isEmpty());
		fixture.runClientTasks();
		assertEquals(List.of("Current event"), fixture.publications);
		assertEquals(1, fixture.successes.size());
		fixture.api.resetEventsSession();
		fixture.runClientTasks();
		assertEquals(List.of("Current event", "reset"), fixture.publications);
		assertTrue(fixture.failures.isEmpty());
	}

	@Test public void resetAheadOfAQueuedResultCancelsTheConsumerAndPreventsPublication() throws Exception {
		EventsFixture fixture = new EventsFixture();
		PendingEvents pending = fixture.fetch();
		fixture.api.resetEventsSession();
		pending.respond("Previous account");
		fixture.runClientTasks();
		assertEquals(List.of("reset"), fixture.publications);
		assertTrue(fixture.successes.isEmpty());
		assertEquals(1, fixture.failures.size());
		assertTrue(fixture.failures.get(0) instanceof CancellationException);
		pending.callback.onFailure(pending.call, new IOException("late failure"));
		fixture.runClientTasks();
		assertEquals(1, fixture.failures.size());
	}

	@Test public void overlappingFetchesCancelOldResultsAndKeepOnlyTheNewestPublication() throws Exception {
		EventsFixture fixture = new EventsFixture();
		PendingEvents first = fixture.fetch();
		PendingEvents second = fixture.fetch();
		PendingEvents newest = fixture.fetch();
		verify(first.call).cancel();
		verify(second.call).cancel();
		first.respond("First event");
		second.respond("Second event");
		newest.respond("Newest event");
		fixture.runClientTasks();
		assertEquals(List.of("Newest event"), fixture.publications);
		assertEquals(1, fixture.successes.size());
		assertEquals(2, fixture.failures.size());
		assertTrue(fixture.failures.stream().allMatch(CancellationException.class::isInstance));
	}

	@Test public void aSupersededQueuedNetworkErrorCompletesAsCancellationOnly() {
		EventsFixture fixture = new EventsFixture();
		PendingEvents old = fixture.fetch();
		fixture.api.fetchEvents(fixture.successes::add, fixture.failures::add);
		old.callback.onFailure(old.call, new IOException("old network failure"));
		fixture.runClientTasks();
		assertEquals(1, fixture.failures.size());
		assertTrue(fixture.failures.get(0) instanceof CancellationException);
		assertTrue(fixture.publications.isEmpty());
	}

	@Test public void shutdownDiscardsQueuedSuccessAndRestartCannotReactivateItsLifetime() throws Exception {
		EventsFixture fixture = new EventsFixture();
		PendingEvents old = fixture.fetch();
		old.respond("Previous plugin lifetime");
		fixture.api.shutDown();
		fixture.api.startUp();
		fixture.runClientTasks();
		assertEquals(List.of("reset"), fixture.publications);
		assertTrue(fixture.successes.isEmpty());
		assertEquals(1, fixture.failures.size());
		assertTrue(fixture.failures.get(0) instanceof CancellationException);
		PendingEvents current = fixture.fetch();
		current.respond("Current plugin lifetime");
		fixture.runClientTasks();
		assertEquals(List.of("reset", "Current plugin lifetime"), fixture.publications);
		assertEquals(1, fixture.successes.size());
	}

	@Test public void aFetchQueuedBeforeShutdownCannotStartInTheRestartedPlugin() {
		EventsFixture fixture = new EventsFixture();
		fixture.api.fetchEvents(fixture.successes::add, fixture.failures::add);
		fixture.api.shutDown();
		fixture.api.startUp();
		fixture.runClientTasks();
		verifyNoInteractions(fixture.http);
		assertEquals(1, fixture.failures.size());
		assertTrue(fixture.failures.get(0) instanceof CancellationException);
		assertTrue(fixture.successes.isEmpty());
	}

	@Test public void anOldFetchDelayedBeforeEnqueueCannotCancelTheRestartedPluginsFetch() throws Exception {
		EventsFixture fixture = new EventsFixture();
		fixture.api.fetchEvents(fixture.successes::add, fixture.failures::add);
		// Hold the captured old-lifetime operation at the client-thread enqueue boundary.
		Runnable delayedFetch = fixture.tasks.remove();
		fixture.api.shutDown();
		fixture.api.startUp();
		PendingEvents current = fixture.fetch();
		fixture.tasks.add(delayedFetch);
		fixture.runClientTasks();
		verify(current.call, never()).cancel();
		current.respond("Current plugin lifetime");
		fixture.runClientTasks();
		assertEquals(List.of("reset", "Current plugin lifetime"), fixture.publications);
		assertEquals(1, fixture.successes.size());
		assertEquals(1, fixture.failures.size());
		assertTrue(fixture.failures.get(0) instanceof CancellationException);
	}

	@Test public void anOldResetDelayedBeforeEnqueueCannotClearTheRestartedPluginsState() throws Exception {
		EventsFixture fixture = new EventsFixture();
		fixture.api.resetEventsSession();
		Runnable delayedReset = fixture.tasks.remove();
		fixture.api.shutDown();
		fixture.api.startUp();
		fixture.fetch().respond("Current plugin lifetime");
		fixture.runClientTasks();
		fixture.tasks.add(delayedReset);
		fixture.runClientTasks();
		assertEquals(List.of("reset", "Current plugin lifetime"), fixture.publications);
		assertEquals(1, fixture.successes.size());
		assertTrue(fixture.failures.isEmpty());
	}

	@Test public void currentEventErrorsCompleteOnceOnTheClientThreadAndPreservePublishedState() throws Exception {
		EventsFixture fixture = new EventsFixture();
		fixture.fetch().respond("Last good response");
		fixture.runClientTasks();
		PendingEvents failed = fixture.fetch();
		IOException error = new IOException("offline");
		failed.callback.onFailure(failed.call, error);
		assertTrue(fixture.failures.isEmpty());
		fixture.runClientTasks();
		assertEquals(List.of("Last good response"), fixture.publications);
		assertEquals(List.of(error), fixture.failures);
		failed.callback.onFailure(failed.call, new IOException("duplicate terminal callback"));
		fixture.runClientTasks();
		assertEquals(1, fixture.failures.size());
	}

	@Test public void aThrowingEventListenerCompletesTheConsumerWithItsFailureExactlyOnce() throws Exception {
		EventsFixture fixture = new EventsFixture();
		IllegalArgumentException invalidEvent = new IllegalArgumentException("Malformed registration");
		fixture.api.addEventsListener(response -> {
			if (response.getData() != null) throw invalidEvent;
		});
		fixture.fetch().respond("Malformed event");
		fixture.runClientTasks();
		assertTrue(fixture.successes.isEmpty());
		assertEquals(List.of(invalidEvent), fixture.failures);
		fixture.api.resetEventsSession();
		fixture.runClientTasks();
		assertEquals(1, fixture.failures.size());
	}

	private static final class EventsFixture {
		final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
		final OkHttpClient http = mock(OkHttpClient.class);
		final RevalApiService api;
		final List<String> publications = new ArrayList<>();
		final List<EventsResponse> successes = new ArrayList<>();
		final List<Exception> failures = new ArrayList<>();
		boolean runningClientTask;

		EventsFixture() {
			ClientThread clientThread = mock(ClientThread.class);
			doAnswer(invocation -> { tasks.add(invocation.getArgument(0)); return null; })
				.when(clientThread).invokeLater(any(Runnable.class));
			api = new RevalApiService(http, new Gson(), clientThread);
			api.addEventsListener(response -> publications.add(response.getData() == null ? "reset"
				: response.getData().getEvents().get(0).getName()));
		}

		PendingEvents fetch() {
			Call call = mock(Call.class);
			when(http.newCall(any(Request.class))).thenReturn(call);
			api.fetchEvents(response -> {
				assertTrue("Success must run on the client thread", runningClientTask);
				assertFalse(Thread.holdsLock(api));
				successes.add(response);
			}, error -> {
				assertTrue("Errors must run on the client thread", runningClientTask);
				assertFalse(Thread.holdsLock(api));
				failures.add(error);
			});
			runClientTasks();
			ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
			verify(call).enqueue(callback.capture());
			return new PendingEvents(call, callback.getValue());
		}

		void runClientTasks() {
			Runnable task;
			while ((task = tasks.poll()) != null) {
				runningClientTask = true;
				try { task.run(); }
				finally { runningClientTask = false; }
			}
		}
	}

	private static final class PendingEvents {
		final Call call;
		final Callback callback;
		PendingEvents(Call call, Callback callback) { this.call = call; this.callback = callback; }
		void respond(String eventName) throws IOException {
			callback.onResponse(call, new Response.Builder()
				.request(new Request.Builder().url("https://example.invalid").build())
				.protocol(Protocol.HTTP_1_1).code(200).message("OK")
				.body(ResponseBody.create(null, "{\"status\":\"success\",\"data\":{\"events\":[{\"name\":\"" + eventName + "\"}]}}"))
				.build());
		}
	}

}

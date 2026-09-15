package com.revalclan.events;

import com.google.gson.Gson;
import com.revalclan.api.RevalApiService;
import okhttp3.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class RegistrationMarksTest {
	private final BlockingQueue<CompletableFuture<String>> requests = new LinkedBlockingQueue<>();
	private OkHttpClient http;
	private RevalApiService api;
	private RegistrationMarks marks;
	private static final String EVENTS = "{\"status\":\"success\",\"data\":{\"events\":[{\"name\":\"Bingo\",\"status\":\"scheduled\",\"registrations\":[{\"osrsNickname\":\"Some Player\",\"status\":\"registered\"},{\"osrsNickname\":\"Pending\",\"status\":\"pending\"}]},{\"name\":\"Old\",\"status\":\"completed\",\"registrations\":[{\"osrsNickname\":\"Old Player\",\"status\":\"registered\"}]}]}}";

	@Before public void setup() {
		http = new OkHttpClient.Builder().addInterceptor(chain -> {
			CompletableFuture<String> response = new CompletableFuture<>();
			requests.add(response);
			try {
				return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
					.body(ResponseBody.create(MediaType.parse("application/json"), response.get(5, TimeUnit.SECONDS))).build();
			} catch (Exception e) { throw new IOException(e); }
		}).build();
		api = new RevalApiService(http, new Gson());
		marks = new RegistrationMarks(null, api);
	}

	@After public void cleanup() {
		http.dispatcher().cancelAll();
		http.dispatcher().executorService().shutdownNow();
		http.connectionPool().evictAll();
	}

	@Test public void snapshotSurvivesCacheClearInflightRefreshAndFailure() throws Exception {
		CountDownLatch first = fetch();
		respond(EVENTS);
		assertTrue(first.await(5, TimeUnit.SECONDS));
		assertEquals(marks.getRegistrations().toString(), "Bingo", marks.getRegistrations().get(net.runelite.client.util.Text.standardize("Some Player")));
		assertEquals(1, marks.getRegistrations().size());
		api.clearCache();
		assertEquals(1, marks.getRegistrations().size());
		CountDownLatch failed = fetch();
		CompletableFuture<String> response = requests.poll(5, TimeUnit.SECONDS);
		assertNotNull(response);
		assertEquals(1, marks.getRegistrations().size());
		response.complete("{\"status\":\"error\",\"message\":\"Unavailable\"}");
		assertTrue(failed.await(5, TimeUnit.SECONDS));
		assertEquals(1, marks.getRegistrations().size());
		CountDownLatch empty = fetch();
		respond("{\"status\":\"success\",\"data\":{\"events\":[]}}");
		assertTrue(empty.await(5, TimeUnit.SECONDS));
		assertTrue(marks.getRegistrations().isEmpty());
	}

	@Test public void logoutRejectsLateResponseAndClearsMarks() throws Exception {
		CountDownLatch first = fetch(); respond(EVENTS);
		assertTrue(first.await(5, TimeUnit.SECONDS));
		assertEquals(1, marks.getRegistrations().size());
		fetch();
		CompletableFuture<String> late = requests.poll(5, TimeUnit.SECONDS);
		assertNotNull(late);
		api.resetEventsSession();
		assertTrue(marks.getRegistrations().isEmpty());
		CountDownLatch idle = new CountDownLatch(1);
		http.dispatcher().setIdleCallback(idle::countDown);
		late.complete(EVENTS);
		assertTrue(idle.await(5, TimeUnit.SECONDS));
		assertTrue(marks.getRegistrations().isEmpty());
	}

	private CountDownLatch fetch() {
		CountDownLatch done = new CountDownLatch(1);
		api.fetchEvents(response -> done.countDown(), error -> done.countDown());
		return done;
	}
	private void respond(String json) throws Exception {
		CompletableFuture<String> response = requests.poll(5, TimeUnit.SECONDS);
		assertNotNull(response);
		response.complete(json);
	}
}

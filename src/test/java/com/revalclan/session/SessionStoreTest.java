package com.revalclan.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SessionStoreTest {
	@Rule public TemporaryFolder temporary = new TemporaryFolder();

	@Test public void writingDoesNotTouchDiskOnTheCaller() throws Exception {
		Queue<Runnable> tasks = new ArrayDeque<>();
		File directory = temporary.newFolder();
		SessionStore store = new SessionStore(new Gson(), tasks::add, directory);
		SessionStore.PersistedSession session = session("test-session");
		store.write(session);
		assertFalse("Game ticks must not write session files", new File(directory, "test-session.json").exists());
		while (!tasks.isEmpty()) tasks.remove().run();
		assertTrue(new File(directory, "test-session.json").exists());
	}

	@Test public void acknowledgementCannotOvertakeAQueuedWrite() throws Exception {
		Queue<Runnable> tasks = new ArrayDeque<>();
		File directory = temporary.newFolder();
		SessionStore store = new SessionStore(new Gson(), tasks::add, directory);
		AtomicReference<List<SessionStore.PersistedSession>> recovered = new AtomicReference<>();
		store.write(session("acked"));
		store.delete("acked");
		store.readAll().thenAccept(recovered::set);
		assertNull(recovered.get());
		assertEquals("Only one disk worker may run at once", 1, tasks.size());
		while (!tasks.isEmpty()) tasks.remove().run();
		assertTrue(recovered.get().isEmpty());
		assertFalse(new File(directory, "acked.json").exists());
	}

	@Test public void recoveryObservesTheLatestQueuedSnapshot() throws Exception {
		Queue<Runnable> tasks = new ArrayDeque<>();
		SessionStore store = new SessionStore(new Gson(), tasks::add, temporary.newFolder());
		SessionStore.PersistedSession first = session("current");
		first.summary.addProperty("totalLootValue", 10);
		SessionStore.PersistedSession last = session("current");
		last.summary.addProperty("totalLootValue", 20);
		AtomicReference<List<SessionStore.PersistedSession>> recovered = new AtomicReference<>();
		store.write(first);
		store.write(last);
		store.readAll().thenAccept(recovered::set);
		while (!tasks.isEmpty()) tasks.remove().run();
		assertEquals(20, recovered.get().get(0).summary.get("totalLootValue").getAsInt());
	}

	@Test public void repeatedSnapshotsSerializeOnlyTheLatestAndFlushWaitsForDisk() throws Exception {
		Queue<Runnable> tasks = new ArrayDeque<>();
		java.util.concurrent.atomic.AtomicInteger serializations = new java.util.concurrent.atomic.AtomicInteger();
		Gson gson = new com.google.gson.GsonBuilder().registerTypeAdapter(SessionStore.PersistedSession.class,
			(com.google.gson.JsonSerializer<SessionStore.PersistedSession>) (session, type, context) -> {
				serializations.incrementAndGet();
				return new Gson().toJsonTree(session);
			}).create();
		SessionStore store = new SessionStore(gson, tasks::add, temporary.newFolder());
		for (int i = 0; i < 1000; i++) store.write(session("same"));
		java.util.concurrent.CompletableFuture<Void> flushed = store.flush();
		assertFalse(flushed.isDone());
		assertEquals(1, tasks.size());
		while (!tasks.isEmpty()) tasks.remove().run();
		flushed.join();
		assertEquals(1, serializations.get());
	}

	@Test public void rejectedWorkerDoesNotThrowFromTicksAndCanBeRetried() throws Exception {
		java.util.concurrent.Executor executor = org.mockito.Mockito.mock(java.util.concurrent.Executor.class);
		org.mockito.Mockito.doThrow(new java.util.concurrent.RejectedExecutionException()).when(executor).execute(org.mockito.ArgumentMatchers.any());
		SessionStore store = new SessionStore(new Gson(), executor, temporary.newFolder());
		store.write(session("retry"));
		assertTrue(store.flush().isCompletedExceptionally());
		org.mockito.Mockito.doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(executor).execute(org.mockito.ArgumentMatchers.any());
		store.flush().join();
		assertEquals(1, store.readAll().join().size());
	}

	static SessionStore.PersistedSession session(String id) {
		SessionStore.PersistedSession session = new SessionStore.PersistedSession();
		session.summary = new JsonObject();
		session.summary.addProperty("sessionId", id);
		session.summary.addProperty("startedAt", System.currentTimeMillis());
		return session;
	}
}

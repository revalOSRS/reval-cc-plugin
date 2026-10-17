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
		store.readAll(recovered::set);
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
		store.readAll(recovered::set);
		while (!tasks.isEmpty()) tasks.remove().run();
		assertEquals(20, recovered.get().get(0).summary.get("totalLootValue").getAsInt());
	}

	static SessionStore.PersistedSession session(String id) {
		SessionStore.PersistedSession session = new SessionStore.PersistedSession();
		session.summary = new JsonObject();
		session.summary.addProperty("sessionId", id);
		session.summary.addProperty("startedAt", System.currentTimeMillis());
		return session;
	}
}

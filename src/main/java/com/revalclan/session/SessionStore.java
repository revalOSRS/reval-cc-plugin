package com.revalclan.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Crash-safe persistence for in-progress sessions: one JSON file per session
 * under {@code ~/.runelite/reval-clan/sessions/}. Disk operations run in order
 * on RuneLite's executor, never on the game or Swing thread.
 *
 * Why not the RuneLite config store: it buffers in memory and only reaches
 * disk on a 5-minute timer or a graceful exit. Verified 2026-09-10 — three
 * consecutive hard kills lost three sessions and resurrected a deleted one.
 * Completed writes survive a client crash. A snapshot still waiting for its
 * worker when the process is killed has not reached disk yet.
 */
@Slf4j
@Singleton
public class SessionStore {
	private static final String DIR_NAME = "reval-clan/sessions";
	/** Undelivered files older than this are dropped at recovery */
	private static final long MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000;

	private final Gson gson;
	private final Executor executor;
	private final File directory;
	// One pending value per session; a delete tombstone supersedes queued snapshots.
	private final Map<String, Optional<PersistedSession>> pending = new LinkedHashMap<>();
	private boolean scheduled;
	private CompletableFuture<Void> flushed = CompletableFuture.completedFuture(null);

	@Inject
	public SessionStore(Gson gson, ScheduledExecutorService executor) {
		this(gson, executor, new File(RuneLite.RUNELITE_DIR, DIR_NAME));
	}

	SessionStore(Gson gson, Executor executor, File directory) {
		this.gson = gson;
		this.executor = executor;
		this.directory = directory;
	}

	private void update(String id, Optional<PersistedSession> value) {
		if (id == null) return;
		synchronized (this) { pending.put(id, value); }
		flush();
	}

	/**
	 * Completes after pending snapshots reach disk. ClientShutdown registers this
	 * future with RuneLite's graceful-exit wait; a hard kill can still lose queued work.
	 * RuneLite's shared executor serializes disk access without a second task queue.
	 */
	public CompletableFuture<Void> flush() {
		CompletableFuture<Void> result;
		synchronized (this) {
			if (scheduled || pending.isEmpty()) return flushed;
			scheduled = true;
			result = flushed = new CompletableFuture<>();
		}
		try { executor.execute(() -> drain(result)); }
		catch (RuntimeException error) {
			synchronized (this) { scheduled = false; }
			log.warn("Could not schedule session persistence", error);
			result.completeExceptionally(error);
		}
		return result;
	}

	private void drain(CompletableFuture<Void> result) {
		Exception failure = null;
		while (true) {
			Map<String, Optional<PersistedSession>> batch;
			synchronized (this) {
				if (pending.isEmpty()) { scheduled = false; break; }
				batch = new LinkedHashMap<>(pending);
				pending.clear();
			}
			for (Map.Entry<String, Optional<PersistedSession>> entry : batch.entrySet()) {
				try {
					if (entry.getValue().isPresent()) writeNow(entry.getValue().get());
					else deleteNow(entry.getKey());
				} catch (IOException | RuntimeException error) {
					failure = error;
					log.warn("Session persistence failed for {}", entry.getKey(), error);
				}
			}
		}
		if (failure == null) result.complete(null); else result.completeExceptionally(failure);
	}

	/** Persisted shape. {@code summary} stays a JsonObject so numbers survive exactly. */
	public static class PersistedSession {
		public long accountHash;
		public String username;
		public int world;
		public List<String> worldFlags;
		/** Membership was proven while this session was recorded — safe to send at startup */
		public boolean member;
		public JsonObject summary;

		public String sessionId() {
			try {
				return summary.get("sessionId").getAsString();
			} catch (Exception e) {
				return null;
			}
		}

		public long startedAt() {
			try {
				return summary.get("startedAt").getAsLong();
			} catch (Exception e) {
				return 0;
			}
		}
	}

	private File dir() {
		File d = directory;
		if (!d.exists() && !d.mkdirs()) {
			log.warn("Could not create session store directory {}", d);
		}
		return d;
	}

	private File fileFor(String sessionId) {
		return new File(dir(), sessionId + ".json");
	}

	/** Atomic write: temp file then move, so a crash mid-write never leaves a torn file. */
	public void write(PersistedSession session) {
		update(session.sessionId(), Optional.of(session));
	}

	private void writeNow(PersistedSession session) throws IOException {
		String id = session.sessionId();
		if (id == null) return;
		Path target = fileFor(id).toPath();
		Path tmp = target.resolveSibling(id + ".tmp");
		try {
			Files.write(tmp, gson.toJson(session).getBytes(StandardCharsets.UTF_8));
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicUnsupported) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally { Files.deleteIfExists(tmp); }
	}

	public void delete(String sessionId) {
		if (sessionId == null) return;
		update(sessionId, Optional.empty());
	}

	private void deleteNow(String sessionId) throws IOException {
		Files.deleteIfExists(fileFor(sessionId).toPath());
	}

	/** Reads on the shared single worker after preceding writes and tombstones. */
	public CompletableFuture<List<PersistedSession>> readAll() {
		CompletableFuture<List<PersistedSession>> result = new CompletableFuture<>();
		flush().whenComplete((ignored, failure) -> {
			if (failure != null) { result.completeExceptionally(failure); return; }
			try {
				executor.execute(() -> {
					if (result.isDone()) return;
					try { result.complete(readAllNow()); }
					catch (RuntimeException error) { result.completeExceptionally(error); }
				});
			} catch (RuntimeException error) { result.completeExceptionally(error); }
		});
		return result;
	}

	/** Every readable persisted session, newest first. Unreadable or expired files are removed. */
	private List<PersistedSession> readAllNow() {
		List<PersistedSession> out = new ArrayList<>();
		File[] files = dir().listFiles((d, name) -> name.endsWith(".json"));
		if (files == null) return out;
		long now = System.currentTimeMillis();
		for (File f : files) {
			PersistedSession session = null;
			try {
				session = gson.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), PersistedSession.class);
			} catch (Exception ignored) {}
			if (session == null || session.summary == null || session.sessionId() == null
				|| now - session.startedAt() > MAX_AGE_MS) {
				if (!f.delete()) log.warn("Could not remove stale session file {}", f);
				continue;
			}
			out.add(session);
		}
		out.sort((a, b) -> Long.compare(b.startedAt(), a.startedAt()));
		return out;
	}
}

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
import java.util.List;

/**
 * Crash-safe persistence for in-progress sessions: one JSON file per session
 * under {@code ~/.runelite/reval-clan/sessions/}, written synchronously.
 *
 * Why not the RuneLite config store: it buffers in memory and only reaches
 * disk on a 5-minute timer or a graceful exit. Verified 2026-09-10 — three
 * consecutive hard kills lost three sessions and resurrected a deleted one.
 * A file written on every persist survives everything short of power loss.
 */
@Slf4j
@Singleton
public class SessionStore {
	private static final String DIR_NAME = "reval-clan/sessions";
	/** Undelivered files older than this are dropped at recovery */
	private static final long MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000;

	@Inject private Gson gson;

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
		File d = new File(RuneLite.RUNELITE_DIR, DIR_NAME);
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
		} catch (IOException e) {
			log.warn("Failed to persist session {}: {}", id, e.getMessage());
		}
	}

	public void delete(String sessionId) {
		if (sessionId == null) return;
		try {
			Files.deleteIfExists(fileFor(sessionId).toPath());
		} catch (IOException e) {
			log.warn("Failed to delete persisted session {}: {}", sessionId, e.getMessage());
		}
	}

	/** Every readable persisted session, newest first. Unreadable or expired files are removed. */
	public List<PersistedSession> readAll() {
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

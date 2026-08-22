package com.revalclan.session;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.revalclan.util.WebhookService;
import com.revalclan.util.Worlds;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accumulates the play session client-side and delivers ONE summary: attached
 * to LOGOUT on a clean logout, or replayed as a recovered SESSION_SUMMARY on
 * next startup after a crash/X-out (periodically persisted to the config
 * store). Server dedupes on sessionId, so a replay can never double-store.
 */
@Slf4j
@Singleton
public class SessionTracker {
	private static final String CONFIG_GROUP = "revalclan";
	/** One config entry per session: activeSession_<sessionId> */
	private static final String CONFIG_KEY_PREFIX = "activeSession_";
	private static final String LEGACY_CONFIG_KEY = "activeSessionJson";

	/** Persist at most once per this many ticks (~30s), and only when dirty */
	private static final int PERSIST_INTERVAL_TICKS = 50;

	/** Oldest persisted sessions beyond this are dropped at recovery */
	private static final int MAX_PERSISTED_SESSIONS = 5;

	/** Malfunction guard — totals stay exact even past the cap */
	private static final int MAX_LOOT_ENTRIES = 5000;

	/** Malfunction guard (deaths) */
	private static final int MAX_LIST_ENTRIES = 1000;

	@Inject private Client client;
	@Inject private ConfigManager configManager;
	@Inject private Gson gson;
	@Inject private WebhookService webhookService;

	private boolean active = false;
	private boolean dirty = false;
	private int ticksSincePersist = 0;

	private String sessionId;
	private long startedAtMs;
	private long lastUpdateMs;
	private String username;
	private long accountHash;
	private int world;
	private List<String> worldFlags;
	private Map<String, Object> startSnapshot;
	private Map<String, Object> endSnapshot;
	private final Map<String, Integer> kills = new HashMap<>();
	private final Map<String, Integer> clues = new HashMap<>();
	/** key: itemId + "|" + source → aggregated loot entry */
	private final Map<String, Map<String, Object>> loot = new LinkedHashMap<>();
	private long totalLootValue = 0;
	private final List<Map<String, Object>> deaths = new ArrayList<>();

	/** Config-store shape; summary stays a JsonObject so numbers survive the round-trip exactly. */
	private static class PersistedSession {
		long accountHash;
		String username;
		Integer world;
		JsonElement worldFlags;
		JsonObject summary;
	}

	/**
	 * Start a new session (client thread, logged in). Any world — the backend
	 * gates on the worldFlags captured here.
	 */
	public void startSession() {
		resetState();
		sessionId = UUID.randomUUID().toString();
		startedAtMs = System.currentTimeMillis();
		lastUpdateMs = startedAtMs;
		username = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";
		accountHash = client.getAccountHash();
		world = client.getWorld();
		worldFlags = Worlds.flagNames(client);
		startSnapshot = buildSnapshot();
		endSnapshot = startSnapshot;
		active = true;
		dirty = true;
		log.debug("Session started: {}", sessionId);
	}

	/**
	 * Finalize (clean logout) and return the summary for the LOGOUT payload, or
	 * null. The persisted copy is kept until confirmDelivered — a client closed
	 * mid-send replays it next startup.
	 */
	public Map<String, Object> finalizeSession() {
		if (!active) return null;

		touch();
		Map<String, Object> summary = buildSummary("logout", lastUpdateMs);

		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_PREFIX + sessionId,
			gson.toJson(persistedEnvelope(summary)));

		reset();
		return summary;
	}

	/** Server confirmed receipt — drop that session's persisted copy. */
	public void confirmDelivered(String deliveredSessionId) {
		if (deliveredSessionId == null) return;
		try {
			configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY_PREFIX + deliveredSessionId);
		} catch (Exception ignored) {}
	}

	/** Replay every persisted-but-unconfirmed session as a recovered SESSION_SUMMARY. */
	public void recoverPersistedSession() {
		try {
			configManager.unsetConfiguration(CONFIG_GROUP, LEGACY_CONFIG_KEY);

			List<String> keys = new ArrayList<>();
			for (String fullKey : configManager.getConfigurationKeys(CONFIG_GROUP + "." + CONFIG_KEY_PREFIX)) {
				keys.add(fullKey.substring(CONFIG_GROUP.length() + 1));
			}
			if (keys.isEmpty()) return;

			// Newest first (by persisted startedAt); drop anything beyond the cap
			keys.sort(Comparator.comparingLong(this::persistedStartedAt).reversed());
			for (String key : keys.subList(Math.min(MAX_PERSISTED_SESSIONS, keys.size()), keys.size())) {
				configManager.unsetConfiguration(CONFIG_GROUP, key);
			}

			for (String key : keys.subList(0, Math.min(MAX_PERSISTED_SESSIONS, keys.size()))) {
				replayPersisted(key);
			}
		} catch (Exception e) {
			log.warn("Failed to recover persisted sessions: {}", e.getMessage());
		}
	}

	private long persistedStartedAt(String key) {
		try {
			PersistedSession p = gson.fromJson(configManager.getConfiguration(CONFIG_GROUP, key), PersistedSession.class);
			return p.summary.get("startedAt").getAsLong();
		} catch (Exception e) {
			return 0;
		}
	}

	// The only event built outside BaseNotifier — no live client exists at
	// startup, so the envelope is reassembled from the persisted copy.
	private void replayPersisted(String key) {
		try {
			PersistedSession persisted = gson.fromJson(
				configManager.getConfiguration(CONFIG_GROUP, key), PersistedSession.class);
			if (persisted == null || persisted.summary == null) {
				configManager.unsetConfiguration(CONFIG_GROUP, key);
				return;
			}

			Map<String, Object> payload = new HashMap<>();
			payload.put("eventType", "SESSION_SUMMARY");
			payload.put("eventTimestamp", System.currentTimeMillis());
			payload.put("accountHash", persisted.accountHash);
			payload.put("username", persisted.username);
			// World flags drive the server's world gate (keeps leagues sessions out)
			if (persisted.world != null) payload.put("world", persisted.world);
			if (persisted.worldFlags != null) payload.put("worldFlags", persisted.worldFlags);
			payload.put("sessionSummary", persisted.summary);

			String recoveredSessionId = persisted.summary.has("sessionId")
				? persisted.summary.get("sessionId").getAsString()
				: null;

			// Clear only on server confirm; otherwise retry next startup
			webhookService.sendDataAsync(payload, response -> confirmDelivered(recoveredSessionId));
			log.info("Recovered unfinished session, replayed as SESSION_SUMMARY");
		} catch (Exception e) {
			log.warn("Failed to replay persisted session: {}", e.getMessage());
			// Unparseable copy is useless for recovery anyway
			try {
				configManager.unsetConfiguration(CONFIG_GROUP, key);
			} catch (Exception ignored) {}
		}
	}

	/** Throttled local persistence (client thread). */
	public void onGameTick() {
		if (!active) return;
		// Pure skilling fires no accumulator event — treat XP movement as dirtiness
		if (xpChangedSinceSnapshot()) {
			touch();
			dirty = true;
		}
		ticksSincePersist++;
		if (dirty && ticksSincePersist >= PERSIST_INTERVAL_TICKS) {
			ticksSincePersist = 0;
			dirty = false;
			try {
				touch();
				persist();
			} catch (Exception e) {
				log.warn("Failed to persist session state: {}", e.getMessage());
			}
		}
	}

	public void reset() {
		active = false;
		resetState();
	}

	public void addKill(String npcName) {
		if (!active || npcName == null || npcName.isEmpty()) return;
		kills.merge(npcName, 1, Integer::sum);
		dirty = true;
	}

	public void addLoot(String source, int itemId, String itemName, int quantity, long gePriceEach) {
		if (!active) return;
		totalLootValue += gePriceEach * quantity;

		String key = itemId + "|" + source;
		Map<String, Object> entry = loot.get(key);
		if (entry != null) {
			entry.put("quantity", ((Number) entry.get("quantity")).intValue() + quantity);
		} else if (loot.size() < MAX_LOOT_ENTRIES) {
			entry = new HashMap<>();
			entry.put("itemId", itemId);
			entry.put("itemName", itemName);
			entry.put("quantity", quantity);
			entry.put("gePrice", gePriceEach);
			entry.put("source", source);
			loot.put(key, entry);
		}
		dirty = true;
	}

	public void addClue(String tier) {
		if (!active || tier == null || tier.isEmpty()) return;
		clues.merge(tier.toLowerCase(), 1, Integer::sum);
		dirty = true;
	}

	public void addDeath(String killedBy, long gpLost) {
		if (!active || deaths.size() >= MAX_LIST_ENTRIES) return;
		Map<String, Object> death = new HashMap<>();
		death.put("killedBy", killedBy);
		death.put("gpLost", gpLost);
		if (client.getLocalPlayer() != null) {
			net.runelite.api.coords.WorldPoint wp = client.getLocalPlayer().getWorldLocation();
			death.put("location", wp.getX() + "," + wp.getY() + "," + wp.getPlane());
		}
		death.put("timestamp", System.currentTimeMillis());
		deaths.add(death);
		dirty = true;
	}

	private void resetState() {
		sessionId = null;
		startedAtMs = 0;
		lastUpdateMs = 0;
		username = null;
		accountHash = 0;
		world = 0;
		worldFlags = null;
		startSnapshot = null;
		endSnapshot = null;
		kills.clear();
		clues.clear();
		loot.clear();
		totalLootValue = 0;
		deaths.clear();
		dirty = false;
		ticksSincePersist = 0;
	}

	/** Refresh the end snapshot and last-update time */
	private void touch() {
		refreshEndSnapshot();
		lastUpdateMs = System.currentTimeMillis();
	}

	/** Rebuild the end snapshot if still logged in */
	private void refreshEndSnapshot() {
		if (client.getGameState() == GameState.LOGGED_IN) {
			Map<String, Object> snapshot = buildSnapshot();
			if (snapshot != null) {
				endSnapshot = snapshot;
			}
		}
	}

	/** Has overall XP moved since the last end snapshot? */
	private boolean xpChangedSinceSnapshot() {
		if (client.getGameState() != GameState.LOGGED_IN || endSnapshot == null) return false;
		Object totalXp = endSnapshot.get("totalXp");
		return totalXp instanceof Number && ((Number) totalXp).longValue() != client.getOverallExperience();
	}

	/**
	 * Snapshot shape matches the backend's PlayerSnapshot:
	 * { skills: {name: {level, xp}}, totalLevel, totalXp, combatLevel, world }
	 */
	private Map<String, Object> buildSnapshot() {
		try {
			Map<String, Object> snapshot = new HashMap<>();
			Map<String, Map<String, Object>> skills = new HashMap<>();
			for (Skill skill : Skill.values()) {
				Map<String, Object> skillData = new HashMap<>();
				skillData.put("level", client.getRealSkillLevel(skill));
				skillData.put("xp", client.getSkillExperience(skill));
				skills.put(skill.getName().toLowerCase(), skillData);
			}
			snapshot.put("skills", skills);
			snapshot.put("totalLevel", client.getTotalLevel());
			snapshot.put("totalXp", client.getOverallExperience());
			if (client.getLocalPlayer() != null) {
				snapshot.put("combatLevel", client.getLocalPlayer().getCombatLevel());
			}
			snapshot.put("world", client.getWorld());
			return snapshot;
		} catch (Exception e) {
			return null;
		}
	}

	private Map<String, Object> buildSummary(String endReason, long endedAtMs) {
		Map<String, Object> summary = new HashMap<>();
		summary.put("sessionId", sessionId);
		summary.put("startedAt", startedAtMs);
		summary.put("endedAt", endedAtMs);
		summary.put("endReason", endReason);
		summary.put("startSnapshot", startSnapshot);
		summary.put("endSnapshot", endSnapshot);
		summary.put("kills", new HashMap<>(kills));
		summary.put("clues", new HashMap<>(clues));
		summary.put("totalLootValue", totalLootValue);
		summary.put("lootItems", new ArrayList<>(loot.values()));
		summary.put("deaths", new ArrayList<>(deaths));
		return summary;
	}

	private void persist() {
		// Persisted summaries replay as "recovered"; endedAt = last local update we saw
		Map<String, Object> persisted = persistedEnvelope(buildSummary("recovered", lastUpdateMs));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_PREFIX + sessionId, gson.toJson(persisted));
	}

	/** Envelope written to the config store: identity + world context + summary */
	private Map<String, Object> persistedEnvelope(Map<String, Object> summary) {
		Map<String, Object> persisted = new HashMap<>();
		persisted.put("accountHash", accountHash);
		persisted.put("username", username);
		persisted.put("world", world);
		persisted.put("worldFlags", worldFlags);
		persisted.put("summary", summary);
		return persisted;
	}
}

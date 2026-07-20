package com.revalclan.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.util.WebhookService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accumulates the whole play session client-side (kills, loot, clues, pets,
 * deaths, collection log slots, start/end skill snapshots) and delivers it to
 * the backend as ONE session summary:
 *
 *   - attached to the LOGOUT event on a clean logout, or
 *   - replayed as a standalone SESSION_SUMMARY event ("recovered") on the next
 *     plugin startup when the client crashed / was X'd out before LOGOUT fired.
 *
 * This replaces per-event server-side session tracking entirely — the backend
 * receives one write per session instead of one per kill/drop/clue.
 *
 * Crash resilience: the accumulator is periodically serialized into the RuneLite
 * config store (local disk, cheap, throttled to once per PERSIST_INTERVAL_TICKS
 * and only when dirty). recoverPersistedSession() on startup replays anything
 * left behind. The summary is idempotent server-side on {@code sessionId}, so a
 * replay racing a delivered LOGOUT can never double-store.
 */
@Slf4j
@Singleton
public class SessionTracker {
	private static final String CONFIG_GROUP = "revalclan";
	private static final String CONFIG_KEY = "activeSessionJson";

	/** Persist locally at most once per this many game ticks (~60s) and only when dirty */
	private static final int PERSIST_INTERVAL_TICKS = 100;

	/** Cap on distinct loot entries (item+source) to bound memory/payload; totals stay exact */
	private static final int MAX_LOOT_ENTRIES = 500;

	/** Cap on list-shaped collections (pets, clog slots, deaths) */
	private static final int MAX_LIST_ENTRIES = 200;

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
	private String accountHash;
	private Map<String, Object> startSnapshot;
	private Map<String, Object> endSnapshot;
	private final Map<String, Integer> kills = new HashMap<>();
	private final Map<String, Integer> clues = new HashMap<>();
	/** key: itemId + "|" + source → aggregated loot entry */
	private final Map<String, Map<String, Object>> loot = new LinkedHashMap<>();
	private long totalLootValue = 0;
	private final List<Map<String, Object>> pets = new ArrayList<>();
	private final List<Map<String, Object>> collectionLogSlots = new ArrayList<>();
	private final List<Map<String, Object>> deaths = new ArrayList<>();

	// ==================== LIFECYCLE ====================

	/**
	 * Start tracking a new session. Must be called on the client thread while
	 * logged in (clan validation point). Seasonal (leagues) worlds are not
	 * tracked — session summaries describe the main-game character.
	 */
	public void startSession() {
		if (client.getWorldType().contains(net.runelite.api.WorldType.SEASONAL)) {
			return;
		}
		resetState();
		sessionId = UUID.randomUUID().toString();
		startedAtMs = System.currentTimeMillis();
		lastUpdateMs = startedAtMs;
		username = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";
		accountHash = String.valueOf(client.getAccountHash());
		startSnapshot = buildSnapshot();
		endSnapshot = startSnapshot;
		active = true;
		dirty = true;
		log.debug("Session started: {}", sessionId);
	}

	/**
	 * Finalize the current session (clean logout) and return the summary map to
	 * embed in the LOGOUT payload, or null when no session is active.
	 * Clears the local persistence so it can't be replayed as "recovered".
	 */
	public Map<String, Object> finalizeSession() {
		if (!active) return null;

		refreshEndSnapshot();
		lastUpdateMs = System.currentTimeMillis();
		Map<String, Object> summary = buildSummary("logout", lastUpdateMs);

		active = false;
		clearPersisted();
		resetState();

		return summary;
	}

	/**
	 * On plugin startup: if a previous session was persisted but never finalized
	 * (crash / X-out), replay it to the backend as a recovered SESSION_SUMMARY.
	 * Safe to call when nothing is persisted.
	 */
	public void recoverPersistedSession() {
		try {
			String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
			if (json == null || json.isEmpty()) return;
			clearPersisted();

			JsonObject persisted = gson.fromJson(json, JsonObject.class);
			if (persisted == null || !persisted.has("summary")) return;

			Map<String, Object> payload = new HashMap<>();
			payload.put("eventType", "SESSION_SUMMARY");
			payload.put("eventTimestamp", System.currentTimeMillis());
			payload.put("accountHash", persisted.get("accountHash").getAsString());
			payload.put("username", persisted.get("username").getAsString());
			// JsonElement values serialize natively through Gson — no lossy Map round-trip
			payload.put("sessionSummary", persisted.get("summary"));

			webhookService.sendDataAsync(payload);
			log.info("Recovered unfinished session, replayed as SESSION_SUMMARY");
		} catch (Exception e) {
			log.warn("Failed to recover persisted session: {}", e.getMessage());
			clearPersisted();
		}
	}

	/**
	 * Tick driver: throttled local persistence of the accumulator (client thread).
	 */
	public void onGameTick() {
		if (!active) return;
		ticksSincePersist++;
		if (dirty && ticksSincePersist >= PERSIST_INTERVAL_TICKS) {
			ticksSincePersist = 0;
			dirty = false;
			try {
				refreshEndSnapshot();
				lastUpdateMs = System.currentTimeMillis();
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

	// ==================== ACCUMULATORS ====================

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
		// over the cap: value still counts via totalLootValue
		dirty = true;
	}

	public void addClue(String tier) {
		if (!active || tier == null || tier.isEmpty()) return;
		clues.merge(tier.toLowerCase(), 1, Integer::sum);
		dirty = true;
	}

	public void addPet(String petName) {
		if (!active || pets.size() >= MAX_LIST_ENTRIES) return;
		Map<String, Object> pet = new HashMap<>();
		pet.put("petName", petName);
		pet.put("timestamp", System.currentTimeMillis());
		pets.add(pet);
		dirty = true;
	}

	public void addCollectionLogSlot(String itemName) {
		if (!active || collectionLogSlots.size() >= MAX_LIST_ENTRIES) return;
		Map<String, Object> slot = new HashMap<>();
		slot.put("itemName", itemName);
		slot.put("timestamp", System.currentTimeMillis());
		collectionLogSlots.add(slot);
		dirty = true;
	}

	public void addDeath(String location) {
		if (!active || deaths.size() >= MAX_LIST_ENTRIES) return;
		Map<String, Object> death = new HashMap<>();
		death.put("location", location);
		death.put("timestamp", System.currentTimeMillis());
		deaths.add(death);
		dirty = true;
	}

	// ==================== INTERNALS ====================

	private void resetState() {
		sessionId = null;
		startedAtMs = 0;
		lastUpdateMs = 0;
		username = null;
		accountHash = null;
		startSnapshot = null;
		endSnapshot = null;
		kills.clear();
		clues.clear();
		loot.clear();
		totalLootValue = 0;
		pets.clear();
		collectionLogSlots.clear();
		deaths.clear();
		dirty = false;
		ticksSincePersist = 0;
	}

	/** Rebuild the end snapshot from the live client if still logged in */
	private void refreshEndSnapshot() {
		if (client.getGameState() == GameState.LOGGED_IN) {
			Map<String, Object> snapshot = buildSnapshot();
			if (snapshot != null) {
				endSnapshot = snapshot;
			}
		}
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
		summary.put("pets", new ArrayList<>(pets));
		summary.put("collectionLogSlots", new ArrayList<>(collectionLogSlots));
		summary.put("deaths", new ArrayList<>(deaths));
		return summary;
	}

	private void persist() {
		Map<String, Object> persisted = new HashMap<>();
		persisted.put("accountHash", accountHash);
		persisted.put("username", username);
		// Persisted summaries replay as "recovered"; endedAt = last local update we saw
		persisted.put("summary", buildSummary("recovered", lastUpdateMs));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, gson.toJson(persisted));
	}

	private void clearPersisted() {
		try {
			configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY);
		} catch (Exception ignored) {}
	}
}

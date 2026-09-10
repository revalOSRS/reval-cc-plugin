package com.revalclan.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.session.SessionStore.PersistedSession;
import com.revalclan.util.ClanMembership;
import com.revalclan.util.WebhookService;
import com.revalclan.util.Worlds;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accumulates the play session client-side and delivers it three ways:
 * <ul>
 *   <li><b>SESSION_HEARTBEAT</b> every ~10 minutes while playing, so the session
 *       exists on the server before it ends (live admin view; a crash costs at
 *       most one interval).</li>
 *   <li>The final summary attached to <b>LOGOUT</b>, or sent as a standalone
 *       <b>SESSION_SUMMARY</b> when a world hop cuts the session.</li>
 *   <li>A <b>SESSION_SUMMARY</b> replay of any session whose final summary the
 *       server never acknowledged — at startup and at every login.</li>
 * </ul>
 * Every persist goes straight to disk ({@link SessionStore}); the local copy is
 * deleted only when the server answers {@code sessionStored} = stored, duplicate
 * or rejected. The server dedupes on sessionId, so a replay can never double-store.
 *
 * A session starts the moment the player is logged in — before clan membership
 * is known — and accumulates regardless; only SENDING is gated on membership.
 */
@Slf4j
@Singleton
public class SessionTracker {
	/** Persist at most once per this many ticks (~30s), and only when dirty */
	private static final int PERSIST_INTERVAL_TICKS = 50;
	/** Running summary to the server every ~10 minutes */
	private static final int HEARTBEAT_INTERVAL_TICKS = 1000;
	/** Malfunction guard (deaths) */
	private static final int MAX_LIST_ENTRIES = 1000;

	/** Character Summary tab counters (Jagex varps) → summary key */
	private static final Map<String, Integer> COUNTER_VARPS = new LinkedHashMap<>();
	static {
		COUNTER_VARPS.put("monstersKilled", VarPlayerID.TRACKING_MONSTERS_KILLED);
		COUNTER_VARPS.put("bossesKilled", VarPlayerID.TRACKING_BOSSES_KILLED);
		COUNTER_VARPS.put("deaths", VarPlayerID.TRACKING_DEATHS);
		COUNTER_VARPS.put("cluesCompleted", VarPlayerID.TRACKING_CLUES_COMPLETED);
		COUNTER_VARPS.put("coinsGained", VarPlayerID.TRACKING_COINS_GAINED);
		COUNTER_VARPS.put("coinsLost", VarPlayerID.TRACKING_COINS_LOST);
	}

	@Inject private Client client;
	@Inject private Gson gson;
	@Inject private WebhookService webhookService;
	@Inject private SessionStore store;
	@Inject private ClanMembership membership;

	private boolean active = false;
	private boolean dirty = false;
	private int ticksSincePersist = 0;
	private int ticksSinceHeartbeat = 0;
	private boolean heartbeatRejected = false;

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
	private long totalLootValue = 0;
	private final List<Map<String, Object>> deaths = new ArrayList<>();
	private int playtimeMinutes = 0;
	private Map<String, Integer> countersStart;
	private Map<String, Integer> countersEnd;

	/** Latest Jagex "Time played" snapshot seen this process — survives session cuts for LOGIN/LOGOUT */
	private int lastKnownPlaytimeMinutes = 0;
	/** Replays already in flight this process — a login right after startup must not send them twice */
	private final Set<String> replaying = new HashSet<>();

	// ------------------------------------------------------------------ lifecycle

	/**
	 * Start a new session (client thread, logged in). Any world, any clan state —
	 * the backend gates on the worldFlags captured here and membership gates sending.
	 */
	public void startSession() {
		if (active) {
			log.debug("startSession while active — cutting {} first", sessionId);
			persistFinal("logout");
			reset();
		}
		resetState();
		sessionId = UUID.randomUUID().toString();
		startedAtMs = System.currentTimeMillis();
		lastUpdateMs = startedAtMs;
		username = currentPlayerName();
		accountHash = client.getAccountHash();
		world = client.getWorld();
		worldFlags = Worlds.flagNames(client);
		startSnapshot = buildSnapshot();
		endSnapshot = startSnapshot;
		readPlaytime();
		active = true;
		dirty = true;
		log.info("Session started: {} world={} flags={}", sessionId, world, worldFlags);
	}

	/**
	 * Finalize (clean logout) and return the summary for the LOGOUT payload, or
	 * null. The persisted copy is kept until the server acks it.
	 */
	public Map<String, Object> finalizeSession() {
		if (!active) return null;
		Map<String, Object> summary = persistFinal("logout");
		reset();
		return summary;
	}

	/**
	 * A world hop ends this session (each row is one world). The summary goes
	 * out now as a standalone SESSION_SUMMARY when membership is proven;
	 * otherwise the file waits for a login that proves it.
	 */
	public void cutForHop() {
		if (!active) return;
		Map<String, Object> summary = persistFinal("hop");
		String id = sessionId;
		reset();
		if (membership.isMember()) {
			sendStandalone(summary, id, currentPlayerName(), client.getAccountHash(), world, worldFlags);
		}
	}

	/** In-memory only — a persisted session replays as 'recovered' later. */
	public void reset() {
		active = false;
		resetState();
	}

	// ------------------------------------------------------------------ ticking

	/** Client thread, every tick while logged in — regardless of clan state. */
	public void onGameTick() {
		if (!active) return;

		// Skills and varps can land a tick or two after LOGGED_IN — fill the blanks
		if (snapshotEmpty(startSnapshot)) {
			startSnapshot = buildSnapshot();
			endSnapshot = startSnapshot;
			if (!snapshotEmpty(startSnapshot)) dirty = true;
		}
		if (playtimeMinutes <= 0) readPlaytime();
		readCounters();

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
				persist(buildSummary("recovered", lastUpdateMs));
			} catch (Exception e) {
				log.warn("Failed to persist session state: {}", e.getMessage());
			}
		}

		if (!heartbeatRejected && ++ticksSinceHeartbeat >= HEARTBEAT_INTERVAL_TICKS) {
			ticksSinceHeartbeat = 0;
			if (membership.isMember()) sendHeartbeat();
		}
	}

	/** VarClientIntChanged for ACCOUNT_SUMMARY_PLAYTIME — the server re-sends it at login, hop and region load. */
	public void onPlaytimeVarcChanged() {
		int value = client.getVarcIntValue(VarClientID.ACCOUNT_SUMMARY_PLAYTIME);
		if (value <= 0) return;
		lastKnownPlaytimeMinutes = Math.max(lastKnownPlaytimeMinutes, value);
		if (active && playtimeMinutes <= 0) {
			playtimeMinutes = value;
			dirty = true;
		}
	}

	/** Latest Jagex "Time played" (minutes) seen this process, or 0 */
	public int getLastKnownPlaytimeMinutes() {
		return lastKnownPlaytimeMinutes;
	}

	// ------------------------------------------------------------------ accumulators

	public void addKill(String npcName) {
		if (!active || npcName == null || npcName.isEmpty()) return;
		kills.merge(npcName, 1, Integer::sum);
		dirty = true;
	}

	/** Only the total is reported; the per-item breakdown has no consumer. */
	public void addLoot(String source, int itemId, String itemName, int quantity, long gePriceEach) {
		if (!active) return;
		totalLootValue += gePriceEach * quantity;
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

	// ------------------------------------------------------------------ delivery + recovery

	/**
	 * Server answered a summary. Drop the local copy only on a definitive
	 * outcome; anything else (older backend, transient error) keeps it for the
	 * next replay. The server dedupes, so keeping is always safe.
	 */
	public void confirmDelivered(String deliveredSessionId, JsonObject response) {
		if (deliveredSessionId == null) return;
		replaying.remove(deliveredSessionId);
		String outcome = null;
		try {
			if (response != null && response.has("sessionStored") && !response.get("sessionStored").isJsonNull()) {
				outcome = response.get("sessionStored").getAsString();
			}
		} catch (Exception ignored) {}
		if ("stored".equals(outcome) || "duplicate".equals(outcome) || "rejected".equals(outcome)) {
			store.delete(deliveredSessionId);
			log.info("Session {} {} by server — local copy dropped", deliveredSessionId, outcome);
		} else {
			log.info("Session {} not acknowledged (sessionStored={}) — keeping local copy", deliveredSessionId, outcome);
		}
	}

	/**
	 * Replay every persisted session the server has not acknowledged.
	 *
	 * @param membershipProven true when called after this login proved clan
	 *        membership — then files recorded before membership was known are
	 *        sent too. At startup only files stamped as recorded by a member go.
	 */
	public void recoverPersistedSessions(boolean membershipProven) {
		try {
			for (PersistedSession persisted : store.readAll()) {
				String id = persisted.sessionId();
				if (id.equals(sessionId) || replaying.contains(id)) continue;
				if (!persisted.member && !membershipProven) continue;
				replaying.add(id);
				replayPersisted(persisted);
			}
		} catch (Exception e) {
			log.warn("Failed to recover persisted sessions: {}", e.getMessage());
		}
	}

	// The envelope is reassembled from the persisted copy — no live client at startup.
	private void replayPersisted(PersistedSession persisted) {
		JsonObject summary = persisted.summary;
		String reason = summary.has("endReason") ? summary.get("endReason").getAsString() : "recovered";
		if (!"logout".equals(reason) && !"hop".equals(reason)) {
			summary.addProperty("endReason", "recovered");
		}
		Map<String, Object> payload = envelope("SESSION_SUMMARY", persisted.username, persisted.accountHash, persisted.world, persisted.worldFlags);
		payload.put("sessionSummary", summary);
		String id = persisted.sessionId();
		webhookService.sendDataAsync(payload, response -> confirmDelivered(id, response));
		log.info("Replaying unacknowledged session {} ({})", id, reason);
	}

	private void sendStandalone(Map<String, Object> summary, String id, String name, long hash, int w, List<String> flags) {
		Map<String, Object> payload = envelope("SESSION_SUMMARY", name, hash, w, flags);
		payload.put("sessionSummary", summary);
		replaying.add(id);
		webhookService.sendDataAsync(payload, response -> confirmDelivered(id, response));
	}

	private void sendHeartbeat() {
		touch();
		log.info("Session {} heartbeat after {} min", sessionId, (lastUpdateMs - startedAtMs) / 60000);
		Map<String, Object> payload = envelope("SESSION_HEARTBEAT", username, accountHash, world, worldFlags);
		payload.put("sessionSummary", buildSummary("heartbeat", lastUpdateMs));
		String id = sessionId;
		webhookService.sendDataAsync(payload, response -> {
			// A heartbeat never drops the local copy; 'rejected' (untracked world) just stops the beat
			try {
				if (response != null && response.has("sessionStored")
					&& "rejected".equals(response.get("sessionStored").getAsString()) && id.equals(sessionId)) {
					heartbeatRejected = true;
				}
			} catch (Exception ignored) {}
		});
	}

	private Map<String, Object> envelope(String eventType, String name, long hash, int w, List<String> flags) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("eventType", eventType);
		payload.put("eventTimestamp", System.currentTimeMillis());
		payload.put("accountHash", hash);
		payload.put("username", name != null ? name : "Unknown");
		payload.put("world", w);
		if (flags != null) payload.put("worldFlags", flags);
		return payload;
	}

	// ------------------------------------------------------------------ internals

	private String currentPlayerName() {
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
			return client.getLocalPlayer().getName();
		}
		return membership.getPlayerName() != null ? membership.getPlayerName() : "Unknown";
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
		totalLootValue = 0;
		deaths.clear();
		playtimeMinutes = 0;
		countersStart = null;
		countersEnd = null;
		dirty = false;
		ticksSincePersist = 0;
		ticksSinceHeartbeat = 0;
		heartbeatRejected = false;
	}

	/** Refresh the end snapshot and last-update time */
	private void touch() {
		if (client.getGameState() == GameState.LOGGED_IN) {
			Map<String, Object> snapshot = buildSnapshot();
			if (!snapshotEmpty(snapshot)) endSnapshot = snapshot;
		}
		lastUpdateMs = System.currentTimeMillis();
	}

	private boolean xpChangedSinceSnapshot() {
		if (client.getGameState() != GameState.LOGGED_IN || endSnapshot == null) return false;
		Object totalXp = endSnapshot.get("totalXp");
		return totalXp instanceof Number && ((Number) totalXp).longValue() != client.getOverallExperience();
	}

	private void readPlaytime() {
		try {
			int value = client.getVarcIntValue(VarClientID.ACCOUNT_SUMMARY_PLAYTIME);
			if (value > 0) {
				playtimeMinutes = value;
				lastKnownPlaytimeMinutes = Math.max(lastKnownPlaytimeMinutes, value);
			}
		} catch (Exception ignored) {}
	}

	/**
	 * The summary-tab varps arrive as a burst of zeros followed by the real
	 * values in the same tick after login/hop, so an all-zero read is ignored:
	 * the first non-zero read fixes the start, every later one moves the end.
	 */
	private void readCounters() {
		Map<String, Integer> current = new LinkedHashMap<>();
		boolean anyNonZero = false;
		for (Map.Entry<String, Integer> e : COUNTER_VARPS.entrySet()) {
			int v = client.getVarpValue(e.getValue());
			if (v != 0) anyNonZero = true;
			current.put(e.getKey(), v);
		}
		if (!anyNonZero) return;
		if (countersStart == null) {
			countersStart = current;
			dirty = true;
		}
		if (countersEnd == null || !countersEnd.equals(current)) {
			countersEnd = current;
			dirty = true;
		}
	}

	private static boolean snapshotEmpty(Map<String, Object> snapshot) {
		if (snapshot == null) return true;
		Object totalXp = snapshot.get("totalXp");
		return !(totalXp instanceof Number) || ((Number) totalXp).longValue() <= 0;
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
		summary.put("deaths", new ArrayList<>(deaths));
		if (worldFlags != null) summary.put("worldFlags", worldFlags);
		if (playtimeMinutes > 0) summary.put("playtimeMinutes", playtimeMinutes);
		if (countersStart != null) summary.put("countersStart", countersStart);
		if (countersEnd != null) summary.put("countersEnd", countersEnd);
		return summary;
	}

	/** Final summary: refreshed, persisted (kept until acked), returned for the payload. */
	private Map<String, Object> persistFinal(String endReason) {
		touch();
		Map<String, Object> summary = buildSummary(endReason, lastUpdateMs);
		persist(summary);
		log.info("Session {} ended ({}) after {} min, member={}", sessionId, endReason,
			(lastUpdateMs - startedAtMs) / 60000, membership.isMember());
		return summary;
	}

	private void persist(Map<String, Object> summary) {
		PersistedSession persisted = new PersistedSession();
		persisted.accountHash = accountHash;
		persisted.username = username;
		persisted.world = world;
		persisted.worldFlags = worldFlags;
		persisted.member = membership.isMember();
		persisted.summary = gson.toJsonTree(summary).getAsJsonObject();
		store.write(persisted);
	}
}

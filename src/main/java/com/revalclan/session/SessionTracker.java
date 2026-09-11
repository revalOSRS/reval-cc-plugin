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
import net.runelite.client.callback.ClientThread;

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
 * All state lives on the client thread: webhook callbacks bounce back through
 * {@link ClientThread} before touching it.
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
	@Inject private ClientThread clientThread;
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

	/** Summaries sent and not yet answered — a login right after startup must not send them twice */
	private final Set<String> inFlight = new HashSet<>();

	// ------------------------------------------------------------------ lifecycle

	/**
	 * Start a session if none is running (client thread, logged in). Idempotent:
	 * LOGGED_IN fires after every region load and reconnect, and only the first
	 * one after a login or a hop should open a segment. Any world, any clan
	 * state — the backend gates on the worldFlags captured here and membership
	 * gates sending.
	 */
	public void startSession() {
		if (active) return;
		resetState();
		sessionId = UUID.randomUUID().toString();
		startedAtMs = System.currentTimeMillis();
		lastUpdateMs = startedAtMs;
		username = playerName();
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
		Map<String, Object> summary = buildSummary("logout", touch());
		persist(summary);
		reset();
		return summary;
	}

	/**
	 * A world hop ends this session (each row is one world). The summary goes
	 * out now as a standalone SESSION_SUMMARY when membership is proven;
	 * otherwise the file waits for a login on this account that proves it.
	 */
	public void cutForHop() {
		if (!active) return;
		PersistedSession persisted = persist(buildSummary("hop", touch()));
		reset();
		if (persisted.member) send(persisted);
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

		// Player, skills and varps can land a tick or two after LOGGED_IN — fill the blanks
		if (username == null) username = playerName();
		if (snapshotEmpty(startSnapshot)) {
			startSnapshot = buildSnapshot();
			endSnapshot = startSnapshot;
			if (!snapshotEmpty(startSnapshot)) dirty = true;
		}
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
			persist(buildSummary("recovered", touch()));
		}

		if (!heartbeatRejected && ++ticksSinceHeartbeat >= HEARTBEAT_INTERVAL_TICKS) {
			ticksSinceHeartbeat = 0;
			if (membership.isMember()) sendHeartbeat();
		}
	}

	/** VarClientIntChanged for ACCOUNT_SUMMARY_PLAYTIME — the server re-sends it at login, hop and region load. */
	public void onPlaytimeVarcChanged() {
		if (active && playtimeMinutes <= 0) readPlaytime();
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
	 * Server answered a summary (any thread — hops onto the client thread).
	 * Drop the local copy only on a definitive outcome; anything else (older
	 * backend, transient error) keeps it for the next replay. The server
	 * dedupes, so keeping is always safe.
	 */
	public void confirmDelivered(String deliveredSessionId, JsonObject response) {
		if (deliveredSessionId == null) return;
		String outcome = storedOutcome(response);
		clientThread.invokeLater(() -> {
			inFlight.remove(deliveredSessionId);
			if ("stored".equals(outcome) || "duplicate".equals(outcome) || "rejected".equals(outcome)) {
				store.delete(deliveredSessionId);
				log.info("Session {} {} by server — local copy dropped", deliveredSessionId, outcome);
			} else {
				log.info("Session {} not acknowledged (sessionStored={}) — keeping local copy", deliveredSessionId, outcome);
			}
		});
	}

	/**
	 * Replay every persisted session the server has not acknowledged: those
	 * recorded while a member, plus — once THIS account has proven membership —
	 * its own files recorded before that was known. Files of other accounts on
	 * the machine stay put: nothing is ever sent for an account that never
	 * proved membership.
	 *
	 * @param provenAccountHash the account that just proved membership, or 0 at startup
	 */
	public void recoverPersistedSessions(long provenAccountHash) {
		for (PersistedSession persisted : store.readAll()) {
			String id = persisted.sessionId();
			if (id.equals(sessionId) || inFlight.contains(id)) continue;
			if (persisted.member || persisted.accountHash == provenAccountHash) send(persisted);
		}
	}

	/** The one delivery path for standalone summaries: hop cuts and replays alike. */
	private void send(PersistedSession persisted) {
		String id = persisted.sessionId();
		inFlight.add(id);
		Map<String, Object> payload = envelope("SESSION_SUMMARY", persisted.username, persisted.accountHash, persisted.world, persisted.worldFlags);
		payload.put("sessionSummary", persisted.summary);
		webhookService.sendDataAsync(payload, response -> confirmDelivered(id, response));
		log.info("Sending session {} ({})", id, persisted.summary.get("endReason"));
	}

	private void sendHeartbeat() {
		Map<String, Object> payload = envelope("SESSION_HEARTBEAT", username, accountHash, world, worldFlags);
		payload.put("sessionSummary", buildSummary(null, touch()));
		String id = sessionId;
		log.info("Session {} heartbeat after {} min", id, (lastUpdateMs - startedAtMs) / 60000);
		webhookService.sendDataAsync(payload, response -> {
			// A heartbeat never drops the local copy; 'rejected' (untracked world) just stops the beat
			if (!"rejected".equals(storedOutcome(response))) return;
			clientThread.invokeLater(() -> {
				if (id.equals(sessionId)) heartbeatRejected = true;
			});
		});
	}

	private static String storedOutcome(JsonObject response) {
		try {
			if (response != null && response.has("sessionStored") && !response.get("sessionStored").isJsonNull()) {
				return response.get("sessionStored").getAsString();
			}
		} catch (Exception ignored) {}
		return null;
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

	private String playerName() {
		return client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
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

	/** Refresh the end snapshot and the last-update time; returns the latter. */
	private long touch() {
		if (client.getGameState() == GameState.LOGGED_IN) {
			Map<String, Object> snapshot = buildSnapshot();
			if (!snapshotEmpty(snapshot)) endSnapshot = snapshot;
		}
		lastUpdateMs = System.currentTimeMillis();
		return lastUpdateMs;
	}

	private boolean xpChangedSinceSnapshot() {
		if (client.getGameState() != GameState.LOGGED_IN || endSnapshot == null) return false;
		Object totalXp = endSnapshot.get("totalXp");
		return totalXp instanceof Number && ((Number) totalXp).longValue() != client.getOverallExperience();
	}

	private void readPlaytime() {
		int value = client.getVarcIntValue(VarClientID.ACCOUNT_SUMMARY_PLAYTIME);
		if (value > 0 && value != playtimeMinutes) {
			playtimeMinutes = value;
			dirty = true;
		}
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
		if (!current.equals(countersEnd)) {
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

	/** @param endReason logout | hop | recovered, or null for a heartbeat (still running) */
	private Map<String, Object> buildSummary(String endReason, long endedAtMs) {
		Map<String, Object> summary = new HashMap<>();
		summary.put("sessionId", sessionId);
		summary.put("startedAt", startedAtMs);
		summary.put("endedAt", endedAtMs);
		if (endReason != null) summary.put("endReason", endReason);
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

	/** Write the summary to disk and return the persisted copy — the same object {@link #send} delivers. */
	private PersistedSession persist(Map<String, Object> summary) {
		PersistedSession persisted = new PersistedSession();
		persisted.accountHash = accountHash;
		persisted.username = username;
		persisted.world = world;
		persisted.worldFlags = worldFlags;
		persisted.member = membership.isMember();
		persisted.summary = gson.toJsonTree(summary).getAsJsonObject();
		store.write(persisted);
		if (summary.get("endReason") != null && !"recovered".equals(summary.get("endReason"))) {
			log.info("Session {} ended ({}) after {} min, member={}", sessionId, summary.get("endReason"),
				(lastUpdateMs - startedAtMs) / 60000, persisted.member);
		}
		return persisted;
	}
}

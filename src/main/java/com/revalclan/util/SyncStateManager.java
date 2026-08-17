package com.revalclan.util;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks the sync-state fingerprint handshake.
 *
 * The plugin hashes its account state (quests + miniquests, diaries, combat
 * achievements — the collection log is deliberately EXCLUDED: boundary payloads
 * never carry a real scan, so clog truth flows only via explicit Sync; kill
 * counts are also excluded, they change constantly and are delivered by
 * KILL_COUNT events / manual sync) and sends the hash with LOGIN/LOGOUT/SYNC
 * payloads. When the hash equals the last one the server acknowledged, the
 * bulky state categories are omitted from the payload.
 *
 * The server's ack (webhook response `sync: {fingerprint, stale}`) is persisted
 * per account in the RuneLite config store. `stale: true` means the server never
 * processed the state we think it has — the acked fingerprint is cleared and a
 * full SYNC is requested (sent from the game-tick loop, where client access is safe).
 */
@Slf4j
@Singleton
public class SyncStateManager {
	private static final String CONFIG_GROUP = "revalclan";
	private static final String CONFIG_KEY_PREFIX = "syncFingerprint_";

	@Inject private ConfigManager configManager;

	/** Set from the webhook response thread; consumed on the game-tick thread */
	private final AtomicBoolean fullSyncRequested = new AtomicBoolean(false);

	// ==================== FINGERPRINT COMPUTATION ====================

	/**
	 * Compute the canonical state fingerprint from a collected data map
	 * (the output of PlayerDataCollector.collectAllData()).
	 * Returns null when the data is too incomplete to fingerprint safely.
	 */
	@SuppressWarnings("unchecked")
	public String computeFingerprint(Map<String, Object> data) {
		try {
			StringBuilder canonical = new StringBuilder(16384);

			// Quests: sorted name=state (quests, then miniquests) + quest points
			Map<String, Object> quests = (Map<String, Object>) data.get("quests");
			canonical.append("quests|");
			if (quests != null) {
				Object questStates = quests.get("questStates");
				if (questStates instanceof Map) {
					for (Map.Entry<String, Object> e : new TreeMap<>((Map<String, Object>) questStates).entrySet()) {
						canonical.append(e.getKey()).append('=').append(e.getValue()).append(';');
					}
				}
				Object miniquestStates = quests.get("miniquestStates");
				if (miniquestStates instanceof Map) {
					canonical.append("mini|");
					for (Map.Entry<String, Object> e : new TreeMap<>((Map<String, Object>) miniquestStates).entrySet()) {
						canonical.append(e.getKey()).append('=').append(e.getValue()).append(';');
					}
				}
				canonical.append("qp=").append(quests.get("questPoints"));
			}

			// Diaries: sorted area=easy,medium,hard,elite
			Map<String, Object> diaries = (Map<String, Object>) data.get("achievementDiaries");
			canonical.append("|diaries|");
			if (diaries != null && diaries.get("progress") instanceof Map) {
				Map<String, Object> progress = new TreeMap<>((Map<String, Object>) diaries.get("progress"));
				for (Map.Entry<String, Object> e : progress.entrySet()) {
					canonical.append(e.getKey()).append('=');
					if (e.getValue() instanceof Map) {
						Map<String, Object> tiers = (Map<String, Object>) e.getValue();
						canonical.append(tiers.get("easy")).append(',')
							.append(tiers.get("medium")).append(',')
							.append(tiers.get("hard")).append(',')
							.append(tiers.get("elite"));
					}
					canonical.append(';');
				}
			}

			// Combat achievements: sorted completed task names + total points.
			// allTasks carries ONLY completed tasks (slim shape, no `completed`
			// flag); a present flag is still honoured for shape tolerance.
			Map<String, Object> cas = (Map<String, Object>) data.get("combatAchievements");
			canonical.append("|cas|");
			if (cas != null) {
				Object allTasks = cas.get("allTasks");
				if (allTasks instanceof List) {
					TreeMap<String, Boolean> completedNames = new TreeMap<>();
					for (Object taskObj : (List<Object>) allTasks) {
						if (taskObj instanceof Map) {
							Map<String, Object> task = (Map<String, Object>) taskObj;
							Object completed = task.get("completed");
							if (completed == null || Boolean.TRUE.equals(completed)) {
								completedNames.put(String.valueOf(task.get("name")), true);
							}
						}
					}
					for (String name : completedNames.keySet()) {
						canonical.append(name).append(';');
					}
				}
				canonical.append("pts=").append(cas.get("totalPoints"));
			}

			// Collection log is intentionally NOT part of the fingerprint:
			// boundary payloads no longer carry it (no real scan exists at
			// LOGIN/LOGOUT), so hashing scan-dependent clog state only forced
			// pointless full boundary payloads. Dropping it changes every
			// fingerprint once post-update. KC attributes are likewise not
			// hashed (see class javadoc).

			return sha256Hex(canonical.toString());
		} catch (Exception e) {
			log.warn("Failed to compute sync fingerprint: {}", e.getMessage());
			return null;
		}
	}

	// ==================== ACKED FINGERPRINT PERSISTENCE ====================

	public String getAckedFingerprint(long accountHash) {
		return configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_PREFIX + accountHash);
	}

	public void storeAckedFingerprint(long accountHash, String fingerprint) {
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_PREFIX + accountHash, fingerprint);
	}

	public void clearAckedFingerprint(long accountHash) {
		try {
			configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY_PREFIX + accountHash);
		} catch (Exception ignored) {}
	}

	// ==================== SERVER ACK HANDLING ====================

	/**
	 * Handle the `sync` object from a webhook response (runs on the HTTP thread —
	 * touches only the config store, never the client).
	 */
	public void handleSyncAckResponse(JsonObject response, long accountHash) {
		try {
			if (response == null || !response.has("sync") || !response.get("sync").isJsonObject()) return;
			JsonObject sync = response.getAsJsonObject("sync");

			boolean stale = sync.has("stale") && !sync.get("stale").isJsonNull() && sync.get("stale").getAsBoolean();
			if (stale) {
				// Server never processed the state we skipped on — clear and re-send full
				clearAckedFingerprint(accountHash);
				fullSyncRequested.set(true);
				log.info("Sync fingerprint stale — full sync requested");
				return;
			}

			if (sync.has("fingerprint") && !sync.get("fingerprint").isJsonNull()) {
				storeAckedFingerprint(accountHash, sync.get("fingerprint").getAsString());
			}
		} catch (Exception e) {
			log.warn("Failed to handle sync ack: {}", e.getMessage());
		}
	}

	/** Consume the pending full-sync request (checked from the game-tick loop) */
	public boolean consumeFullSyncRequest() {
		return fullSyncRequested.getAndSet(false);
	}

	private static String sha256Hex(String input) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
		StringBuilder hex = new StringBuilder(hash.length * 2);
		for (byte b : hash) {
			hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return hex.toString();
	}
}

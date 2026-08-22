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
import java.util.function.Consumer;

/**
 * Sync-state fingerprint handshake: hashes quests + miniquests, diaries and
 * CAs (clog and KCs deliberately excluded — no real clog scan exists at
 * boundaries, KCs change constantly). When the hash matches the last server
 * ack, boundary payloads go slim. An ack with stale=true clears the stored
 * fingerprint and requests a full repair SYNC.
 */
@Slf4j
@Singleton
public class SyncStateManager {
	private static final String CONFIG_GROUP = "revalclan";
	private static final String CONFIG_KEY_PREFIX = "syncFingerprint_";

	@Inject private ConfigManager configManager;

	/** Set from the webhook response thread; consumed on the game-tick thread */
	private final AtomicBoolean fullSyncRequested = new AtomicBoolean(false);

	/** Canonical state fingerprint; null when the data is too incomplete to hash safely. */
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

			// CAs: sorted completed task names + total points (a present `completed`
			// flag is honoured, but the slim shape omits it)
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

			// Collection log and KCs are deliberately NOT hashed (see class javadoc)

			return sha256Hex(canonical.toString());
		} catch (Exception e) {
			log.warn("Failed to compute sync fingerprint: {}", e.getMessage());
			return null;
		}
	}

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

	/** Response consumer recording the server's fingerprint ack for this account. */
	public Consumer<JsonObject> ackHandler(long accountHash) {
		return response -> handleSyncAckResponse(response, accountHash);
	}

	/** Handles the `sync` object from a webhook response. HTTP thread — never touch the client. */
	public void handleSyncAckResponse(JsonObject response, long accountHash) {
		try {
			if (response == null || !response.has("sync") || !response.get("sync").isJsonObject()) return;
			JsonObject sync = response.getAsJsonObject("sync");

			boolean stale = sync.has("stale") && !sync.get("stale").isJsonNull() && sync.get("stale").getAsBoolean();
			if (stale) {
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

	/** Consumed from the game-tick loop */
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

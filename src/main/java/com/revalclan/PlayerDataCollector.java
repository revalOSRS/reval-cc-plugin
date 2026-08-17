package com.revalclan;

import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.combatachievements.CombatAchievementManager;
import com.revalclan.diaries.AchievementDiaryManager;
import com.revalclan.pbs.ClogPersonalBestCapture;
import com.revalclan.pbs.PersonalBestManager;
import com.revalclan.player.PlayerManager;
import com.revalclan.quests.QuestManager;
import com.revalclan.util.SyncStateManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects ALL player data by coordinating various managers
 */
@Slf4j
@Singleton
public class PlayerDataCollector {
	@Inject
	private Client client;

	@Inject
	private PlayerManager playerManager;

	@Inject
	private QuestManager questManager;

	@Inject
	private AchievementDiaryManager achievementDiaryManager;

	@Inject
	private CombatAchievementManager combatAchievementManager;

	@Inject
	private CollectionLogManager collectionLogManager;

	@Inject
	private PersonalBestManager personalBestManager;

	@Inject
	private ClogPersonalBestCapture clogPersonalBestCapture;

	@Inject
	private SyncStateManager syncStateManager;

	/**
	 * Collects all player data as a full payload, fingerprint included.
	 * Only this path (the explicit SYNC) carries the collection log — clog
	 * truth requires an actual log scan, which only a deliberate sync has.
	 */
	public Map<String, Object> collectAllData() {
		Map<String, Object> data = collectFullState();
		data.put("collectionLog", collectionLogManager.sync());
		attachFingerprint(data);
		return data;
	}

	/**
	 * LOGIN/LOGOUT payload: slim (player + fingerprint) when state is
	 * unchanged since the last acked fingerprint, full otherwise.
	 * Never includes the collection log: at LOGIN no scan has happened
	 * (zeros / varp fallback) and at LOGOUT one only exists if the log
	 * happened to be opened — clog flows exclusively via explicit Sync.
	 */
	public Map<String, Object> collectBoundaryData() {
		Map<String, Object> data = collectFullState();

		String fingerprint = attachFingerprint(data);
		if (fingerprint == null) return data;

		String acked = syncStateManager.getAckedFingerprint(client.getAccountHash());
		if (fingerprint.equals(acked)) {
			Map<String, Object> slim = new HashMap<>();
			slim.put("player", data.get("player"));
			slim.put("syncFingerprint", fingerprint);
			return slim;
		}

		return data;
	}

	private Map<String, Object> collectFullState() {
		Map<String, Object> data = new HashMap<>();
		data.put("player", playerManager.sync());
		data.put("quests", questManager.sync());
		data.put("achievementDiaries", achievementDiaryManager.sync());
		data.put("combatAchievements", combatAchievementManager.sync());
		data.put("personalBests", personalBestManager.sync());
		data.put("clogPersonalBests", clogPersonalBestCapture.sync());
		return data;
	}

	/**
	 * Compute and attach the state fingerprint, returning it (null when not
	 * attached). Skipped on seasonal (leagues) worlds — leagues state is a
	 * different character and flows through the leagues pipeline, which does
	 * not participate in the fingerprint handshake.
	 */
	private String attachFingerprint(Map<String, Object> data) {
		try {
			if (client.getWorldType().contains(WorldType.SEASONAL)) return null;
			String fingerprint = syncStateManager.computeFingerprint(data);
			if (fingerprint != null) {
				data.put("syncFingerprint", fingerprint);
			}
			return fingerprint;
		} catch (Exception e) {
			log.warn("Failed to attach sync fingerprint: {}", e.getMessage());
			return null;
		}
	}
}

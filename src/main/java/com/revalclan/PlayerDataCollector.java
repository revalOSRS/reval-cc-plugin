package com.revalclan;

import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.combatachievements.CombatAchievementManager;
import com.revalclan.diaries.AchievementDiaryManager;
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
	private SyncStateManager syncStateManager;

	/**
	 * Collects all player data and returns it as a map.
	 * Includes the state fingerprint (non-seasonal worlds) so the server can
	 * record what it processed. Used by the manual SYNC button — always full,
	 * doubling as the repair path for fingerprint drift.
	 */
	public Map<String, Object> collectAllData() {
		Map<String, Object> data = new HashMap<>();

		data.put("player", playerManager.sync());
		data.put("quests", questManager.sync());
		data.put("achievementDiaries", achievementDiaryManager.sync());
		data.put("combatAchievements", combatAchievementManager.sync());
		data.put("collectionLog", collectionLogManager.sync());

		attachFingerprint(data);

		return data;
	}

	/**
	 * Collects data for a LOGIN/LOGOUT session boundary. When the state
	 * fingerprint equals the last server-acked one, the four bulky state
	 * categories are OMITTED — the payload carries only player (incl. skills)
	 * and the fingerprint, and the backend skips the redundant reprocessing.
	 */
	public Map<String, Object> collectBoundaryData() {
		Map<String, Object> data = collectAllData();

		String fingerprint = (String) data.get("syncFingerprint");
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

	/**
	 * Compute and attach the state fingerprint. Skipped on seasonal (leagues)
	 * worlds — leagues state is a different character and flows through the
	 * leagues pipeline, which does not participate in the fingerprint handshake.
	 */
	private void attachFingerprint(Map<String, Object> data) {
		try {
			if (client.getWorldType().contains(WorldType.SEASONAL)) return;
			String fingerprint = syncStateManager.computeFingerprint(data);
			if (fingerprint != null) {
				data.put("syncFingerprint", fingerprint);
			}
		} catch (Exception e) {
			log.warn("Failed to attach sync fingerprint: {}", e.getMessage());
		}
	}
}

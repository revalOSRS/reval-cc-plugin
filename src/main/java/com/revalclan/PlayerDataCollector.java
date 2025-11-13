package com.revalclan;

import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.combatachievements.CombatAchievementManager;
import com.revalclan.diaries.AchievementDiaryManager;
import com.revalclan.player.PlayerManager;
import com.revalclan.quests.QuestManager;
import lombok.extern.slf4j.Slf4j;

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
	private PlayerManager playerManager;
	
	@Inject
	private QuestManager questManager;
	
	@Inject
	private AchievementDiaryManager achievementDiaryManager;
	
	@Inject
	private CombatAchievementManager combatAchievementManager;

	@Inject
	private CollectionLogManager collectionLogManager;

	/**
	 * Collects all player data and returns it as a map
	 */
	public Map<String, Object> collectAllData() {
		Map<String, Object> data = new HashMap<>();
		
		data.put("player", playerManager.sync());
		data.put("quests", questManager.sync());
		data.put("achievementDiaries", achievementDiaryManager.sync());
		data.put("combatAchievements", combatAchievementManager.sync());
		data.put("collectionLog", collectionLogManager.sync());
		
		return data;
	}
}

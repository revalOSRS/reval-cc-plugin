package com.revalclan.diaries;

import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages achievement diary progress
 */
@Singleton
public class AchievementDiaryManager {
	@Inject
	private Client client;

	private static final String[] TIERS = {"easy", "medium", "hard", "elite"};

	private final Map<String, Map<String, Integer>> diaryVarbits = new HashMap<>();

	/**
	 * Initialize all diary varbit mappings: the easy, medium, hard and elite varbit of each region
	 */
	public AchievementDiaryManager() {
		addRegion("Ardougne", 4458, 4459, 4460, 4461);
		addRegion("Desert", 4483, 4484, 4485, 4486);
		addRegion("Falador", 4462, 4463, 4464, 4465);
		addRegion("Fremennik", 4491, 4492, 4493, 4494);
		addRegion("Kandarin", 4475, 4476, 4477, 4478);
		addRegion("Karamja", 3578, 3599, 3611, 4566);
		addRegion("Kourend", 7925, 7926, 7927, 7928);
		addRegion("Lumbridge", 4495, 4496, 4497, 4498);
		addRegion("Morytania", 4487, 4488, 4489, 4490);
		addRegion("Varrock", 4479, 4480, 4481, 4482);
		addRegion("Western", 4471, 4472, 4473, 4474);
		addRegion("Wilderness", 4466, 4467, 4468, 4469);
	}

	private void addRegion(String region, int... tierVarbits) {
		Map<String, Integer> tiers = new HashMap<>();
		for (int i = 0; i < TIERS.length; i++) {
			tiers.put(TIERS[i], tierVarbits[i]);
		}
		diaryVarbits.put(region, tiers);
	}

	/**
	 * Sync and get achievement diary progress data
	 */
	public Map<String, Object> sync() {
		Map<String, Object> diaryData = new HashMap<>();
		Map<String, Map<String, Boolean>> diaryProgress = new HashMap<>();
		
		int totalCompleted = 0;
		
		for (Map.Entry<String, Map<String, Integer>> regionEntry : diaryVarbits.entrySet()) {
			String region = regionEntry.getKey();
			Map<String, Boolean> tierProgress = new HashMap<>();
			
			for (Map.Entry<String, Integer> tierEntry : regionEntry.getValue().entrySet()) {
				String tier = tierEntry.getKey();
				int varbitId = tierEntry.getValue();
				int value = client.getVarbitValue(varbitId);
				
				// Karamja Easy (3578), Medium (3599), Hard (3611) special case
				// 0 = not started, 1 = started, 2 = completed
				boolean isComplete;
				if (varbitId == 3578 || varbitId == 3599 || varbitId == 3611) {
					isComplete = value > 1;
				} else {
					isComplete = value > 0;
				}
				
				tierProgress.put(tier, isComplete);
				
				if (isComplete) {
					totalCompleted++;
				}
			}
			
			diaryProgress.put(region, tierProgress);
		}
		
		diaryData.put("progress", diaryProgress);
		diaryData.put("totalCompleted", totalCompleted);
		diaryData.put("totalDiaries", 48); // 12 regions * 4 tiers
		
		return diaryData;
	}
}


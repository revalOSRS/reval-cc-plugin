package com.revalclan.diaries;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages achievement diary progress
 */
@Slf4j
@Singleton
public class AchievementDiaryManager {
	@Inject
	private Client client;

	private final Map<String, Map<String, Integer>> diaryVarbits = new HashMap<>();

	public AchievementDiaryManager() {
		initializeDiaryVarbits();
	}

	/**
	 * Initialize all diary varbit mappings
	 */
	private void initializeDiaryVarbits() {
		Map<String, Integer> ardougne = new HashMap<>();
		ardougne.put("easy", 4458);
		ardougne.put("medium", 4459);
		ardougne.put("hard", 4460);
		ardougne.put("elite", 4461);
		diaryVarbits.put("Ardougne", ardougne);
		
		Map<String, Integer> desert = new HashMap<>();
		desert.put("easy", 4483);
		desert.put("medium", 4484);
		desert.put("hard", 4485);
		desert.put("elite", 4486);
		diaryVarbits.put("Desert", desert);

		Map<String, Integer> falador = new HashMap<>();
		falador.put("easy", 4462);
		falador.put("medium", 4463);
		falador.put("hard", 4464);
		falador.put("elite", 4465);
		diaryVarbits.put("Falador", falador);

		Map<String, Integer> fremennik = new HashMap<>();
		fremennik.put("easy", 4491);
		fremennik.put("medium", 4492);
		fremennik.put("hard", 4493);
		fremennik.put("elite", 4494);
		diaryVarbits.put("Fremennik", fremennik);

		Map<String, Integer> kandarin = new HashMap<>();
		kandarin.put("easy", 4475);
		kandarin.put("medium", 4476);
		kandarin.put("hard", 4477);
		kandarin.put("elite", 4478);
		diaryVarbits.put("Kandarin", kandarin);

		Map<String, Integer> karamja = new HashMap<>();
		karamja.put("easy", 3578);
		karamja.put("medium", 3599);
		karamja.put("hard", 3611);
		karamja.put("elite", 4566);
		diaryVarbits.put("Karamja", karamja);
		
		Map<String, Integer> kourend = new HashMap<>();
		kourend.put("easy", 7925);
		kourend.put("medium", 7926);
		kourend.put("hard", 7927);
		kourend.put("elite", 7928);
		diaryVarbits.put("Kourend", kourend);
		
		Map<String, Integer> lumbridge = new HashMap<>();
		lumbridge.put("easy", 4495);
		lumbridge.put("medium", 4496);
		lumbridge.put("hard", 4497);
		lumbridge.put("elite", 4498);
		diaryVarbits.put("Lumbridge", lumbridge);

		Map<String, Integer> morytania = new HashMap<>();
		morytania.put("easy", 4487);
		morytania.put("medium", 4488);
		morytania.put("hard", 4489);
		morytania.put("elite", 4490);
		diaryVarbits.put("Morytania", morytania);

		Map<String, Integer> varrock = new HashMap<>();
		varrock.put("easy", 4479);
		varrock.put("medium", 4480);
		varrock.put("hard", 4481);
		varrock.put("elite", 4482);
		diaryVarbits.put("Varrock", varrock);
		
		Map<String, Integer> western = new HashMap<>();
		western.put("easy", 4471);
		western.put("medium", 4472);
		western.put("hard", 4473);
		western.put("elite", 4474);
		diaryVarbits.put("Western", western);
		
		Map<String, Integer> wilderness = new HashMap<>();
		wilderness.put("easy", 4466);
		wilderness.put("medium", 4467);
		wilderness.put("hard", 4468);
		wilderness.put("elite", 4469);
		diaryVarbits.put("Wilderness", wilderness);
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


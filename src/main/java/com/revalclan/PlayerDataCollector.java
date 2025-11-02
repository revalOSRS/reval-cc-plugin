package com.revalclan;

import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.combatachievements.CombatAchievementManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects ALL player data including quests, achievement diaries, combat achievements, killcounts, and collection log
 * Output format matches Temple OSRS for API compatibility
 */
@Slf4j
@Singleton
public class PlayerDataCollector
{
	@Inject
	private Client client;
	
	@Inject
	private CombatAchievementManager combatAchievementManager;

	@Inject
	private CollectionLogManager collectionLogManager;

	/**
	 * Collects all player data and returns it as a map
	 */
	public Map<String, Object> collectAllData()
	{
		Map<String, Object> data = new HashMap<>();
		
		data.put("player", collectPlayerMetadata());
		
		data.put("quests", collectQuestData());
		data.put("achievementDiaries", collectAchievementDiaryData());
		data.put("combatAchievements", combatAchievementManager.getData());
		data.put("collectionLog", collectionLogManager.getCollectionLogData());
		
		data.put("timestamp", Instant.now().toString());
		
		return data;
	}

	/**
	 * Collects player metadata
	 */
	private Map<String, Object> collectPlayerMetadata()
	{
		Map<String, Object> metadata = new HashMap<>();
		
		if (client.getLocalPlayer() != null)
		{
			metadata.put("username", client.getLocalPlayer().getName());
			metadata.put("combatLevel", client.getLocalPlayer().getCombatLevel());
		}
		else
		{
			metadata.put("username", "Unknown");
			metadata.put("combatLevel", 0);
		}
		
		metadata.put("accountHash", client.getAccountHash());
		metadata.put("accountType", getAccountTypeName());
		metadata.put("totalLevel", client.getTotalLevel());
		metadata.put("totalExperience", client.getOverallExperience());
		
		return metadata;
	}

	private String getAccountTypeName()
	{
		int accountType = client.getVarbitValue(10059); // Varbits.ACCOUNT_TYPE
		switch (accountType)
		{
			case 0:
				return "NORMAL";
			case 1:
				return "IRONMAN";
			case 2:
				return "ULTIMATE_IRONMAN";
			case 3:
				return "HARDCORE_IRONMAN";
			case 4:
				return "GROUP_IRONMAN";
			case 5:
				return "HARDCORE_GROUP_IRONMAN";
			case 6:
				return "UNRANKED_GROUP_IRONMAN";
			default:
				return "UNKNOWN";
		}
	}

	/**
	 * Collects quest completion data
	 */
	private Map<String, Object> collectQuestData()
	{
		Map<String, Object> questData = new HashMap<>();
		int completedCount = 0;
		int inProgressCount = 0;
		int notStartedCount = 0;
		
		Map<String, String> questStates = new HashMap<>();
		
		for (Quest quest : Quest.values())
		{
			QuestState state = quest.getState(client);
			questStates.put(quest.getName(), state.name());
			
			switch (state)
			{
				case FINISHED:
					completedCount++;
					break;
				case IN_PROGRESS:
					inProgressCount++;
					break;
				case NOT_STARTED:
					notStartedCount++;
					break;
			}
		}
		
		questData.put("completed", completedCount);
		questData.put("inProgress", inProgressCount);
		questData.put("notStarted", notStartedCount);
		questData.put("questPoints", client.getVarpValue(101)); // Quest points varplayer
		questData.put("questStates", questStates);
		
		return questData;
	}

	/**
	 * Collects achievement diary progress
	 */
	private Map<String, Object> collectAchievementDiaryData()
	{
		Map<String, Object> diaryData = new HashMap<>();
		Map<String, Map<String, Boolean>> diaryProgress = new HashMap<>();
		
		Map<String, Map<String, Integer>> diaryVarbits = new HashMap<>();
		
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
		
		int totalCompleted = 0;
		for (Map.Entry<String, Map<String, Integer>> regionEntry : diaryVarbits.entrySet())
		{
			String region = regionEntry.getKey();
			Map<String, Boolean> tierProgress = new HashMap<>();
			
			for (Map.Entry<String, Integer> tierEntry : regionEntry.getValue().entrySet())
			{
				String tier = tierEntry.getKey();
				int varbitId = tierEntry.getValue();
				int value = client.getVarbitValue(varbitId);
				
				// Karamja Easy (3578), Medium (3599), Hard (3611) special case
				// 0 = not started, 1 = started, 2 = completed
				boolean isComplete;
				if (varbitId == 3578 || varbitId == 3599 || varbitId == 3611)
				{
					isComplete = value > 1;
				}
				else
				{
					isComplete = value > 0;
				}
				
				tierProgress.put(tier, isComplete);
				
				if (isComplete)
				{
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

	/**
	 * Writes all collected data to a JSON file for inspection
	 */
	public void writeDataToFile()
	{
		Map<String, Object> data = collectAllData();
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(data);
		
		@SuppressWarnings("unchecked")
		Map<String, Object> player = (Map<String, Object>) data.get("player");
		String playerName = player.get("username") != null ? player.get("username").toString() : "unknown";
		playerName = playerName.replaceAll("[^a-zA-Z0-9]", "_"); // Sanitize filename
		
		String timestamp = Instant.now().toString().replaceAll("[:]", "-");
		String filename = String.format("reval_data_%s_%s.json", playerName, timestamp);
		
		String userHome = System.getProperty("user.home");
		File dataDir = new File(userHome, ".runelite/reval-clan");
		
		if (!dataDir.exists())
		{
			dataDir.mkdirs();
		}
		
		File outputFile = new File(dataDir, filename);
		
		try (FileWriter writer = new FileWriter(outputFile))
		{
			writer.write(json);
			log.info("Data written to: {}", outputFile.getAbsolutePath());
		}
		catch (IOException e)
		{
			log.error("Failed to write data file", e);
		}
	}
}

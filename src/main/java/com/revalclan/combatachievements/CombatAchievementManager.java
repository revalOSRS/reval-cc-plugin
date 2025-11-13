package com.revalclan.combatachievements;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Manages Combat Achievement task data by reading from game cache (enums/structs)
 */
@Slf4j
@Singleton
public class CombatAchievementManager {
	@Inject private Client client;

	private static final Map<Integer, String> TIER_ENUMS = new LinkedHashMap<>();
	static {
		TIER_ENUMS.put(3981, "Easy");
		TIER_ENUMS.put(3982, "Medium");
		TIER_ENUMS.put(3983, "Hard");
		TIER_ENUMS.put(3984, "Elite");
		TIER_ENUMS.put(3985, "Master");
		TIER_ENUMS.put(3986, "Grandmaster");
	}

	private static final Map<Integer, String> TYPE_MAP = new LinkedHashMap<>();
	static {
		TYPE_MAP.put(1, "Stamina");
		TYPE_MAP.put(2, "Perfection");
		TYPE_MAP.put(3, "Kill Count");
		TYPE_MAP.put(4, "Mechanical");
		TYPE_MAP.put(5, "Restriction");
		TYPE_MAP.put(6, "Speed");
	}

	private static final int BOSS_ENUM_ID = 3971;

	private static final int FIELD_NAME = 1308;
	private static final int FIELD_DESCRIPTION = 1309;
	private static final int FIELD_TASK_ID = 1306;
	private static final int FIELD_TYPE_ID = 1311;
	private static final int FIELD_BOSS_ID = 1312;

	private static final int[] COMPLETION_VARPS = {
		3116,  // CA_TASK_COMPLETED_0
		3117,  // CA_TASK_COMPLETED_1
		3118,  // CA_TASK_COMPLETED_2
		3119,  // CA_TASK_COMPLETED_3
		3120,  // CA_TASK_COMPLETED_4
		3121,  // CA_TASK_COMPLETED_5
		3122,  // CA_TASK_COMPLETED_6
		3123,  // CA_TASK_COMPLETED_7
		3124,  // CA_TASK_COMPLETED_8
		3125,  // CA_TASK_COMPLETED_9
		3126,  // CA_TASK_COMPLETED_10
		3127,  // CA_TASK_COMPLETED_11
		3128,  // CA_TASK_COMPLETED_12
		3387,  // CA_TASK_COMPLETED_13
		3718,  // CA_TASK_COMPLETED_14
		3773,  // CA_TASK_COMPLETED_15
		3774,  // CA_TASK_COMPLETED_16
		4204,  // CA_TASK_COMPLETED_17
		4496,  // CA_TASK_COMPLETED_18
		4721   // CA_TASK_COMPLETED_19
	};

	private final List<CombatAchievementTask> allTasks = new ArrayList<>();

	/**
	 * Sync and get combat achievement data
	 */
	public Map<String, Object> sync() {
		allTasks.clear();
		
		for (Map.Entry<Integer, String> tierEntry : TIER_ENUMS.entrySet()) {
			try {
				EnumComposition tierEnum = client.getEnum(tierEntry.getKey());
				if (tierEnum == null) continue;
				
				for (int structId : tierEnum.getIntVals()) {
					try {
						CombatAchievementTask task = loadTaskFromStruct(structId, tierEntry.getValue());
						if (task != null) allTasks.add(task);
					} catch (Exception ignored) {}
				}
			} catch (Exception ignored) {}
		}
		
		int totalPoints = calculateTotalPoints();
		
		Map<String, Object> data = new HashMap<>();
		data.put("currentTier", calculateCurrentTier(totalPoints));
		data.put("totalPoints", totalPoints);
		data.put("tierProgress", getTierProgress());
		data.put("allTasks", getAllTasksDetailed());
		data.put("totalTasksLoaded", allTasks.size());
		
		return data;
	}

	/**
	 * Loads a single task from a struct
	 */
	private CombatAchievementTask loadTaskFromStruct(int structId, String tierName) {
		StructComposition struct = client.getStructComposition(structId);
		if (struct == null) return null;
		
		String name = struct.getStringValue(FIELD_NAME);
		String description = struct.getStringValue(FIELD_DESCRIPTION);
		int taskId = struct.getIntValue(FIELD_TASK_ID);
		int typeId = struct.getIntValue(FIELD_TYPE_ID);
		String type = TYPE_MAP.getOrDefault(typeId, "Unknown");
		int bossId = struct.getIntValue(FIELD_BOSS_ID);
		String bossName = getBossName(bossId);
		
		boolean completed = isTaskCompleted(taskId);
		
		CombatAchievementTask task = new CombatAchievementTask();
		task.setId(taskId);
		task.setName(name);
		task.setDescription(description);
		task.setTier(tierName);
		task.setType(type);
		task.setBoss(bossName);
		task.setCompleted(completed);
		task.setPoints(getPointsForTier(tierName));
		
		return task;
	}

	/**
	 * Gets boss name from boss enum
	 */
	private String getBossName(int bossId) {
		try {
			EnumComposition bossEnum = client.getEnum(BOSS_ENUM_ID);
			if (bossEnum != null) {
				String name = bossEnum.getStringValue(bossId);
				if (name != null && !name.isEmpty()) return name;
			}
		} catch (Exception ignored) {}
		return "Unknown";
	}

	/**
	 * Checks if a task is completed using VarPlayer
	 */
	private boolean isTaskCompleted(int taskId) {
		if (taskId < 0 || taskId >= COMPLETION_VARPS.length * 32) return false;

		int varpIndex = taskId / 32;
		int bitIndex = taskId % 32;

		if (varpIndex >= COMPLETION_VARPS.length) return false;
		
		try {
			int varpValue = client.getVarpValue(COMPLETION_VARPS[varpIndex]);
			return (varpValue & (1 << bitIndex)) != 0;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Gets points for a tier
	 */
	private int getPointsForTier(String tier) {
		switch (tier.toLowerCase()) {
			case "easy": return 1;
			case "medium": return 2;
			case "hard": return 3;
			case "elite": return 4;
			case "master": return 5;
			case "grandmaster": return 6;
			default: return 1;
		}
	}

	/**
	 * Calculate total points from completed tasks
	 */
	private int calculateTotalPoints() {
		return allTasks.stream()
			.filter(CombatAchievementTask::isCompleted)
			.mapToInt(CombatAchievementTask::getPoints)
			.sum();
	}

	/**
	 * Calculate current tier based on total points
	 */
	private String calculateCurrentTier(int totalPoints) {
		if (totalPoints >= 1550) return "Grandmaster";
		if (totalPoints >= 1265) return "Master";
		if (totalPoints >= 977) return "Elite";
		if (totalPoints >= 683) return "Hard";
		if (totalPoints >= 391) return "Medium";
		if (totalPoints >= 110) return "Easy";
		return "None";
	}

	/**
	 * Get tier progress breakdown
	 */
	private Map<String, Map<String, Integer>> getTierProgress() {
		Map<String, Map<String, Integer>> tierProgress = new LinkedHashMap<>();
		
		for (String tier : TIER_ENUMS.values()) {
			int completed = (int) allTasks.stream()
				.filter(t -> t.getTier().equals(tier) && t.isCompleted())
				.count();
			
			int total = (int) allTasks.stream()
				.filter(t -> t.getTier().equals(tier))
				.count();
			
			Map<String, Integer> tierData = new HashMap<>();
			tierData.put("completed", completed);
			tierData.put("total", total);
			tierProgress.put(tier.toLowerCase(), tierData);
		}
		
		return tierProgress;
	}

	/**
	 * Get all tasks with full details
	 */
	private List<Map<String, Object>> getAllTasksDetailed() {
		List<Map<String, Object>> tasksList = new ArrayList<>();
		
		for (CombatAchievementTask task : allTasks) {
			tasksList.add(Map.of(
				"id", task.getId(),
				"name", task.getName(),
				"description", task.getDescription(),
				"tier", task.getTier(),
				"type", task.getType(),
				"boss", task.getBoss(),
				"points", task.getPoints(),
				"completed", task.isCompleted()
			));
		}
		
		return tasksList;
	}
}
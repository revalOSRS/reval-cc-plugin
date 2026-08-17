package com.revalclan.combatachievements;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarbitID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.BiConsumer;

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
		4721,  // CA_TASK_COMPLETED_19
		5673   // CA_TASK_COMPLETED_20 (task ids 640+, e.g. Maggot King — added 2026-08-12)
	};

	private final List<CombatAchievementTask> allTasks = new ArrayList<>();

	/**
	 * Sync and get combat achievement data
	 */
	public Map<String, Object> sync() {
		allTasks.clear();

		forEachTaskStruct((struct, tierName) -> {
			CombatAchievementTask task = loadTaskFromStruct(struct, tierName);
			if (task != null) allTasks.add(task);
		});

		// Prefer the game's own CA points varp; fall back to summing completions
		int totalPoints = readCaPointsVarbit();
		if (totalPoints <= 0) totalPoints = calculateTotalPoints();

		Map<String, Object> data = new HashMap<>();
		data.put("currentTier", calculateCurrentTier(totalPoints));
		data.put("totalPoints", totalPoints);
		data.put("tierProgress", getTierProgress());
		data.put("allTasks", getAllTasksDetailed());
		data.put("totalTasksLoaded", allTasks.size());

		return data;
	}

	/**
	 * Current total CA points from the game cache + varps, without building
	 * the full task list.
	 */
	public int computeCurrentTotalPoints() {
		int[] total = {0};
		forEachTaskStruct((struct, tierName) -> {
			if (isTaskCompleted(struct.getIntValue(FIELD_TASK_ID))) {
				total[0] += getPointsForTier(tierName);
			}
		});
		return total[0];
	}

	/**
	 * Single traversal of the game-cache task structs shared by sync() and
	 * computeCurrentTotalPoints(), keeping the two in lockstep. Cache read
	 * failures are logged once here, not swallowed at every layer.
	 */
	private void forEachTaskStruct(BiConsumer<StructComposition, String> visitor) {
		for (Map.Entry<Integer, String> tierEntry : TIER_ENUMS.entrySet()) {
			EnumComposition tierEnum;
			try {
				tierEnum = client.getEnum(tierEntry.getKey());
			} catch (Exception e) {
				log.debug("Failed to read CA tier enum {}: {}", tierEntry.getKey(), e.getMessage());
				continue;
			}
			if (tierEnum == null) continue;

			for (int structId : tierEnum.getIntVals()) {
				try {
					StructComposition struct = client.getStructComposition(structId);
					if (struct != null) {
						visitor.accept(struct, tierEntry.getValue());
					}
				} catch (Exception e) {
					log.debug("Failed to read CA struct {}: {}", structId, e.getMessage());
				}
			}
		}
	}

	/**
	 * Loads a single task from a struct
	 */
	private CombatAchievementTask loadTaskFromStruct(StructComposition struct, String tierName) {
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
	 * The game's own CA points counter — authoritative, and immune to Jagex
	 * adding new completion varps with new task batches (which silently broke
	 * the per-task sum when task ids passed 640). Returns 0 when the varbit
	 * is unavailable so callers can fall back to summing completed tasks.
	 */
	private int readCaPointsVarbit() {
		try {
			return client.getVarbitValue(VarbitID.CA_POINTS);
		} catch (Exception e) {
			return 0;
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
	 * Calculate current tier based on total points. Thresholds are the in-game
	 * points required for each tier's rewards (as of the 2026-08 task additions)
	 * and must be kept in sync with the wiki when Jagex adds tasks.
	 */
	private String calculateCurrentTier(int totalPoints) {
		// In-game unlock points as of the 2026-08-12 rebalance
		if (totalPoints >= 2672) return "Grandmaster";
		if (totalPoints >= 1940) return "Master";
		if (totalPoints >= 1075) return "Elite";
		if (totalPoints >= 419) return "Hard";
		if (totalPoints >= 161) return "Medium";
		if (totalPoints >= 41) return "Easy";
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
	 * Get completed tasks in the slim sync shape.
	 *
	 * Only COMPLETED tasks are sent, each as {name, tier, type} — the backend
	 * keys CA state on name/tier/completed, so descriptions, ids, points and
	 * the ~75% incomplete majority were pure payload bloat (~148KB of it).
	 */
	private List<Map<String, Object>> getAllTasksDetailed() {
		List<Map<String, Object>> tasksList = new ArrayList<>();

		for (CombatAchievementTask task : allTasks) {
			if (!task.isCompleted()) continue;
			tasksList.add(Map.of(
				"name", task.getName(),
				"tier", task.getTier(),
				"type", task.getType()
			));
		}

		return tasksList;
	}
}
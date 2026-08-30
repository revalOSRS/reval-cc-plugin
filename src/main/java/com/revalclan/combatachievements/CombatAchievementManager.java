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


	private static final int FIELD_NAME = 1308;
	private static final int FIELD_TASK_ID = 1306;
	private static final int FIELD_TYPE_ID = 1311;

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

		int totalPoints = currentTotalPoints();
		Map<String, Integer> thresholds = tierThresholds();

		Map<String, Object> data = new HashMap<>();
		data.put("currentTier", calculateCurrentTier(totalPoints, thresholds));
		data.put("totalPoints", totalPoints);
		data.put("tierProgress", getTierProgress());
		data.put("tierThresholds", lowerCaseKeys(thresholds));
		data.put("allTasks", getCompletedTasks());
		data.put("totalTasksLoaded", allTasks.size());

		return data;
	}

	/** Current total CA points: the game's own varp, falling back to a task-struct sum. */
	public int currentTotalPoints() {
		int varp = readCaPointsVarbit();
		if (varp > 0) return varp;
		int[] total = {0};
		forEachTaskStruct((struct, tierName) -> {
			if (isTaskCompleted(struct.getIntValue(FIELD_TASK_ID))) {
				total[0] += getPointsForTier(tierName);
			}
		});
		return total[0];
	}

	/** Shared task-struct traversal for sync() and currentTotalPoints(). */
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
		CombatAchievementTask task = new CombatAchievementTask();
		task.setName(struct.getStringValue(FIELD_NAME));
		task.setTier(tierName);
		task.setType(TYPE_MAP.getOrDefault(struct.getIntValue(FIELD_TYPE_ID), "Unknown"));
		task.setCompleted(isTaskCompleted(struct.getIntValue(FIELD_TASK_ID)));
		return task;
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

	/** The game's own per-tier unlock points, in ladder order (packed into varps CA_THRESHOLDS1-3). */
	private static final int[] THRESHOLD_VARBITS = {
		VarbitID.CA_THRESHOLD_EASY,
		VarbitID.CA_THRESHOLD_MEDIUM,
		VarbitID.CA_THRESHOLD_HARD,
		VarbitID.CA_THRESHOLD_ELITE,
		VarbitID.CA_THRESHOLD_MASTER,
		VarbitID.CA_THRESHOLD_GRANDMASTER
	};

	/**
	 * Points needed to unlock each tier, read from the game's CA_THRESHOLD_* varbits
	 * so a new task batch is reflected the moment it goes live, with no plugin release.
	 * Returns an empty map (=> currentTier "None") if the varps haven't arrived, which
	 * only happens when the rest of the CA varps are missing too and the sync is moot.
	 */
	private Map<String, Integer> tierThresholds() {
		Map<String, Integer> thresholds = new LinkedHashMap<>();
		int previous = 0;
		int i = 0;
		for (String tier : TIER_ENUMS.values()) {
			int value = client.getVarbitValue(THRESHOLD_VARBITS[i++]);
			if (value <= previous) {
				log.warn("CA tier thresholds not available from varbits ({} = {})", tier, value);
				return Collections.emptyMap();
			}
			thresholds.put(tier, value);
			previous = value;
		}
		return thresholds;
	}

	/**
	 * Logs the raw CA varbits the tier ladder is built from. Called on the first
	 * ticks after login so a dev-client run shows whether the game populated them.
	 * @return true once the threshold varbits form a valid ladder
	 */
	public boolean logTierThresholds() {
		Map<String, Integer> thresholds = tierThresholds();
		int points = readCaPointsVarbit();
		StringBuilder status = new StringBuilder();
		int[] statusVarbits = {
			VarbitID.CA_TIER_STATUS_EASY, VarbitID.CA_TIER_STATUS_MEDIUM, VarbitID.CA_TIER_STATUS_HARD,
			VarbitID.CA_TIER_STATUS_ELITE, VarbitID.CA_TIER_STATUS_MASTER, VarbitID.CA_TIER_STATUS_GRANDMASTER
		};
		int i = 0;
		for (String tier : TIER_ENUMS.values()) {
			status.append(tier.toLowerCase()).append('=').append(client.getVarbitValue(statusVarbits[i++])).append(' ');
		}
		log.info("CA thresholds from varbits: {} | CA_POINTS={} | derived tier={} | CA_TIER_STATUS: {}",
			thresholds.isEmpty() ? "NOT POPULATED" : lowerCaseKeys(thresholds),
			points, calculateCurrentTier(points, thresholds), status.toString().trim());
		return !thresholds.isEmpty();
	}

	/** The game's tier ladder with lowercase keys, for event payloads; empty if the varps aren't in yet. */
	public Map<String, Integer> tierThresholdsForPayload() {
		return lowerCaseKeys(tierThresholds());
	}

	/** Highest tier whose unlock point the player has reached, walking the ladder in order. */
	private String calculateCurrentTier(int totalPoints, Map<String, Integer> thresholds) {
		String current = "None";
		for (Map.Entry<String, Integer> tier : thresholds.entrySet()) {
			if (totalPoints < tier.getValue()) break;
			current = tier.getKey();
		}
		return current;
	}

	private static Map<String, Integer> lowerCaseKeys(Map<String, Integer> map) {
		Map<String, Integer> out = new LinkedHashMap<>();
		map.forEach((k, v) -> out.put(k.toLowerCase(), v));
		return out;
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

	/** Only COMPLETED tasks. The server's storage path filters on the completed flag. */
	private List<Map<String, Object>> getCompletedTasks() {
		List<Map<String, Object>> tasksList = new ArrayList<>();

		for (CombatAchievementTask task : allTasks) {
			if (!task.isCompleted()) continue;
			tasksList.add(Map.of(
				"name", task.getName(),
				"tier", task.getTier(),
				"type", task.getType(),
				"completed", true
			));
		}

		return tasksList;
	}
}
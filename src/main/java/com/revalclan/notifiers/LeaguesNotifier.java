package com.revalclan.notifiers;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.leagues.LeaguesConfigResponse;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class LeaguesNotifier extends BaseNotifier {
	private static final String AREA_UNLOCK_PREFIX = "Congratulations, you've unlocked a new area: ";
	private static final String RELIC_UNLOCK_PREFIX = "Congratulations, you've unlocked a new Relic: ";
	private static final Pattern TASK_PATTERN = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) task: (?<task>.+?)\\."
	);
	private static final Pattern MASTERY_PATTERN = Pattern.compile(
		"Congratulations, you've unlocked a new .+ Combat Mastery: (?<type>\\w+) (?<tier>\\w+)\\."
	);

	private static final Map<String, Integer> ROMAN_NUMERALS = Map.of(
		"I", 1, "II", 2, "III", 3, "IV", 4, "V", 5, "VI", 6, "VII", 7, "VIII", 8, "IX", 9, "X", 10
	);

	private static final int CONFIG_RETRY_INTERVAL = 100;
	private static final int CONFIG_MAX_RETRIES = 5;

	@Inject private RevalApiService revalApiService;

	private String currentEventType = "LEAGUES";

	private TreeMap<Integer, String> pointsToTrophy = new TreeMap<>();
	private TreeMap<Integer, Integer> pointsToRelicTier = new TreeMap<>();
	private Map<String, Integer> relicNameToTier = new HashMap<>();
	private Map<String, Integer> difficultyToPoints = new HashMap<>();
	private List<Integer> areaUnlockTasks;

	private int configRetryTicks = 0;
	private int configRetryAttempts = 0;

	@Override
	public boolean isEnabled() {
		return config.notifyLeagues()
			&& filterManager.getFilters().isLeaguesEnabled()
			&& client.getWorldType().contains(WorldType.SEASONAL);
	}

	private boolean hasConfig() {
		LeaguesConfigResponse.LeaguesConfig cfg = getLeaguesConfig();
		return cfg != null
			&& cfg.getLeagueVersion() == client.getVarbitValue(VarbitID.LEAGUE_TYPE);
	}

	@Override
	protected String getEventType() {
		return currentEventType;
	}

	public void onGameTick() {
		if (getLeaguesConfig() != null || configRetryAttempts >= CONFIG_MAX_RETRIES) return;
		if (!client.getWorldType().contains(WorldType.SEASONAL)) return;

		configRetryTicks++;
		if (configRetryTicks >= CONFIG_RETRY_INTERVAL) {
			configRetryTicks = 0;
			configRetryAttempts++;
			fetchConfig();
		}
	}

	public void fetchConfig() {
		revalApiService.fetchLeaguesConfig(
			this::buildLookupMaps,
			error -> {}
		);
	}

	public void reset() {
		configRetryTicks = 0;
		configRetryAttempts = 0;
		revalApiService.clearLeaguesCache();
	}

	public void onChatMessage(String message) {
		if (!isEnabled()) return;

		if (message.startsWith(AREA_UNLOCK_PREFIX)) {
			notifyAreaUnlock(message.substring(AREA_UNLOCK_PREFIX.length(), message.length() - 1));
			return;
		}

		if (message.startsWith(RELIC_UNLOCK_PREFIX)) {
			notifyRelicUnlock(message.substring(RELIC_UNLOCK_PREFIX.length(), message.length() - 1));
			return;
		}

		Matcher taskMatcher = TASK_PATTERN.matcher(message);
		if (taskMatcher.find()) {
			notifyTaskCompletion(taskMatcher.group("tier"), taskMatcher.group("task"));
			return;
		}

		Matcher masteryMatcher = MASTERY_PATTERN.matcher(message);
		if (masteryMatcher.find()) {
			notifyCombatMastery(masteryMatcher.group("type"), masteryMatcher.group("tier"));
		}
	}

	private void notifyTaskCompletion(String tierName, String taskName) {
		int totalPoints = client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED);
		int tasksCompleted = client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED);

		currentEventType = "LEAGUES_TASK";
		Map<String, Object> data = createBaseData();
		data.put("taskName", taskName);
		data.put("difficultyTier", tierName);
		data.put("totalPoints", totalPoints);
		data.put("tasksCompleted", tasksCompleted);

		if (hasConfig()) {
			int taskPoints = difficultyToPoints.getOrDefault(tierName.toLowerCase(), 0);

			String trophyEarned = null;
			Map.Entry<Integer, String> currentTrophy = pointsToTrophy.floorEntry(totalPoints);
			if (currentTrophy != null) {
				Map.Entry<Integer, String> previousTrophy = pointsToTrophy.floorEntry(totalPoints - taskPoints);
				if (previousTrophy == null || currentTrophy.getKey() > previousTrophy.getKey()) {
					trophyEarned = currentTrophy.getValue();
				}
			}

			Integer nextTrophyThreshold = pointsToTrophy.ceilingKey(totalPoints + 1);
			Integer nextRelicThreshold = pointsToRelicTier.ceilingKey(totalPoints + 1);
			data.put("taskPoints", taskPoints);
			data.put("tasksUntilNextArea", computeTasksUntilNextArea(tasksCompleted));
			data.put("pointsUntilNextRelic", nextRelicThreshold != null ? nextRelicThreshold - totalPoints : null);
			data.put("pointsUntilNextTrophy", nextTrophyThreshold != null ? nextTrophyThreshold - totalPoints : null);
			data.put("trophyEarned", trophyEarned);
		}

		sendNotification(data);
	}

	private void notifyRelicUnlock(String relicName) {
		int currentPoints = client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED);

		currentEventType = "LEAGUES_RELIC";
		Map<String, Object> data = createBaseData();
		data.put("relicName", relicName);
		data.put("currentPoints", currentPoints);

		if (hasConfig()) {
			Integer relicTier = relicNameToTier.getOrDefault(relicName, 0);
			int requiredPoints = 0;
			for (Map.Entry<Integer, Integer> entry : pointsToRelicTier.entrySet()) {
				if (entry.getValue().equals(relicTier)) {
					requiredPoints = entry.getKey();
					break;
				}
			}

			Integer nextTierThreshold = pointsToRelicTier.ceilingKey(currentPoints + 1);
			data.put("relicTier", relicTier);
			data.put("requiredPoints", requiredPoints);
			data.put("pointsUntilNextTier", nextTierThreshold != null ? nextTierThreshold - currentPoints : null);
		}

		sendNotification(data);
	}

	private void notifyAreaUnlock(String areaName) {
		int tasksCompleted = client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED);

		currentEventType = "LEAGUES_AREA";
		Map<String, Object> data = createBaseData();
		data.put("areaName", areaName);
		data.put("areaIndex", computeAreasUnlocked());
		data.put("tasksCompleted", tasksCompleted);

		if (hasConfig()) {
			data.put("tasksUntilNextArea", computeTasksUntilNextArea(tasksCompleted));
		}

		sendNotification(data);
	}

	private void notifyCombatMastery(String type, String romanTier) {
		Integer tier = ROMAN_NUMERALS.get(romanTier);
		if (tier == null) return;

		currentEventType = "LEAGUES_MASTERY";
		Map<String, Object> data = createBaseData();
		data.put("masteryType", type);
		data.put("masteryTier", tier);

		sendNotification(data);
	}

	private Map<String, Object> createBaseData() {
		Map<String, Object> data = new HashMap<>();
		data.put("seasonalWorld", true);
		LeaguesConfigResponse.LeaguesConfig cfg = getLeaguesConfig();
		if (cfg != null) {
			data.put("leagueName", cfg.getLeagueName());
		}
		return data;
	}

	private LeaguesConfigResponse.LeaguesConfig getLeaguesConfig() {
		return revalApiService.getCachedLeaguesConfig();
	}

	private int computeAreasUnlocked() {
		try {
			if (client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_4) > 0) return 3;
			if (client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_3) > 0) return 2;
			if (client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_2) > 0) return 1;
			if (client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_1) > 0) return 0;
		} catch (Exception e) {
			// Area selection varbits not available, use task-based fallback
		}

		if (areaUnlockTasks != null) {
			int tasksCompleted = client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED);
			int unlocked = 0;
			for (int threshold : areaUnlockTasks) {
				if (tasksCompleted >= threshold) unlocked++;
			}
			return unlocked;
		}

		return 0;
	}

	private Integer computeTasksUntilNextArea(int tasksCompleted) {
		if (areaUnlockTasks == null) return null;
		for (int threshold : areaUnlockTasks) {
			if (tasksCompleted < threshold) return threshold - tasksCompleted;
		}
		return null;
	}

	private void buildLookupMaps(LeaguesConfigResponse.LeaguesConfig cfg) {
		pointsToTrophy.clear();
		for (LeaguesConfigResponse.Trophy trophy : cfg.getTrophies()) {
			pointsToTrophy.put(trophy.getPointsRequired(), trophy.getName());
		}

		pointsToRelicTier.clear();
		relicNameToTier.clear();
		for (LeaguesConfigResponse.RelicTier tier : cfg.getRelicTiers()) {
			pointsToRelicTier.put(tier.getPointsRequired(), tier.getTier());
			for (String relicName : tier.getRelics()) {
				relicNameToTier.put(relicName, tier.getTier());
			}
		}

		difficultyToPoints.clear();
		for (LeaguesConfigResponse.TaskDifficulty diff : cfg.getTaskDifficulties()) {
			difficultyToPoints.put(diff.getName().toLowerCase(), diff.getPoints());
		}

		areaUnlockTasks = cfg.getAreaUnlockTasks();
	}
}

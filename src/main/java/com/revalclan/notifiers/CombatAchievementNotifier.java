package com.revalclan.notifiers;

import com.revalclan.combatachievements.CombatAchievementManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class CombatAchievementNotifier extends BaseNotifier {
	@Inject
	private CombatAchievementManager combatAchievementManager;

	private static final Pattern CA_PATTERN = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) combat task: (?<task>.+)\\.",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern POINTS_SUFFIX = Pattern.compile("\\s+\\(\\d+ points?\\)$");

	@Override 
	public boolean isEnabled() {
		return config.notifyCombatAchievement() && filterManager.getFilters().isCombatAchievementEnabled();
	}

	@Override
	protected String getEventType() {
		return "COMBAT_ACHIEVEMENT";
	}

	public void onChatMessage(String message) {
		if (!isEnabled()) return;

		Matcher matcher = CA_PATTERN.matcher(message);
		if (matcher.find()) {
			String tier = matcher.group("tier");
			String task = matcher.group("task");

			handleCombatAchievement(tier, task);
		}
	}

	private void handleCombatAchievement(String tier, String task) {
		task = POINTS_SUFFIX.matcher(task).replaceAll("");

		Map<String, Object> caData = new HashMap<>();
		caData.put("tier", tier);
		caData.put("task", task);

		// v2.17+: send the game's own CA point total so the backend doesn't have
		// to re-derive it from every stored completion row
		try {
			int totalPoints = combatAchievementManager.computeCurrentTotalPoints();
			if (totalPoints > 0) {
				caData.put("totalPoints", totalPoints);
			}
		} catch (Exception ignored) {}

		sendNotification(caData);
	}
}


package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class CombatAchievementNotifier extends BaseNotifier {
	private static final Pattern CA_PATTERN = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) combat task: (?<task>.+)\\.",
		Pattern.CASE_INSENSITIVE
	);

	@Inject private RevalClanConfig config;

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
		task = task.replaceAll("\\s+\\(\\d+ points?\\)$", "");

		Map<String, Object> caData = new HashMap<>();
		caData.put("tier", tier);
		caData.put("task", task);

		sendNotification(caData);
	}
}


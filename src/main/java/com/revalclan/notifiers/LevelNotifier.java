package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Notifies on level ups
 */
@Slf4j
@Singleton
public class LevelNotifier extends BaseNotifier
{
	@Inject
	private RevalClanConfig config;

	private final Map<Skill, Integer> previousLevels = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> previousXp = new EnumMap<>(Skill.class);

	@Override
	public boolean isEnabled()
	{
		return config.enableWebhook() && config.notifyLevel();
	}

	@Override
	protected String getEventType()
	{
		return "LEVEL";
	}

	public void onStatChanged(StatChanged event)
	{
		if (!isEnabled()) return;

		Skill skill = event.getSkill();
		int newLevel = event.getLevel();
		int newXp = event.getXp();

		// Initialize if first time seeing this skill
		if (!previousLevels.containsKey(skill))
		{
			previousLevels.put(skill, newLevel);
			previousXp.put(skill, newXp);
			return;
		}

		int oldLevel = previousLevels.get(skill);
		int oldXp = previousXp.get(skill);

		// Update tracking
		previousLevels.put(skill, newLevel);
		previousXp.put(skill, newXp);

		// Check for level up
		if (newLevel > oldLevel && newXp > oldXp)
		{
			handleLevelUp(skill, newLevel, newXp);
		}
	}

	private void handleLevelUp(Skill skill, int level, int xp)
	{
		int totalLevel = client.getTotalLevel();
		long totalXp = client.getOverallExperience();
		int combatLevel = client.getLocalPlayer() != null ? client.getLocalPlayer().getCombatLevel() : 0;

		Map<String, Object> levelData = new HashMap<>();
		levelData.put("player", getPlayerName());
		levelData.put("skill", skill.getName());
		levelData.put("level", level);
		levelData.put("experience", xp);
		levelData.put("totalLevel", totalLevel);
		levelData.put("totalExperience", totalXp);
		levelData.put("combatLevel", combatLevel);

		log.info("{} leveled {} to {}", getPlayerName(), skill.getName(), level);

		sendNotification(config.webhookUrl(), levelData);
	}

	public void reset()
	{
		previousLevels.clear();
		previousXp.clear();
	}
}


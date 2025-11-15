package com.revalclan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("revalclan")
public interface RevalClanConfig extends Config {
	@ConfigSection(
		name = "Event Notifications",
		description = "Toggle individual event types",
		position = 1
	)
	String eventsSection = "eventsSection";

	// Event type toggles
	@ConfigItem(
		keyName = "notifyLoot",
		name = "Loot Drops",
		description = "Send notifications for valuable loot drops",
		section = eventsSection,
		position = 1
	)
	default boolean notifyLoot() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyPet",
		name = "Pet Drops",
		description = "Send notifications when you receive a pet",
		section = eventsSection,
		position = 2
	)
	default boolean notifyPet() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyQuest",
		name = "Quest Completions",
		description = "Send notifications when you complete a quest",
		section = eventsSection,
		position = 3
	)
	default boolean notifyQuest() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyLevel",
		name = "Level Ups",
		description = "Send notifications when you level up",
		section = eventsSection,
		position = 4
	)
	default boolean notifyLevel() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyKillCount",
		name = "Kill Counts",
		description = "Send notifications for boss kill counts",
		section = eventsSection,
		position = 5
	)
	default boolean notifyKillCount() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyClue",
		name = "Clue Scrolls",
		description = "Send notifications when you complete a clue scroll",
		section = eventsSection,
		position = 6
	)
	default boolean notifyClue() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyDiary",
		name = "Achievement Diaries",
		description = "Send notifications when you complete an achievement diary",
		section = eventsSection,
		position = 7
	)
	default boolean notifyDiary() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyCombatAchievement",
		name = "Combat Achievements",
		description = "Send notifications when you complete a combat achievement",
		section = eventsSection,
		position = 8
	)
	default boolean notifyCombatAchievement() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyCollection",
		name = "Collection Log",
		description = "Send notifications when you add items to collection log",
		section = eventsSection,
		position = 9
	)
	default boolean notifyCollection() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyDeath",
		name = "Player Deaths",
		description = "Send notifications when you die",
		section = eventsSection,
		position = 10
	)
	default boolean notifyDeath() {
		return true;
	}

	@ConfigItem(
		keyName = "deathIncludeScreenshot",
		name = "Death Screenshots",
		description = "Include a screenshot with death notifications",
		section = eventsSection,
		position = 11
	)
	default boolean deathIncludeScreenshot() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyDetailedKill",
		name = "Detailed Kills",
		description = "Send detailed kill tracking (damage, weapons, specs)",
		section = eventsSection,
		position = 12
	)
	default boolean notifyDetailedKill() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyAreaEntry",
		name = "Area Entry",
		description = "Send notifications when entering specific regions",
		section = eventsSection,
		position = 13
	)
	default boolean notifyAreaEntry() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyEmote",
		name = "Emotes",
		description = "Send notifications when performing emotes",
		section = eventsSection,
		position = 14
	)
	default boolean notifyEmote() {
		return true;
	}
}

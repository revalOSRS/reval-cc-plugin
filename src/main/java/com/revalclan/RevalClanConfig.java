package com.revalclan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("revalclan")
public interface RevalClanConfig extends Config {

	// ── Panel Settings ─────────────────────────────────────────────────
	@ConfigSection(
		name = "Panel Settings",
		description = "Customize the Reval side panel appearance",
		position = 0
	)
	String panelSection = "panelSection";

	@ConfigItem(
		keyName = "hideCompletedItems",
		name = "Hide completed items",
		description = "Only show incomplete milestones, combat achievements and collection log tiers on the profile",
		section = panelSection,
		position = 0
	)
	default boolean hideCompletedItems() {
		return false;
	}

	// ── Event Notifications ────────────────────────────────────────────
	@ConfigSection(
		name = "Event Notifications",
		description = "Disabling notifiers will stop the plugin from tracking and sending the corresponding events to Reval. Some features will not work as expected. Disable at your own discretion.",
		position = 1,
		closedByDefault = true
	)
	String eventsSection = "eventsSection";

	@ConfigItem(
		keyName = "notifyLoot",
		name = "Loot Drops",
		description = "Track valuable loot drops",
		section = eventsSection,
		position = 1,
		warning = "Disabling this will stop loot tracking. Your drop points and loot history will not update."
	)
	default boolean notifyLoot() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyPet",
		name = "Pet Drops",
		description = "Track pet drops",
		section = eventsSection,
		position = 2,
		warning = "Disabling this will stop pet tracking. Pet points will not update."
	)
	default boolean notifyPet() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyQuest",
		name = "Quest Completions",
		description = "Track quest completions",
		section = eventsSection,
		position = 3,
		warning = "Disabling this will stop quest tracking."
	)
	default boolean notifyQuest() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyLevel",
		name = "Level Ups",
		description = "Track level ups",
		section = eventsSection,
		position = 4,
		warning = "Disabling this will stop level-up tracking. Milestone points tied to levels will not update."
	)
	default boolean notifyLevel() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyKillCount",
		name = "Kill Counts",
		description = "Track boss kill counts",
		section = eventsSection,
		position = 5,
		warning = "Disabling this will stop kill count tracking."
	)
	default boolean notifyKillCount() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyClue",
		name = "Clue Scrolls",
		description = "Track clue scroll completions",
		section = eventsSection,
		position = 6,
		warning = "Disabling this will stop clue scroll tracking."
	)
	default boolean notifyClue() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyDiary",
		name = "Achievement Diaries",
		description = "Track achievement diary completions",
		section = eventsSection,
		position = 7,
		warning = "Disabling this will stop diary tracking."
	)
	default boolean notifyDiary() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyCombatAchievement",
		name = "Combat Achievements",
		description = "Track combat achievement completions",
		section = eventsSection,
		position = 8,
		warning = "Disabling this will stop combat achievement tracking. CA points will not update."
	)
	default boolean notifyCombatAchievement() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyCollection",
		name = "Collection Log",
		description = "Track collection log additions",
		section = eventsSection,
		position = 9,
		warning = "Disabling this will stop collection log tracking. Collection log points will not update."
	)
	default boolean notifyCollection() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyDeath",
		name = "Player Deaths",
		description = "Track player deaths",
		section = eventsSection,
		position = 10,
		warning = "Disabling this will stop death tracking."
	)
	default boolean notifyDeath() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyDetailedKill",
		name = "Detailed Kills",
		description = "Track detailed kill data (damage, weapons, specs)",
		section = eventsSection,
		position = 12,
		warning = "Disabling this will stop detailed kill tracking."
	)
	default boolean notifyDetailedKill() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyEmote",
		name = "Emotes",
		description = "Track emote usage",
		section = eventsSection,
		position = 14
	)
	default boolean notifyEmote() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyChat",
		name = "System Chat Messages",
		description = "Track system chat messages (game events, broadcasts, etc.)",
		section = eventsSection,
		position = 15
	)
	default boolean notifyChat() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyMusic",
		name = "Music Played",
		description = "Track music tracks played",
		section = eventsSection,
		position = 17
	)
	default boolean notifyMusic() {
		return true;
	}
}

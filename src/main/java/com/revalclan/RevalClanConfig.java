package com.revalclan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Which game events get tracked and sent to Reval is decided by the backend
 * (see FilterManager), not by the player. The only per-player switches left
 * here are the ones that change what the player themself sees or shares.
 */
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

	// ── Profile cards ──────────────────────────────────────────────────
	@ConfigSection(
		name = "Profile cards",
		description = "Where the 'View Reval Profile' right-click option appears",
		position = 1
	)
	String profileCardsSection = "profileCardsSection";

	@ConfigItem(
		keyName = "profileCardsOnPlayers",
		name = "Players",
		description = "View Reval Profile when right-clicking a clan member in the world (off by default to keep the menu short)",
		section = profileCardsSection,
		position = 0
	)
	default boolean profileCardsOnPlayers() {
		return false;
	}

	@ConfigItem(
		keyName = "profileCardsInChat",
		name = "Chat",
		description = "View Reval Profile when right-clicking a clan member's name in chat",
		section = profileCardsSection,
		position = 1
	)
	default boolean profileCardsInChat() {
		return true;
	}

	@ConfigItem(
		keyName = "profileCardsInClanList",
		name = "Clan list",
		description = "View Reval Profile when right-clicking a member in the clan member list",
		section = profileCardsSection,
		position = 2
	)
	default boolean profileCardsInClanList() {
		return true;
	}

	// ── Events ─────────────────────────────────────────────────────────
	@ConfigSection(
		name = "Events",
		description = "Clan event features",
		position = 2
	)
	String clanEventsSection = "clanEventsSection";

	/**
	 * Keeps the old "teamNameColors" key on purpose: this used to be the single
	 * toggle for both surfaces, so anyone who had already switched colors off
	 * stays switched off for the clan list instead of having them reappear.
	 */
	@ConfigItem(
		keyName = "teamNameColors",
		name = "Team colors: clan list",
		description = "Color clan members' names by their event team in the clan member list",
		section = clanEventsSection,
		position = 0
	)
	default boolean teamColorsInClanList() {
		return true;
	}

	@ConfigItem(
		keyName = "teamColorsInChat",
		name = "Team colors: chat",
		description = "Color clan members' names by their event team in clan chat (off by default - chat is busy enough)",
		section = clanEventsSection,
		position = 1
	)
	default boolean teamColorsInChat() {
		return false;
	}

	// ── Notifications ──────────────────────────────────────────────────
	@ConfigSection(
		name = "Notifications",
		description = "What the plugin shows you and what it shares with the clan",
		position = 3
	)
	String eventsSection = "eventsSection";

	@ConfigItem(
		keyName = "showAnnouncements",
		name = "Show clan notifications",
		description = "Show Reval announcements and notifications in chat",
		section = eventsSection,
		position = 0
	)
	default boolean showAnnouncements() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyDeath",
		name = "Send player deaths to Discord",
		description = "Post your deaths (and who killed you) to the clan Discord",
		section = eventsSection,
		position = 1
	)
	default boolean notifyDeath() {
		return true;
	}

	@ConfigItem(
		keyName = "notifyLeagues",
		name = "Leagues Events",
		description = "Track Leagues events (tasks, relics, areas, combat masteries)",
		section = eventsSection,
		position = 2
	)
	default boolean notifyLeagues() {
		return true;
	}
}

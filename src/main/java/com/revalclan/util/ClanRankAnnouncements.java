package com.revalclan.util;

import com.revalclan.RevalClanConfig;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/** Local clan-system messages for promotions observed in the authoritative clan roster. */
@Singleton
public class ClanRankAnnouncements {
	private final Client client;
	private final RevalClanConfig config;
	private final ChatMessageManager chatMessageManager;
	private final ChatIconManager chatIconManager;
	private Map<String, Integer> previousRanks;

	@Inject
	public ClanRankAnnouncements(Client client, RevalClanConfig config,
		ChatMessageManager chatMessageManager, ChatIconManager chatIconManager) {
		this.client = client;
		this.config = config;
		this.chatMessageManager = chatMessageManager;
		this.chatIconManager = chatIconManager;
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (client.getGameState() != GameState.LOGGED_IN) return;
		ClanSettings settings = client.getClanSettings();
		ClanChannel channel = client.getClanChannel();
		if (settings == null || channel == null
			|| !ClanValidator.REQUIRED_CLAN_NAME.equalsIgnoreCase(settings.getName())
			|| !ClanValidator.REQUIRED_CLAN_NAME.equalsIgnoreCase(channel.getName())) {
			reset();
			return;
		}

		Map<String, Integer> currentRanks = new HashMap<>();
		for (ClanMember member : settings.getMembers()) {
			if (member.getName() == null || member.getRank() == null) continue;
			String nameKey = Text.standardize(member.getName());
			int rank = member.getRank().getRank();
			currentRanks.put(nameKey, rank);
			Integer previous = previousRanks == null ? null : previousRanks.get(nameKey);
			// Initial loads and newly joined members are baselines, not promotions.
			if (previous != null && previous >= 0 && rank > previous && config.showAnnouncements()) {
				announce(member, settings.titleForRank(member.getRank()));
			}
		}
		// Track changes even while announcements are disabled; enabling must not replay them.
		previousRanks = currentRanks;
	}

	private void announce(ClanMember member, ClanTitle title) {
		String rankName = title == null || title.getName() == null || title.getName().isEmpty()
			? "rank " + member.getRank().getRank() : title.getName();
		int icon = title == null ? -1 : chatIconManager.getIconNumber(title);
		String iconTag = icon >= 0 ? "<img=" + icon + "> " : "";
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CLAN_MESSAGE)
			.sender("Reval")
			.runeLiteFormattedMessage(Text.escapeJagex(member.getName()) + " has been promoted to "
				+ iconTag + Text.escapeJagex(rankName) + ".")
			.build());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.LOGGING_IN
			|| state == GameState.HOPPING || state == GameState.CONNECTION_LOST) reset();
	}

	public void reset() {
		previousRanks = null;
	}
}

package com.revalclan.playercards;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.playercards.ProfileCardResponse;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Chat commands backed by the profile-card endpoint: !revalwins shows the
 * sender's event wins and competition podium counts with trophy icons.
 */
@Slf4j
@Singleton
public class ProfileChatCommands {
	private static final String WINS_COMMAND = "!revalwins";

	private final Client client;
	private final RevalApiService apiService;
	private final ChatCommandManager chatCommandManager;
	private final ChatTrophyIcons icons;

	@Inject
	public ProfileChatCommands(Client client, RevalApiService apiService,
							   ChatCommandManager chatCommandManager, ChatTrophyIcons icons) {
		this.client = client;
		this.apiService = apiService;
		this.chatCommandManager = chatCommandManager;
		this.icons = icons;
	}

	public void startUp() {
		chatCommandManager.registerCommandAsync(WINS_COMMAND, this::winsCommand);
	}

	public void shutDown() {
		chatCommandManager.unregisterCommand(WINS_COMMAND);
	}

	private void winsCommand(ChatMessage chatMessage, String message) {
		String name = senderName(chatMessage);
		if (name.isEmpty()) {
			return;
		}
		MessageNode messageNode = chatMessage.getMessageNode();
		apiService.fetchProfileCard(name,
			response -> {
				ProfileCardResponse.CardData data = response.getData();
				if (data == null) {
					return;
				}
				int wins = data.getEventWins() != null ? data.getEventWins().size() : 0;
				ProfileCardResponse.Podiums podiums = data.getCompetitionPodiums();
				int first = podiums != null && podiums.getFirst() != null ? podiums.getFirst() : 0;
				int second = podiums != null && podiums.getSecond() != null ? podiums.getSecond() : 0;
				int third = podiums != null && podiums.getThird() != null ? podiums.getThird() : 0;

				String text = icons.star() + "<col=ffb83f> " + wins + (wins == 1 ? " event win" : " event wins")
					+ "</col> <col=9f9f9f>|</col> "
					+ icons.goldTrophy() + "<col=ffb83f> " + first + "</col>  "
					+ icons.silverTrophy() + "<col=ffb83f> " + second + "</col>  "
					+ icons.bronzeTrophy() + "<col=ffb83f> " + third + "</col>";
				messageNode.setRuneLiteFormatMessage(text);
				client.refreshChat();
			},
			error -> log.debug("!revalwins lookup failed for {}", name, error));
	}

	private String senderName(ChatMessage chatMessage) {
		String name = chatMessage.getName();
		if (name == null || name.isEmpty()) {
			// Own message in some chat modes carries no name; use the local player
			name = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		}
		return name != null ? Text.removeTags(name).replace('\u00A0', ' ') : "";
	}
}

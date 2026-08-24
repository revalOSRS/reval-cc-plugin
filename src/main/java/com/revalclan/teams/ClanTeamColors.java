package com.revalclan.teams;

import com.revalclan.RevalClanConfig;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;

/**
 * Colors clan members' names by their active event team, in clan chat and in
 * the clan sidepanel member list, and tags chat lines with the team name
 * after the clan name. Purely cosmetic; does nothing when no event is
 * active.
 */
@Singleton
public class ClanTeamColors {
	private final Client client;
	private final ClientThread clientThread;
	private final ActiveTeamColors activeTeams;
	private final RevalClanConfig config;

	@Inject
	public ClanTeamColors(Client client, ClientThread clientThread, ActiveTeamColors activeTeams, RevalClanConfig config) {
		this.client = client;
		this.clientThread = clientThread;
		this.activeTeams = activeTeams;
		this.config = config;
	}

	/** Recolor an already-open sidepanel; rebuilds are caught by the script hook. */
	public void startUp() {
		if (!config.teamNameColors()) {
			return;
		}
		activeTeams.ensureFresh();
		clientThread.invoke(this::recolorSidepanel);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (event.getType() != ChatMessageType.CLAN_CHAT || !config.teamNameColors()) {
			return;
		}
		activeTeams.ensureFresh();
		String plain = Text.removeTags(event.getName());
		Color color = activeTeams.teamColorFor(plain);
		if (color == null) {
			return;
		}
		event.getMessageNode().setName(ColorUtil.wrapWithColorTag(event.getName(), chatTone(color)));
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == ScriptID.CLAN_SIDEPANEL_DRAW && config.teamNameColors()) {
			activeTeams.ensureFresh();
			recolorSidepanel();
		}
	}

	private void recolorSidepanel() {
		Widget list = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (list == null) {
			return;
		}
		Widget[] children = list.getDynamicChildren();
		if (children == null) {
			return;
		}
		for (Widget child : children) {
			String text = child.getText();
			// Rows hold a name widget and a world widget; skip the worlds
			if (text == null || text.isEmpty() || text.matches("W\\d+")) {
				continue;
			}
			Color color = activeTeams.teamColorFor(Text.removeTags(text));
			if (color != null) {
				child.setTextColor(color.getRGB() & 0xFFFFFF);
			}
		}
	}

	/** Chat draws the same hex noticeably brighter than the sidepanel; deepen it there. */
	private static Color chatTone(Color color) {
		return new Color(
			Math.round(color.getRed() * 0.78f),
			Math.round(color.getGreen() * 0.78f),
			Math.round(color.getBlue() * 0.78f));
	}
}

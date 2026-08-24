package com.revalclan.teams;

import com.revalclan.RevalClanConfig;
import com.revalclan.util.ClanSidepanel;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;

/**
 * Colors clan members' names by their active event team, in clan chat and in
 * the clan sidepanel member list. Purely cosmetic; does nothing when no event
 * is active or the config toggle is off.
 */
@Singleton
public class ClanTeamColors {
	/** The list renders names white by default; used to restore on disable. */
	private static final int DEFAULT_NAME_COLOR = 0xFFFFFF;

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
		activeTeams.refresh();
		clientThread.invoke(this::recolorSidepanel);
	}

	/** Restore default name colors; chat lines already colored stay as sent. */
	public void shutDown() {
		clientThread.invoke(this::resetSidepanel);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!"revalclan".equals(event.getGroup()) || !"teamNameColors".equals(event.getKey())) {
			return;
		}
		if (config.teamNameColors()) {
			activeTeams.refresh();
			clientThread.invoke(this::recolorSidepanel);
		} else {
			clientThread.invoke(this::resetSidepanel);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (event.getType() != ChatMessageType.CLAN_CHAT || !config.teamNameColors()) {
			return;
		}
		activeTeams.refresh();
		Color color = activeTeams.teamColorFor(event.getName());
		if (color != null) {
			event.getMessageNode().setName(ColorUtil.wrapWithColorTag(event.getName(), chatTone(color)));
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == ScriptID.CLAN_SIDEPANEL_DRAW && config.teamNameColors()) {
			activeTeams.refresh();
			recolorSidepanel();
		}
	}

	private void recolorSidepanel() {
		ClanSidepanel.eachNameRow(client, child -> {
			Color color = activeTeams.teamColorFor(child.getText());
			if (color != null) {
				child.setTextColor(color.getRGB() & 0xFFFFFF);
			}
		});
	}

	private void resetSidepanel() {
		ClanSidepanel.eachNameRow(client, child -> child.setTextColor(DEFAULT_NAME_COLOR));
	}

	/** Chat draws the same hex noticeably brighter than the sidepanel; deepen it there. */
	private static Color chatTone(Color color) {
		return new Color(
			Math.round(color.getRed() * 0.78f),
			Math.round(color.getGreen() * 0.78f),
			Math.round(color.getBlue() * 0.78f));
	}
}

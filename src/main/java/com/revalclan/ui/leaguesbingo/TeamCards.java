package com.revalclan.ui.leaguesbingo;

import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Payload;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Team;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.UnlockedRegion;
import com.revalclan.ui.components.AccentCard;
import com.revalclan.ui.components.Badge;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.components.ColorDot;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.DateTimeUtil;
import net.runelite.client.ui.FontManager;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

/** The event summary, the standings rows and the team chip above the boards. */
final class TeamCards {
	private static final int NAME_WIDTH = 112;

	private TeamCards() {
	}

	static JComponent eventSummary(Payload payload) {
		AccentCard card = new AccentCard(UIConstants.ACCENT_GREEN, false);
		String status = payload.getEvent() != null ? payload.getEvent().getStatus() : null;
		boolean live = "active".equalsIgnoreCase(status);

		JPanel top = Labels.row();
		top.add(new Badge(live ? "LIVE" : status != null ? status.toUpperCase() : "EVENT", live ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_SECONDARY), BorderLayout.WEST);
		top.add(Labels.small(payload.getTeams().size() + " teams", UIConstants.TEXT_SECONDARY), BorderLayout.EAST);
		card.add(top);

		String endDate = payload.getEvent() != null ? payload.getEvent().getEndDate() : null;
		if (endDate != null) {
			card.add(Box.createVerticalStrut(6));
			card.add(Labels.small((live ? "Ends " : "Ended ") + DateTimeUtil.formatShort(endDate), UIConstants.TEXT_MUTED));
		}
		if (payload.isTilesHidden()) {
			card.add(Box.createVerticalStrut(6));
			String reveal = payload.getTilesRevealAt() != null ? "Tiles reveal " + DateTimeUtil.formatShort(payload.getTilesRevealAt()) : "Tiles not revealed yet";
			card.add(Labels.small(reveal, UIConstants.ACCENT_GOLD));
		}
		return card;
	}

	/** Standings row: rank, name, YOU badge, score, and the tiles / regions / x2 line. */
	static JComponent row(Team team, int rank, boolean mine, Runnable onOpen) {
		Color color = LeaguesBingoPanel.teamColor(team);
		AccentCard card = new AccentCard(color, true);

		JPanel top = Labels.row();
		JPanel left = Labels.flow();
		left.add(Labels.small("#" + rank, UIConstants.TEXT_MUTED));
		left.add(Labels.bold(Labels.ellipsize(team.getName(), FontManager.getRunescapeBoldFont(), mine ? NAME_WIDTH - 28 : NAME_WIDTH), UIConstants.TEXT_PRIMARY));
		if (mine) left.add(new Badge("YOU", color));
		top.add(left, BorderLayout.WEST);
		top.add(Labels.bold(team.getScore() + " pts", UIConstants.ACCENT_GOLD), BorderLayout.EAST);
		card.add(top);

		card.add(Box.createVerticalStrut(4));
		int regions = team.getUnlockedRegions().size();
		int doubled = 0;
		for (UnlockedRegion u : team.getUnlockedRegions()) {
			if (u.getBoardCompletedAt() != null) doubled++;
		}
		card.add(Labels.small(team.getUniqueCompletedTiles() + " tiles  |  " + regions + " region" + (regions != 1 ? "s" : "")
			+ (doubled > 0 ? "  |  " + doubled + " x2" : ""), UIConstants.TEXT_SECONDARY));

		Clickable.onPress(card, onOpen, card::setHovered);
		return card;
	}

	/** Colour dot, name and score; with tiles, regions and open picks when detailed. */
	static JComponent chip(Team team, Payload payload, boolean detailed) {
		Color color = LeaguesBingoPanel.teamColor(team);
		AccentCard card = new AccentCard(color, false);

		JPanel top = Labels.row();
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		left.setOpaque(false);
		left.add(new ColorDot(color, 10));
		left.add(Labels.bold(Labels.ellipsize(team.getName(), FontManager.getRunescapeBoldFont(), NAME_WIDTH), UIConstants.TEXT_PRIMARY));
		top.add(left, BorderLayout.WEST);
		top.add(Labels.bold(team.getScore() + " pts", UIConstants.ACCENT_GOLD), BorderLayout.EAST);
		card.add(top);

		if (detailed) {
			card.add(Box.createVerticalStrut(4));
			int tokens = team.availableTokens();
			card.add(Labels.small(team.getUniqueCompletedTiles() + " tiles  |  " + team.getUnlockedRegions().size() + "/" + payload.getBoards().size() + " regions"
				+ (tokens > 0 ? "  |  " + tokens + " pick" + (tokens != 1 ? "s" : "") + " open" : ""), UIConstants.TEXT_SECONDARY));
		}
		return card;
	}
}

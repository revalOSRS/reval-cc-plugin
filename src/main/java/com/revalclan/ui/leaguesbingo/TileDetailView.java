package com.revalclan.ui.leaguesbingo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Completion;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.RequirementProgress;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Requirements;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Team;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Tile;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.TileProgress;
import com.revalclan.ui.components.AccentCard;
import com.revalclan.ui.components.Badge;
import com.revalclan.ui.components.DotIcon;
import com.revalclan.ui.components.ProgressBar;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.DateTimeUtil;
import com.revalclan.util.Json;
import com.revalclan.util.NumberFmt;
import com.revalclan.util.TextWrap;
import net.runelite.client.ui.FontManager;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The card under the grid: one tile's task, status, requirements and who contributed. */
final class TileDetailView {
	private static final int TEXT_WIDTH = 158;
	private static final int DOT_SIZE = 9;
	private static final int DOT_GAP = 6;
	private static final int REQ_INDENT = DOT_SIZE + DOT_GAP;
	private static final int MAX_OPTIONS = 8;
	private static final int MAX_CONTRIBUTORS = 3;

	private TileDetailView() {
	}

	static JComponent build(Team team, Tile tile, boolean locked) {
		int percent = BoardStats.tilePercent(team, tile);
		boolean completed = percent >= 100;
		Color status = locked ? UIConstants.TEXT_MUTED : completed ? TileCell.COMPLETED : percent > 0 ? TileCell.IN_PROGRESS : UIConstants.TEXT_MUTED;
		AccentCard card = new AccentCard(status, false);

		JPanel top = Labels.row();
		top.add(Labels.small(tile.getPosition(), UIConstants.TEXT_MUTED), BorderLayout.WEST);
		top.add(Labels.bold(tile.getPoints() + " pt" + (tile.getPoints() != 1 ? "s" : ""), UIConstants.ACCENT_GOLD), BorderLayout.EAST);
		card.add(top);
		card.add(Box.createVerticalStrut(4));
		card.add(Labels.wrapped(tile.getTask(), FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY, TEXT_WIDTH));

		card.add(Box.createVerticalStrut(6));
		JPanel meta = Labels.flow();
		meta.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (tile.getDifficulty() != null) meta.add(Badge.difficulty(tile.getDifficulty()));
		if (tile.getCategory() != null) meta.add(Badge.category(tile.getCategory()));
		card.add(meta);

		card.add(Box.createVerticalStrut(8));
		card.add(Labels.small(statusText(team, tile, locked, completed, percent), status));
		if (!locked && (completed || percent > 0)) {
			card.add(Box.createVerticalStrut(5));
			card.add(new ProgressBar(percent, status));
		}

		Requirements reqs = tile.getRequirements();
		List<JsonObject> list = reqs != null ? reqs.getRequirements() : new ArrayList<>();
		if (!list.isEmpty()) {
			card.add(Box.createVerticalStrut(10));
			String match = list.size() > 1 ? (reqs.isAnyMatch() ? "  (any)" : "  (all)") : "";
			card.add(Labels.small("Requirements" + match, UIConstants.TEXT_SECONDARY));
			TileProgress progress = team.progressFor(tile.getBoardTileId());
			for (int i = 0; i < list.size(); i++) {
				RequirementProgress rp = progress != null ? progress.requirement(i) : null;
				// 'any' tiles finish on one requirement, so only rows the
				// progress data flags count as done; 'all' tiles finish only
				// when every row is done.
				boolean rowDone = rp != null && rp.isCompleted() || (completed && !reqs.isAnyMatch());
				card.add(Box.createVerticalStrut(5));
				card.add(requirementRow(list.get(i), rp, rowDone));
			}
		}

		if (tile.getDescription() != null && !tile.getDescription().trim().isEmpty()) {
			card.add(Box.createVerticalStrut(8));
			card.add(Labels.wrapped(tile.getDescription(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED, TEXT_WIDTH));
		}
		return card;
	}

	private static String statusText(Team team, Tile tile, boolean locked, boolean completed, int percent) {
		if (locked) return "Locked for this team";
		if (completed) {
			Completion done = team.completionFor(tile.getBoardTileId());
			return "Completed" + (done != null && done.getCompletedAt() != null ? "  |  " + DateTimeUtil.formatShort(done.getCompletedAt()) : "");
		}
		return percent > 0 ? "In progress  |  " + percent + "%" : "Not started";
	}

	/**
	 * Dot + description on the first line (the count at its end), wrapped
	 * continuation lines, the option list with obtained names in green, and
	 * the contributors, all hanging under the text.
	 */
	private static JComponent requirementRow(JsonObject requirement, RequirementProgress rp, boolean done) {
		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		text.setAlignmentX(Component.LEFT_ALIGNMENT);

		Font font = FontManager.getRunescapeSmallFont();
		Color color = done ? TileCell.COMPLETED : UIConstants.TEXT_SECONDARY;
		FontMetrics fm = new JLabel().getFontMetrics(font);

		String count = progressCount(rp);
		int countWidth = count != null ? fm.stringWidth(count) + 8 : 0;

		// A plain label carries the dot as its icon, so Swing centers the two
		// on one line; HTML wrapping would put the dot at the block's top.
		List<String> lines = TextWrap.wrap(RequirementText.describe(requirement), fm, TEXT_WIDTH - REQ_INDENT - countWidth);
		JLabel first = new JLabel(lines.isEmpty() ? "" : lines.get(0),
			new DotIcon(done, DOT_SIZE, TileCell.COMPLETED, UIConstants.TEXT_MUTED), JLabel.LEFT);
		first.setFont(font);
		first.setForeground(color);
		first.setIconTextGap(DOT_GAP);

		JPanel firstLine = new JPanel(new BorderLayout(8, 0));
		firstLine.setOpaque(false);
		firstLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		firstLine.add(first, BorderLayout.CENTER);
		if (count != null) {
			firstLine.add(Labels.label(count, font, done ? TileCell.COMPLETED : TileCell.IN_PROGRESS), BorderLayout.EAST);
		}
		firstLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, firstLine.getPreferredSize().height));
		text.add(firstLine);
		for (int i = 1; i < lines.size(); i++) {
			text.add(indented(Labels.label(lines.get(i), font, color)));
		}

		List<String> options = RequirementText.optionNames(requirement);
		if (options.size() > 1) {
			text.add(indented(optionsLabel(options, obtainedNames(rp != null ? rp.getProgressMetadata() : null), font)));
		}

		String who = rp != null ? contributors(rp.getProgressMetadata()) : "";
		if (!who.isEmpty()) {
			text.add(indented(Labels.wrapped("by " + who, font, done ? TileCell.COMPLETED : TileCell.IN_PROGRESS, TEXT_WIDTH - REQ_INDENT)));
		}
		return text;
	}

	private static JLabel indented(JLabel label) {
		label.setBorder(new EmptyBorder(0, REQ_INDENT, 0, 0));
		return label;
	}

	/** Obtained names first (green) so they survive the cap on long lists (49 pets...). */
	private static JLabel optionsLabel(List<String> options, Set<String> obtained, Font font) {
		List<String> ordered = new ArrayList<>();
		for (String n : options) if (obtained.contains(n.toLowerCase())) ordered.add(n);
		for (String n : options) if (!obtained.contains(n.toLowerCase())) ordered.add(n);
		StringBuilder html = new StringBuilder();
		int shown = Math.min(ordered.size(), MAX_OPTIONS);
		for (int i = 0; i < shown; i++) {
			if (i > 0) html.append(", ");
			String name = ordered.get(i);
			String escaped = Labels.escapeHtml(name);
			if (obtained.contains(name.toLowerCase())) {
				html.append("<span style='color:#4caf50'>").append(escaped).append("</span>");
			} else {
				html.append(escaped);
			}
		}
		if (ordered.size() > shown) html.append(" +").append(ordered.size() - shown).append(" more");
		return Labels.wrappedHtml(html.toString(), font, UIConstants.TEXT_MUTED, TEXT_WIDTH - REQ_INDENT);
	}

	/** "current/target" when the requirement counts past one, else null. */
	private static String progressCount(RequirementProgress rp) {
		if (rp == null) return null;
		JsonObject meta = rp.getProgressMetadata();
		Double target = Json.number(meta, "targetValue");
		Double current = Json.number(meta, "currentTotalCount");
		if (current == null) current = rp.getProgressValue();
		if (target == null || target <= 1 || current == null) return null;
		return NumberFmt.group(Math.round(current)) + "/" + NumberFmt.group(Math.round(target));
	}

	/** Item and pet names (lowercased) the team has already turned in for this requirement. */
	private static Set<String> obtainedNames(JsonObject meta) {
		Set<String> names = new HashSet<>();
		if (meta == null) return names;
		collectNames(Json.array(meta, "lastItemsObtained"), names);
		JsonArray contributions = Json.array(meta, "playerContributions");
		if (contributions != null) {
			for (JsonElement c : contributions) {
				if (!c.isJsonObject()) continue;
				collectNames(Json.array(c.getAsJsonObject(), "items"), names);
				collectNames(Json.array(c.getAsJsonObject(), "pets"), names);
			}
		}
		return names;
	}

	private static void collectNames(JsonArray array, Set<String> into) {
		for (String n : Json.strings(array, "itemName")) into.add(n.toLowerCase());
		for (String n : Json.strings(array, "petName")) into.add(n.toLowerCase());
	}

	private static String contributors(JsonObject meta) {
		List<String> names = new ArrayList<>();
		for (String n : Json.strings(Json.array(meta, "playerContributions"), "osrsNickname")) {
			if (!names.contains(n)) names.add(n);
		}
		if (names.size() > MAX_CONTRIBUTORS) {
			return String.join(", ", names.subList(0, MAX_CONTRIBUTORS)) + " +" + (names.size() - MAX_CONTRIBUTORS);
		}
		return String.join(", ", names);
	}
}

package com.revalclan.ui.leaguesbingo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Board;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Payload;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Team;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Tile;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.TileProgress;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.UnlockedRegion;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.DateTimeUtil;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leagues Bingo inside the side panel: pick a team, pick one of its region
 * boards, then browse the tiles. Mirrors the homepage showcase, fed by the
 * same public payload, sized for the 225px RuneLite panel.
 */
public class LeaguesBingoPanel extends JPanel {
	private enum View { TEAMS, BOARDS, BOARD }

	private static final long FRESH_MS = 45_000;
	private static final int TEXT_WIDTH = 158;
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d HH:mm");

	private final RevalApiService api;
	private final Client client;
	private final Runnable onClose;
	private final WikiIconCache icons;

	private final BackButton backButton;
	private final JLabel titleLabel;
	private final RefreshButton refreshButton;
	private final JPanel body;
	private final JScrollPane scroll;

	private EventsResponse.EventSummary event;
	private Payload payload;
	private long loadedAt;
	private boolean loading;
	private String loadError;

	private View view = View.TEAMS;
	private String teamId;
	private String region;
	private String tileId;

	private JPanel tileDetailHolder;
	private final List<TileCell> cells = new ArrayList<>();

	public LeaguesBingoPanel(RevalApiService api, Client client, Runnable onClose) {
		this.api = api;
		this.client = client;
		this.onClose = onClose;
		this.icons = new WikiIconCache(api.getHttpClient());

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.CARD_BG);
		header.setBorder(new EmptyBorder(6, 6, 6, 6));
		backButton = new BackButton("< Back", this::back);
		titleLabel = new JLabel("");
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(UIConstants.ACCENT_GOLD);
		titleLabel.setHorizontalAlignment(JLabel.CENTER);
		refreshButton = new RefreshButton(this::refresh);
		header.add(backButton, BorderLayout.WEST);
		header.add(titleLabel, BorderLayout.CENTER);
		header.add(refreshButton, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(UIConstants.BACKGROUND);
		body.setBorder(new EmptyBorder(10, 8, 12, 8));

		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = super.getPreferredSize();
				if (getParent() != null) size.width = getParent().getWidth();
				return size;
			}
		};
		wrapper.setBackground(UIConstants.BACKGROUND);
		wrapper.add(body, BorderLayout.NORTH);

		scroll = new JScrollPane(wrapper);
		scroll.setBorder(null);
		scroll.setBackground(UIConstants.BACKGROUND);
		scroll.getViewport().setBackground(UIConstants.BACKGROUND);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
	}

	// ==================== Navigation ====================

	/** Show the event, starting at the team list. Re-uses a recent payload. */
	public void open(EventsResponse.EventSummary event) {
		boolean sameEvent = this.event != null && this.event.getId().equals(event.getId());
		this.event = event;
		if (!sameEvent) {
			payload = null;
			teamId = null;
			region = null;
			tileId = null;
		}
		view = View.TEAMS;
		loadError = null;
		if (payload == null || System.currentTimeMillis() - loadedAt > FRESH_MS) {
			load();
		}
		render(true);
	}

	public void refresh() {
		if (event != null && !loading) load();
	}

	private void back() {
		tileId = null;
		switch (view) {
			case BOARD:
				view = View.BOARDS;
				break;
			case BOARDS:
				view = View.TEAMS;
				break;
			default:
				onClose.run();
				return;
		}
		render(true);
	}

	private void showBoards(Team team) {
		teamId = team.getId();
		region = null;
		tileId = null;
		view = View.BOARDS;
		render(true);
	}

	private void showBoard(String regionId) {
		region = regionId;
		tileId = null;
		view = View.BOARD;
		render(true);
	}

	// ==================== Loading ====================

	private void load() {
		loading = true;
		loadError = null;
		refreshButton.setLoading(true);
		api.fetchLeaguesBingoEvent(event.getId(),
			response -> SwingUtilities.invokeLater(() -> {
				loading = false;
				refreshButton.setLoading(false);
				payload = response.getData();
				loadedAt = System.currentTimeMillis();
				render(false);
			}),
			error -> SwingUtilities.invokeLater(() -> {
				loading = false;
				refreshButton.setLoading(false);
				loadError = error.getMessage();
				render(false);
			}));
	}

	// ==================== Rendering ====================

	private void render(boolean scrollToTop) {
		body.removeAll();
		cells.clear();
		tileDetailHolder = null;

		Team team = payload != null ? payload.teamById(teamId) : null;
		if (view != View.TEAMS && team == null) {
			view = View.TEAMS;
		}

		switch (view) {
			case BOARDS:
				backButton.setText("< Teams");
				titleLabel.setText(team.getName());
				titleLabel.setForeground(teamColor(team));
				buildBoardsView(team);
				break;
			case BOARD:
				backButton.setText("< Boards");
				titleLabel.setText(LeaguesRegions.byId(region).displayName);
				titleLabel.setForeground(LeaguesRegions.byId(region).accent);
				buildBoardView(team);
				break;
			default:
				backButton.setText("< Events");
				titleLabel.setText(event != null ? event.getName() : "");
				titleLabel.setForeground(UIConstants.ACCENT_GOLD);
				buildTeamsView();
		}

		body.revalidate();
		body.repaint();
		if (scrollToTop) {
			SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
		}
	}

	private boolean showStatusInsteadOfContent() {
		if (payload != null) return false;
		if (loading) {
			body.add(centered(label("Loading boards...", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED)));
		} else if (loadError != null) {
			body.add(centered(wrapped("Failed to load: " + loadError, FontManager.getRunescapeSmallFont(), UIConstants.ERROR_COLOR, TEXT_WIDTH + 20)));
		} else {
			body.add(centered(label("No board data", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED)));
		}
		return true;
	}

	// ---------- Teams ----------

	private void buildTeamsView() {
		if (showStatusInsteadOfContent()) return;

		body.add(buildEventSummary());
		body.add(Box.createVerticalStrut(10));
		body.add(sectionTitle("Standings"));
		body.add(Box.createVerticalStrut(6));

		List<Team> teams = new ArrayList<>(payload.getTeams());
		teams.sort(Comparator.comparingInt(Team::getScore).reversed()
			.thenComparing(Comparator.comparingInt(Team::getUniqueCompletedTiles).reversed()));

		if (teams.isEmpty()) {
			body.add(centered(label("No teams yet", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED)));
			return;
		}

		String me = localPlayerName();
		int rank = 1;
		for (Team team : teams) {
			body.add(buildTeamRow(team, rank++, team.hasMember(me)));
			body.add(Box.createVerticalStrut(6));
		}
		body.add(Box.createVerticalStrut(4));
		body.add(centered(label("Pick a team to see its boards", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED)));
	}

	private JComponent buildEventSummary() {
		Card card = new Card(UIConstants.ACCENT_GREEN, false);
		String status = payload.getEvent() != null ? payload.getEvent().getStatus() : null;
		boolean live = "active".equalsIgnoreCase(status);

		JPanel top = row();
		top.add(badge(live ? "LIVE" : status != null ? status.toUpperCase() : "EVENT", live ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_SECONDARY), BorderLayout.WEST);
		top.add(label(payload.getTeams().size() + " teams", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY), BorderLayout.EAST);
		card.add(top);

		String endDate = payload.getEvent() != null ? payload.getEvent().getEndDate() : null;
		if (endDate != null) {
			card.add(Box.createVerticalStrut(6));
			card.add(label((live ? "Ends " : "Ended ") + fmtDate(endDate), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));
		}

		if (payload.isTilesHidden()) {
			card.add(Box.createVerticalStrut(6));
			String reveal = payload.getTilesRevealAt() != null ? "Tiles reveal " + fmtDate(payload.getTilesRevealAt()) : "Tiles not revealed yet";
			card.add(label(reveal, FontManager.getRunescapeSmallFont(), UIConstants.ACCENT_GOLD));
		}
		return card;
	}

	private JComponent buildTeamRow(Team team, int rank, boolean mine) {
		Color color = teamColor(team);
		Card card = new Card(color, true);

		JPanel top = row();
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		left.setOpaque(false);
		left.add(label("#" + rank, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));
		left.add(label(ellipsize(team.getName(), FontManager.getRunescapeBoldFont(), mine ? 84 : 112), FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY));
		if (mine) left.add(badge("YOU", color));
		top.add(left, BorderLayout.WEST);
		top.add(label(team.getScore() + " pts", FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_GOLD), BorderLayout.EAST);
		card.add(top);

		card.add(Box.createVerticalStrut(4));
		int regions = team.getUnlockedRegions().size();
		int doubled = 0;
		for (UnlockedRegion u : team.getUnlockedRegions()) {
			if (u.getBoardCompletedAt() != null) doubled++;
		}
		String stats = team.getUniqueCompletedTiles() + " tiles  |  " + regions + " region" + (regions != 1 ? "s" : "")
			+ (doubled > 0 ? "  |  " + doubled + " x2" : "");
		card.add(label(stats, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));

		Clickable.onPress(card, () -> showBoards(team), card::setHovered);
		return card;
	}

	// ---------- Boards ----------

	private void buildBoardsView(Team team) {
		if (showStatusInsteadOfContent()) return;

		body.add(buildTeamChip(team, true));
		body.add(Box.createVerticalStrut(10));
		body.add(sectionTitle("Boards"));
		body.add(Box.createVerticalStrut(6));

		List<Board> boards = new ArrayList<>(payload.getBoards());
		boards.sort(Comparator.comparing((Board b) -> team.hasUnlocked(b.getRegion()) ? 0 : 1)
			.thenComparingInt(b -> LeaguesRegions.order(b.getRegion())));

		for (Board board : boards) {
			body.add(buildBoardRow(team, board));
			body.add(Box.createVerticalStrut(6));
		}
	}

	private JComponent buildTeamChip(Team team, boolean detailed) {
		Color color = teamColor(team);
		Card card = new Card(color, false);

		JPanel top = row();
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		left.setOpaque(false);
		left.add(new ColorDot(color, 10));
		left.add(label(ellipsize(team.getName(), FontManager.getRunescapeBoldFont(), 112), FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY));
		top.add(left, BorderLayout.WEST);
		top.add(label(team.getScore() + " pts", FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_GOLD), BorderLayout.EAST);
		card.add(top);

		if (detailed) {
			card.add(Box.createVerticalStrut(4));
			int tokens = team.getPickTokens() != null ? team.getPickTokens().getAvailable() : 0;
			String line = team.getUniqueCompletedTiles() + " tiles  |  " + team.getUnlockedRegions().size() + "/" + payload.getBoards().size() + " regions"
				+ (tokens > 0 ? "  |  " + tokens + " pick" + (tokens != 1 ? "s" : "") + " open" : "");
			card.add(label(line, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
		}
		return card;
	}

	private JComponent buildBoardRow(Team team, Board board) {
		LeaguesRegions.Region meta = LeaguesRegions.byId(board.getRegion());
		BoardStats stats = BoardStats.of(team, board);
		Card card = new Card(meta.accent, true);
		card.setDim(stats.locked);

		JPanel top = row();
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		left.setOpaque(false);
		left.add(label(meta.displayName, FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY));
		if (stats.locked) left.add(badge("LOCKED", UIConstants.TEXT_MUTED));
		if (stats.boardComplete) left.add(badge("x2", UIConstants.ACCENT_GOLD));
		top.add(left, BorderLayout.WEST);
		top.add(label(stats.percent() + "%", FontManager.getRunescapeSmallFont(), stats.completed == stats.total && stats.total > 0 ? TileCell.COMPLETED : UIConstants.TEXT_SECONDARY), BorderLayout.EAST);
		card.add(top);

		card.add(Box.createVerticalStrut(4));
		card.add(label(stats.line(board), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
		card.add(Box.createVerticalStrut(6));
		card.add(new ProgressBar(stats.percent(), meta.accent));

		Clickable.onPress(card, () -> showBoard(board.getRegion()), card::setHovered);
		return card;
	}

	// ---------- Board ----------

	private void buildBoardView(Team team) {
		if (showStatusInsteadOfContent()) return;

		Board board = payload.boardFor(region);
		if (board == null) {
			body.add(centered(label("Board not found", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED)));
			return;
		}

		LeaguesRegions.Region meta = LeaguesRegions.byId(region);
		BoardStats stats = BoardStats.of(team, board);

		body.add(buildTeamChip(team, false));
		body.add(Box.createVerticalStrut(8));

		Card header = new Card(meta.accent, false);
		JPanel top = row();
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		left.setOpaque(false);
		left.add(label(meta.displayName, FontManager.getRunescapeBoldFont(), meta.accent));
		if (stats.locked) left.add(badge("LOCKED", UIConstants.TEXT_MUTED));
		if (stats.boardComplete) left.add(badge("x2", UIConstants.ACCENT_GOLD));
		top.add(left, BorderLayout.WEST);
		top.add(label(stats.percent() + "%", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY), BorderLayout.EAST);
		header.add(top);
		header.add(Box.createVerticalStrut(4));
		header.add(label(stats.line(board), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
		header.add(Box.createVerticalStrut(6));
		header.add(new ProgressBar(stats.percent(), meta.accent));
		body.add(header);
		body.add(Box.createVerticalStrut(10));

		body.add(buildGrid(team, board, meta, stats));
		body.add(Box.createVerticalStrut(10));

		tileDetailHolder = new JPanel();
		tileDetailHolder.setLayout(new BoxLayout(tileDetailHolder, BoxLayout.Y_AXIS));
		tileDetailHolder.setOpaque(false);
		tileDetailHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(tileDetailHolder);
		renderTileDetail(team, board, stats);
	}

	private JComponent buildGrid(Team team, Board board, LeaguesRegions.Region meta, BoardStats stats) {
		int cols = Math.max(1, board.getColumns());
		int rows = Math.max(1, board.getRows());
		int gap = 4;
		int gutter = 16;
		int tileSize = Math.max(14, Math.min(56, (198 - gutter - (cols - 1) * gap) / cols));

		JPanel grid = new JPanel(new GridBagLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (!stats.boardComplete) return;
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(TileCell.withAlpha(meta.accent, 110));
				g2.setStroke(new BasicStroke(1.5f));
				g2.draw(new RoundRectangle2D.Float(gutter - 2, gutter - 2, getWidth() - gutter + 1, getHeight() - gutter + 1, 10, 10));
				g2.dispose();
			}
		};
		grid.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(gap / 2, gap / 2, gap / 2, gap / 2);

		Font small = FontManager.getRunescapeSmallFont();
		for (int col = 0; col < cols; col++) {
			c.gridx = col + 1;
			c.gridy = 0;
			JLabel l = label(String.valueOf((char) ('A' + col)), small, UIConstants.TEXT_MUTED);
			l.setHorizontalAlignment(JLabel.CENTER);
			l.setPreferredSize(new Dimension(tileSize, gutter - gap));
			grid.add(l, c);
		}

		Color accent = teamColor(team);
		for (int r = 0; r < rows; r++) {
			c.gridx = 0;
			c.gridy = r + 1;
			JLabel l = label(String.valueOf(r + 1), small, UIConstants.TEXT_MUTED);
			l.setHorizontalAlignment(JLabel.CENTER);
			l.setPreferredSize(new Dimension(gutter - gap, tileSize));
			grid.add(l, c);

			for (int col = 0; col < cols; col++) {
				c.gridx = col + 1;
				String position = String.valueOf((char) ('A' + col)) + (r + 1);
				Tile tile = payload.isTilesHidden() ? null : board.tileAt(position);

				TileCell cell;
				if (payload.isTilesHidden()) {
					cell = new TileCell(null, TileCell.State.HIDDEN, 0, meta.accent, tileSize);
				} else if (tile == null) {
					cell = new TileCell(null, TileCell.State.FILLER, 0, meta.accent, tileSize);
				} else {
					int percent = tilePercent(team, tile);
					TileCell.State state = percent >= 100 ? TileCell.State.COMPLETED
						: percent > 0 ? TileCell.State.IN_PROGRESS : TileCell.State.NOT_STARTED;
					cell = new TileCell(tile, state, percent, accent, tileSize);
					cell.setDimmed(stats.locked);
					cell.setSelected(tile.getBoardTileId().equals(tileId));
					cells.add(cell);
					final TileCell target = cell;
					Clickable.onPress(cell, () -> selectTile(team, board, stats, target), cell::setHovered);
					if (tile.getIcon() != null) {
						icons.load(tile.getIcon(), target::setIcon);
					}
				}
				grid.add(cell, c);
			}
		}

		JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		holder.setOpaque(false);
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);
		holder.add(grid);
		return holder;
	}

	private void selectTile(Team team, Board board, BoardStats stats, TileCell cell) {
		Tile tile = cell.getTile();
		tileId = tile.getBoardTileId().equals(tileId) ? null : tile.getBoardTileId();
		for (TileCell c : cells) {
			c.setSelected(c.getTile().getBoardTileId().equals(tileId));
		}
		renderTileDetail(team, board, stats);
	}

	private void renderTileDetail(Team team, Board board, BoardStats stats) {
		if (tileDetailHolder == null) return;
		tileDetailHolder.removeAll();

		Tile tile = null;
		if (tileId != null) {
			for (Tile t : board.getTiles()) {
				if (tileId.equals(t.getBoardTileId())) tile = t;
			}
		}

		if (tile == null) {
			String hint = payload.isTilesHidden() ? "Tiles are hidden until the reveal" : "Click a tile to see its task and progress";
			JLabel hintLabel = wrapped(hint, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED, TEXT_WIDTH + 10);
			tileDetailHolder.add(centered(hintLabel));
		} else {
			tileDetailHolder.add(buildTileDetail(team, tile, stats.locked));
		}
		tileDetailHolder.revalidate();
		tileDetailHolder.repaint();
	}

	private JComponent buildTileDetail(Team team, Tile tile, boolean locked) {
		int percent = tilePercent(team, tile);
		boolean completed = percent >= 100;
		Color status = locked ? UIConstants.TEXT_MUTED : completed ? TileCell.COMPLETED : percent > 0 ? TileCell.IN_PROGRESS : UIConstants.TEXT_MUTED;
		Card card = new Card(status, false);

		// Title: position + task
		JPanel top = row();
		top.add(label(tile.getPosition(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED), BorderLayout.WEST);
		top.add(label(tile.getPoints() + " pt" + (tile.getPoints() != 1 ? "s" : ""), FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_GOLD), BorderLayout.EAST);
		card.add(top);
		card.add(Box.createVerticalStrut(4));
		card.add(wrapped(tile.getTask(), FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY, TEXT_WIDTH));

		// Meta line
		card.add(Box.createVerticalStrut(6));
		JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		meta.setOpaque(false);
		meta.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (tile.getDifficulty() != null) meta.add(badge(tile.getDifficulty().toUpperCase(), difficultyColor(tile.getDifficulty())));
		if (tile.getCategory() != null) meta.add(badge(tile.getCategory().toUpperCase(), UIConstants.TEXT_SECONDARY));
		card.add(meta);

		// Status
		card.add(Box.createVerticalStrut(8));
		String statusText;
		if (locked) {
			statusText = "Locked for this team";
		} else if (completed) {
			LeaguesBingoResponse.Completion done = team.completionFor(tile.getBoardTileId());
			statusText = "Completed" + (done != null && done.getCompletedAt() != null ? "  |  " + fmtDate(done.getCompletedAt()) : "");
		} else if (percent > 0) {
			statusText = "In progress  |  " + percent + "%";
		} else {
			statusText = "Not started";
		}
		card.add(label(statusText, FontManager.getRunescapeSmallFont(), status));
		if (!locked && (completed || percent > 0)) {
			card.add(Box.createVerticalStrut(5));
			card.add(new ProgressBar(percent, status));
		}

		// Requirements
		LeaguesBingoResponse.Requirements reqs = tile.getRequirements();
		List<JsonObject> list = reqs != null ? reqs.getRequirements() : new ArrayList<>();
		if (!list.isEmpty()) {
			card.add(Box.createVerticalStrut(10));
			String match = reqs.getMatchType() != null && list.size() > 1
				? ("any".equalsIgnoreCase(reqs.getMatchType()) ? "  (any)" : "  (all)") : "";
			card.add(label("Requirements" + match, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
			TileProgress progress = team.progressFor(tile.getBoardTileId());
			for (int i = 0; i < list.size(); i++) {
				card.add(Box.createVerticalStrut(5));
				card.add(buildRequirementRow(list.get(i), progress != null ? progress.requirement(i) : null, completed));
			}
		}

		if (tile.getDescription() != null && !tile.getDescription().trim().isEmpty()) {
			card.add(Box.createVerticalStrut(8));
			card.add(wrapped(tile.getDescription(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED, TEXT_WIDTH));
		}
		return card;
	}

	private JComponent buildRequirementRow(JsonObject requirement, LeaguesBingoResponse.RequirementProgress rp, boolean tileDone) {
		boolean done = tileDone || (rp != null && rp.isCompleted());
		JPanel rowPanel = new JPanel(new BorderLayout(6, 0));
		rowPanel.setOpaque(false);
		rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel markHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 3));
		markHolder.setOpaque(false);
		markHolder.add(new StatusDot(done));
		rowPanel.add(markHolder, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		text.add(wrapped(RequirementText.describe(requirement), FontManager.getRunescapeSmallFont(),
			done ? UIConstants.TEXT_PRIMARY : UIConstants.TEXT_SECONDARY, TEXT_WIDTH - 16));

		List<String> items = RequirementText.itemNames(requirement);
		if (items.size() > 1) {
			String names = items.size() > 8
				? String.join(", ", items.subList(0, 8)) + " +" + (items.size() - 8) + " more"
				: String.join(", ", items);
			text.add(wrapped(names, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED, TEXT_WIDTH - 16));
		}

		if (rp != null) {
			JsonObject meta = rp.getProgressMetadata();
			Double target = RequirementText.number(meta, "targetValue");
			Double current = RequirementText.number(meta, "currentTotalCount");
			if (current == null) current = rp.getProgressValue();
			StringBuilder sub = new StringBuilder();
			if (target != null && target > 1 && current != null) {
				sub.append(RequirementText.fmt(Math.round(current))).append("/").append(RequirementText.fmt(Math.round(target)));
			}
			String who = contributors(meta);
			if (!who.isEmpty()) {
				if (sub.length() > 0) sub.append("  |  ");
				sub.append("by ").append(who);
			}
			if (sub.length() > 0) {
				text.add(wrapped(sub.toString(), FontManager.getRunescapeSmallFont(), done ? TileCell.COMPLETED : TileCell.IN_PROGRESS, TEXT_WIDTH - 16));
			}
		}

		rowPanel.add(text, BorderLayout.CENTER);
		return rowPanel;
	}

	private static String contributors(JsonObject meta) {
		if (meta == null || !meta.has("playerContributions") || !meta.get("playerContributions").isJsonArray()) return "";
		JsonArray arr = meta.getAsJsonArray("playerContributions");
		List<String> names = new ArrayList<>();
		for (JsonElement e : arr) {
			if (!e.isJsonObject()) continue;
			String n = RequirementText.str(e.getAsJsonObject(), "osrsNickname", null);
			if (n != null && !names.contains(n)) names.add(n);
		}
		if (names.size() > 3) {
			return String.join(", ", names.subList(0, 3)) + " +" + (names.size() - 3);
		}
		return String.join(", ", names);
	}

	// ==================== Derived data ====================

	/** 100 when done, 1..99 while in progress, 0 when untouched. */
	static int tilePercent(Team team, Tile tile) {
		if (team.completionFor(tile.getBoardTileId()) != null) return 100;
		TileProgress p = team.progressFor(tile.getBoardTileId());
		if (p == null) return 0;
		double value = p.getValue() != null ? p.getValue() : 0;
		int total = p.getTotalRequirements() != null ? p.getTotalRequirements() : 0;
		int pct;
		if (total > 1) {
			pct = (int) Math.round(100d * p.completedRequirementCount() / total);
		} else if (p.getTarget() != null && p.getTarget() > 0) {
			pct = (int) Math.round(100d * value / p.getTarget());
		} else {
			pct = value > 0 ? 8 : 0;
		}
		if (pct <= 0 && value > 0) pct = 8;
		return Math.max(0, Math.min(99, pct));
	}

	private static final class BoardStats {
		int total;
		int completed;
		int earned;
		int bonus;
		boolean locked;
		boolean boardComplete;

		static BoardStats of(Team team, Board board) {
			BoardStats s = new BoardStats();
			UnlockedRegion unlock = team.unlockFor(board.getRegion());
			s.locked = unlock == null;
			s.boardComplete = unlock != null && unlock.getBoardCompletedAt() != null;
			s.bonus = unlock != null && unlock.getBonusPoints() != null ? unlock.getBonusPoints() : 0;
			s.total = board.getTiles().size();
			for (Tile t : board.getTiles()) {
				if (team.completionFor(t.getBoardTileId()) != null) {
					s.completed++;
					s.earned += t.getPoints();
				}
			}
			return s;
		}

		int percent() {
			return total == 0 ? 0 : (int) Math.round(100d * completed / total);
		}

		String line(Board board) {
			if (board.getTiles().isEmpty()) {
				return board.getRows() + "x" + board.getColumns() + " board  |  tiles hidden";
			}
			return completed + "/" + total + " tiles  |  " + earned + (bonus > 0 ? " + " + bonus : "") + "/" + board.getTotalPoints() + " pts";
		}
	}

	// ==================== Small helpers ====================

	private String localPlayerName() {
		try {
			return client != null && client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static Color teamColor(Team team) {
		try {
			Color c = Color.decode(team.getColor());
			// Pure white/black team colors vanish on the dark panel; nudge them.
			if (c.getRed() + c.getGreen() + c.getBlue() > 720) return new Color(220, 220, 220);
			if (c.getRed() + c.getGreen() + c.getBlue() < 60) return new Color(140, 140, 140);
			return c;
		} catch (Exception e) {
			return UIConstants.ACCENT_BLUE;
		}
	}

	private static Color difficultyColor(String difficulty) {
		switch (difficulty.toLowerCase()) {
			case "easy": return UIConstants.TIER_EASY;
			case "medium": return UIConstants.TIER_MEDIUM;
			case "hard": return UIConstants.TIER_HARD;
			case "elite": return UIConstants.TIER_ELITE;
			case "master": return UIConstants.TIER_MASTER;
			default: return UIConstants.TEXT_SECONDARY;
		}
	}

	private static String fmtDate(String iso) {
		try {
			return DateTimeUtil.parseToLocal(iso).format(DATE_FMT);
		} catch (Exception e) {
			return iso;
		}
	}

	/** Cuts text to fit maxWidth pixels in the given font, adding an ellipsis. */
	private static String ellipsize(String text, Font font, int maxWidth) {
		if (text == null) return "";
		java.awt.FontMetrics fm = new JLabel().getFontMetrics(font);
		if (fm.stringWidth(text) <= maxWidth) return text;
		String dots = "...";
		int end = text.length();
		while (end > 1 && fm.stringWidth(text.substring(0, end).trim() + dots) > maxWidth) end--;
		return text.substring(0, end).trim() + dots;
	}

	private static JLabel label(String text, Font font, Color color) {
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel wrapped(String text, Font font, Color color, int width) {
		String safe = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		JLabel l = new JLabel("<html><div style='width:" + width + "px'>" + safe + "</div></html>");
		l.setFont(font);
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel sectionTitle(String text) {
		return label(text, FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY);
	}

	private static JPanel row() {
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static JComponent centered(JComponent inner) {
		JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 12));
		wrap.setOpaque(false);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(inner);
		return wrap;
	}

	private static JLabel badge(String text, Color color) {
		JLabel b = new JLabel(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(TileCell.withAlpha(color, 32));
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setForeground(color);
		b.setBorder(new EmptyBorder(2, 6, 2, 6));
		b.setOpaque(false);
		return b;
	}

	/** Rounded card with a left accent bar; optionally hover-highlighted. */
	private static final class Card extends JPanel {
		private final Color accent;
		private final boolean hoverable;
		private boolean hovered;
		private boolean dim;

		Card(Color accent, boolean hoverable) {
			this.accent = accent;
			this.hoverable = hoverable;
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(false);
			setBorder(new EmptyBorder(9, 14, 9, 10));
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		void setHovered(boolean hovered) {
			this.hovered = hoverable && hovered;
			repaint();
		}

		void setDim(boolean dim) {
			this.dim = dim;
		}

		@Override
		public Dimension getMaximumSize() {
			Dimension d = super.getPreferredSize();
			return new Dimension(Integer.MAX_VALUE, d.height);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(hovered ? UIConstants.CARD_HOVER : UIConstants.CARD_BG);
			g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
			g2.setColor(UIConstants.BORDER_COLOR);
			g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 10, 10));
			g2.setColor(dim ? TileCell.withAlpha(accent, 90) : accent);
			g2.fillRoundRect(0, 6, 4, getHeight() - 12, 4, 4);
			g2.dispose();
		}
	}

	private static final class ProgressBar extends JComponent {
		private final int percent;
		private final Color color;

		ProgressBar(int percent, Color color) {
			this.percent = Math.max(0, Math.min(100, percent));
			this.color = color;
			setPreferredSize(new Dimension(100, 6));
			setMinimumSize(new Dimension(20, 6));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(UIConstants.PROGRESS_BG);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
			int w = (int) Math.round(getWidth() * percent / 100d);
			if (w > 0) {
				g2.setColor(color);
				g2.fillRoundRect(0, 0, Math.max(w, 6), getHeight(), 6, 6);
			}
			g2.dispose();
		}
	}

	/** Filled green dot when done, hollow muted ring otherwise. */
	private static final class StatusDot extends JComponent {
		private final boolean done;

		StatusDot(boolean done) {
			this.done = done;
			Dimension d = new Dimension(9, 9);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (done) {
				g2.setColor(TileCell.COMPLETED);
				g2.fillOval(0, 0, getWidth(), getHeight());
			} else {
				g2.setColor(UIConstants.TEXT_MUTED);
				g2.setStroke(new BasicStroke(1.5f));
				g2.drawOval(1, 1, getWidth() - 2, getHeight() - 2);
			}
			g2.dispose();
		}
	}

	private static final class ColorDot extends JComponent {
		private final Color color;

		ColorDot(Color color, int size) {
			this.color = color;
			Dimension d = new Dimension(size, size);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.fillOval(0, 0, getWidth(), getHeight());
			g2.dispose();
		}
	}
}

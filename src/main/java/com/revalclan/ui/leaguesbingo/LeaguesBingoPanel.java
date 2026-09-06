package com.revalclan.ui.leaguesbingo;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.api.leaguesbingo.LeaguesBingoMeResponse;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Board;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Payload;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Team;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Tile;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.UnlockedRegion;
import com.revalclan.ui.components.AccentButton;
import com.revalclan.ui.components.AccentCard;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.components.ProgressBar;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.components.ScrollWrap;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.TeamColor;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Leagues Bingo inside the side panel: pick a team, pick one of its region
 * boards, then browse the tiles. Mirrors the homepage showcase, fed by the
 * same public payload, sized for the 225px RuneLite panel.
 */
@Singleton
public class LeaguesBingoPanel extends JPanel {
	private enum View { TEAMS, BOARDS, BOARD }

	private static final long FRESH_MS = 45_000;
	private static final int TEXT_WIDTH = 158;

	private final RevalApiService api;
	private final Client client;
	private final SpriteManager spriteManager;
	private final TileIcons tileIcons;
	private Runnable onClose = () -> {};

	private final BackButton backButton;
	private final JLabel titleLabel;
	private final RefreshButton refreshButton;
	private final JPanel body;
	private final JScrollPane scroll;

	private EventsResponse.EventSummary event;
	private Payload payload;
	/** What the backend says this account may do here; null until answered. */
	private LeaguesBingoMeResponse.Viewer me;
	private long loadedAt;
	private boolean loading;
	private boolean picking;
	private String loadError;

	private View view = View.TEAMS;
	private String teamId;
	private String region;
	private String tileId;
	private BoardGridView grid;
	private JPanel tileDetailHolder;

	@Inject
	public LeaguesBingoPanel(RevalApiService api, Client client, ItemManager itemManager, SpriteManager spriteManager) {
		this.api = api;
		this.client = client;
		this.spriteManager = spriteManager;
		this.tileIcons = new TileIcons(itemManager);

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
		scroll = ScrollWrap.of(body);
		add(scroll, BorderLayout.CENTER);
	}

	/** Called when the user backs out of the team list. */
	public void setOnClose(Runnable onClose) {
		this.onClose = onClose;
	}

	// ==================== Navigation ====================

	/** Show the event, starting at the team list. Re-uses a recent payload. */
	public void open(EventsResponse.EventSummary event) {
		boolean sameEvent = this.event != null && this.event.getId().equals(event.getId());
		if (!sameEvent) reset();
		this.event = event;
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

	/** Forget everything, including who may pick: the next account starts clean. */
	public void reset() {
		event = null;
		payload = null;
		me = null;
		loadedAt = 0;
		loadError = null;
		teamId = null;
		region = null;
		tileId = null;
		view = View.TEAMS;
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
		loadViewer();
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

	/** Ask the backend what this account may do; a failure just hides the pick buttons. */
	private void loadViewer() {
		long accountHash = client != null ? client.getAccountHash() : -1;
		if (accountHash == -1) return;
		api.fetchLeaguesBingoMe(event.getId(), accountHash,
			response -> SwingUtilities.invokeLater(() -> {
				me = response.getData();
				if (view != View.TEAMS) render(false);
			}),
			error -> SwingUtilities.invokeLater(() -> me = null));
	}

	// ==================== Rendering ====================

	private void render(boolean scrollToTop) {
		body.removeAll();
		grid = null;
		tileDetailHolder = null;

		Team team = payload != null ? payload.teamById(teamId) : null;
		if (view != View.TEAMS && team == null) view = View.TEAMS;

		switch (view) {
			case BOARDS:
				setHeader("< Teams", team.getName(), teamColor(team));
				break;
			case BOARD:
				setHeader("< Boards", LeaguesRegions.byId(region).getDisplayName(), LeaguesRegions.byId(region).getAccent());
				break;
			default:
				setHeader("< Events", event != null ? event.getName() : "", UIConstants.ACCENT_GOLD);
		}

		if (payload == null) {
			body.add(statusView());
		} else if (view == View.BOARDS) {
			buildBoardsView(team);
		} else if (view == View.BOARD) {
			buildBoardView(team);
		} else {
			buildTeamsView();
		}

		body.revalidate();
		body.repaint();
		if (scrollToTop) {
			SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
		}
	}

	private void setHeader(String backText, String title, Color titleColor) {
		backButton.setText(backText);
		titleLabel.setText(title);
		titleLabel.setForeground(titleColor);
	}

	private JComponent statusView() {
		if (loading) return Labels.centered(Labels.small("Loading boards...", UIConstants.TEXT_MUTED));
		if (loadError != null) {
			return Labels.centered(Labels.wrapped("Failed to load: " + loadError, FontManager.getRunescapeSmallFont(), UIConstants.ERROR_COLOR, TEXT_WIDTH + 20));
		}
		return Labels.centered(Labels.small("No board data", UIConstants.TEXT_MUTED));
	}

	// ---------- Teams ----------

	private void buildTeamsView() {
		body.add(TeamCards.eventSummary(payload));
		body.add(Box.createVerticalStrut(10));
		body.add(Labels.sectionTitle("Standings"));
		body.add(Box.createVerticalStrut(6));

		List<Team> teams = new ArrayList<>(payload.getTeams());
		teams.sort(Comparator.comparingInt(Team::getScore).reversed()
			.thenComparing(Comparator.comparingInt(Team::getUniqueCompletedTiles).reversed()));
		if (teams.isEmpty()) {
			body.add(Labels.centered(Labels.small("No teams yet", UIConstants.TEXT_MUTED)));
			return;
		}

		String me = localPlayerName();
		int rank = 1;
		for (Team team : teams) {
			body.add(TeamCards.row(team, rank++, team.hasMember(me), () -> showBoards(team)));
			body.add(Box.createVerticalStrut(6));
		}
		body.add(Box.createVerticalStrut(4));
		body.add(Labels.centered(Labels.small("Pick a team to see its boards", UIConstants.TEXT_MUTED)));
	}

	// ---------- Boards ----------

	private void buildBoardsView(Team team) {
		body.add(TeamCards.chip(team, payload, true));
		body.add(Box.createVerticalStrut(10));
		body.add(Labels.sectionTitle("Boards"));
		body.add(Box.createVerticalStrut(6));

		// Unlocked boards in the order the team unlocked them, locked ones after.
		List<Board> boards = new ArrayList<>(payload.getBoards());
		boards.sort(Comparator.comparing((Board b) -> team.hasUnlocked(b.getRegion()) ? 0 : 1)
			.thenComparing(b -> unlockedAt(team, b.getRegion()), Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparingInt(b -> LeaguesRegions.order(b.getRegion())));

		for (Board board : boards) {
			AccentCard card = boardSummary(team, board, UIConstants.TEXT_PRIMARY, true);
			Clickable.onPress(card, () -> showBoard(board.getRegion()), card::setHovered);
			body.add(card);
			body.add(Box.createVerticalStrut(6));
		}
	}

	/** ISO-8601 timestamps sort correctly as strings. */
	private static String unlockedAt(Team team, String region) {
		UnlockedRegion u = team.unlockFor(region);
		return u != null ? u.getUnlockedAt() : null;
	}

	/** Summary block shared by the board list and the board header; bottom slot = unlock button or progress bar. */
	private AccentCard boardSummary(Team team, Board board, Color nameColor, boolean hoverable) {
		LeaguesRegions.Region meta = LeaguesRegions.byId(board.getRegion());
		BoardStats stats = BoardStats.of(team, board);
		JComponent bottom = canUnlock(team, board, stats) ? unlockButton(team, board) : new ProgressBar(stats.percent(), meta.getAccent());
		return BoardSummaryCard.build(meta, stats, board, spriteManager, nameColor, hoverable, bottom);
	}

	// ---------- Board ----------

	private void buildBoardView(Team team) {
		Board board = payload.boardFor(region);
		if (board == null) {
			body.add(Labels.centered(Labels.small("Board not found", UIConstants.TEXT_MUTED)));
			return;
		}
		LeaguesRegions.Region meta = LeaguesRegions.byId(region);
		BoardStats stats = BoardStats.of(team, board);

		body.add(TeamCards.chip(team, payload, false));
		body.add(Box.createVerticalStrut(8));
		body.add(boardSummary(team, board, meta.getAccent(), false));
		body.add(Box.createVerticalStrut(10));

		grid = new BoardGridView(payload, team, board, meta, stats, tileIcons, tileId, tile -> {
			tileId = tile.getBoardTileId().equals(tileId) ? null : tile.getBoardTileId();
			grid.setSelected(tileId);
			renderTileDetail(team, board, stats);
		});
		body.add(grid);
		body.add(Box.createVerticalStrut(10));

		tileDetailHolder = new JPanel();
		tileDetailHolder.setLayout(new BoxLayout(tileDetailHolder, BoxLayout.Y_AXIS));
		tileDetailHolder.setOpaque(false);
		tileDetailHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(tileDetailHolder);
		renderTileDetail(team, board, stats);
	}

	private void renderTileDetail(Team team, Board board, BoardStats stats) {
		tileDetailHolder.removeAll();
		Tile tile = null;
		for (Tile t : board.getTiles()) {
			if (t.getBoardTileId().equals(tileId)) tile = t;
		}
		if (tile == null) {
			String hint = payload.isTilesHidden() ? "Tiles are hidden until the reveal" : "Click a tile to see its task and progress";
			tileDetailHolder.add(Labels.centered(Labels.wrapped(hint, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED, TEXT_WIDTH + 10)));
		} else {
			tileDetailHolder.add(TileDetailView.build(team, tile, stats.isLocked()));
		}
		tileDetailHolder.revalidate();
		tileDetailHolder.repaint();
	}

	// ==================== Region picks ====================

	/**
	 * A locked, non-default board this viewer may unlock for the team right
	 * now. Board contents and double picks are the backend's rules.
	 */
	private boolean canUnlock(Team team, Board board, BoardStats stats) {
		return me != null && me.canPickFor(team.getId())
			&& stats.isLocked()
			&& !payload.isDefaultRegion(board.getRegion())
			&& team.availableTokens() > 0;
	}

	private JComponent unlockButton(Team team, Board board) {
		JButton button = new AccentButton("Unlock board", UIConstants.ACCENT_GOLD);
		button.setForeground(UIConstants.BACKGROUND);
		button.setPreferredSize(new Dimension(100, 22));
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setToolTipText("Spend one pick token to unlock this board");
		button.setEnabled(!picking);
		button.addActionListener(e -> confirmUnlock(team, board));
		return button;
	}

	private void confirmUnlock(Team team, Board board) {
		if (picking || me == null) return;
		LeaguesRegions.Region meta = LeaguesRegions.byId(board.getRegion());
		int tokens = team.availableTokens();
		String summary = "<html><body style='width:230px'>"
			+ "<b>Unlock " + Labels.escapeHtml(meta.getDisplayName()) + " for " + Labels.escapeHtml(team.getName()) + "?</b><br><br>"
			+ board.getRows() + "x" + board.getColumns() + " board, " + board.getTiles().size() + " tiles, "
			+ board.getTotalPoints() + " points.<br>"
			+ "This spends 1 of " + tokens + " pick token" + (tokens != 1 ? "s" : "") + " and cannot be undone."
			+ (me.isSuperadmin() && !me.isOwnTeam(team.getId()) ? "<br><br><i>You are unlocking as a superadmin.</i>" : "")
			+ "</body></html>";
		int choice = JOptionPane.showConfirmDialog(this, summary, "Unlock region", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (choice != JOptionPane.OK_OPTION) return;

		long accountHash = client != null ? client.getAccountHash() : -1;
		if (accountHash == -1) return;
		picking = true;
		render(false);
		// Pickers act for their own team; only a superadmin names another team.
		String targetTeam = me.isOwnTeam(team.getId()) ? null : team.getId();
		api.pickLeaguesBingoRegion(event.getId(), accountHash, board.getRegion(), targetTeam,
			response -> SwingUtilities.invokeLater(() -> {
				picking = false;
				if (response.getData() != null && response.getData().getMe() != null) me = response.getData().getMe();
				load();
				JOptionPane.showMessageDialog(this, meta.getDisplayName() + " unlocked for " + team.getName() + ".",
					"Region unlocked", JOptionPane.INFORMATION_MESSAGE);
			}),
			error -> SwingUtilities.invokeLater(() -> {
				picking = false;
				render(false);
				JOptionPane.showMessageDialog(this, "Could not unlock: " + error.getMessage(), "Unlock failed", JOptionPane.WARNING_MESSAGE);
			}));
	}

	// ==================== Helpers ====================

	private String localPlayerName() {
		try {
			return client != null && client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	static Color teamColor(Team team) {
		return TeamColor.onDarkPanel(TeamColor.parse(team.getColor()));
	}
}

package com.revalclan.ui.leaguesbingo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.api.leaguesbingo.LeaguesBingoMeResponse;
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
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import com.revalclan.util.SpriteIcons;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import java.awt.Cursor;
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
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final ItemNameIndex itemNames;
	/** Tile icon name -> game item id; null entries mark names we could not resolve. */
	private final Map<String, Integer> iconItemIds = new HashMap<>();

	private final BackButton backButton;
	private final JLabel titleLabel;
	private final RefreshButton refreshButton;
	private final JPanel body;
	private final JScrollPane scroll;

	private EventsResponse.EventSummary event;
	private Payload payload;
	/** What the backend says this account may do here; null until answered. */
	private LeaguesBingoMeResponse.Viewer me;
	private boolean picking;
	private long loadedAt;
	private boolean loading;
	private String loadError;

	private View view = View.TEAMS;
	private String teamId;
	private String region;
	private String tileId;

	private JPanel tileDetailHolder;
	private final List<TileCell> cells = new ArrayList<>();

	public LeaguesBingoPanel(RevalApiService api, Client client, ItemManager itemManager, SpriteManager spriteManager,
							 ItemNameIndex itemNames, Runnable onClose) {
		this.api = api;
		this.client = client;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.itemNames = itemNames;
		this.onClose = onClose;

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
			me = null;
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
				if (view == View.BOARDS || view == View.BOARD) render(false);
			}),
			error -> SwingUtilities.invokeLater(() -> me = null));
	}

	// ==================== Region picks ====================

	/** A locked board this viewer could unlock right now for the given team. */
	private boolean canUnlock(Team team, Board board, BoardStats stats) {
		if (me == null || !me.canPickFor(team.getId()) || !stats.locked) return false;
		if (board.getTiles().isEmpty() && !payload.isTilesHidden()) return false;
		if (payload.getConfig() != null && payload.getConfig().getDefaultRegions() != null
			&& payload.getConfig().getDefaultRegions().contains(board.getRegion())) return false;
		return team.getPickTokens() != null && team.getPickTokens().getAvailable() > 0;
	}

	private JComponent unlockButton(Team team, Board board) {
		JButton button = new JButton("Unlock board") {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color base = UIConstants.ACCENT_GOLD;
				g2.setColor(!isEnabled() ? TileCell.withAlpha(base, 90) : getModel().isPressed() ? base.darker() : getModel().isRollover() ? base.brighter() : base);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(UIConstants.BACKGROUND);
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setPreferredSize(new Dimension(100, 22));
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setToolTipText("Spend one pick token to unlock this board");
		button.setEnabled(!picking);
		button.addActionListener(e -> confirmUnlock(team, board));
		return button;
	}

	private void confirmUnlock(Team team, Board board) {
		if (picking || me == null) return;
		LeaguesRegions.Region meta = LeaguesRegions.byId(board.getRegion());
		int tokens = team.getPickTokens() != null ? team.getPickTokens().getAvailable() : 0;
		String summary = "<html><body style='width:230px'>"
			+ "<b>Unlock " + escapeHtml(meta.displayName) + " for " + escapeHtml(team.getName()) + "?</b><br><br>"
			+ board.getRows() + "x" + board.getColumns() + " board, " + board.getTiles().size() + " tiles, "
			+ board.getTotalPoints() + " points.<br>"
			+ "This spends 1 of " + tokens + " pick token" + (tokens != 1 ? "s" : "") + " and cannot be undone."
			+ (me.isSuperadmin() && !team.getId().equals(me.getTeamId()) ? "<br><br><i>You are unlocking as a superadmin.</i>" : "")
			+ "</body></html>";
		int choice = javax.swing.JOptionPane.showConfirmDialog(this, summary, "Unlock region",
			javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE);
		if (choice != javax.swing.JOptionPane.OK_OPTION) return;

		long accountHash = client != null ? client.getAccountHash() : -1;
		if (accountHash == -1) return;
		picking = true;
		render(false);
		// Pickers act for their own team; only a superadmin names another team.
		String targetTeam = team.getId().equals(me.getTeamId()) ? null : team.getId();
		api.pickLeaguesBingoRegion(event.getId(), accountHash, board.getRegion(), targetTeam,
			response -> SwingUtilities.invokeLater(() -> {
				picking = false;
				if (response.getData() != null && response.getData().getMe() != null) me = response.getData().getMe();
				load();
				javax.swing.JOptionPane.showMessageDialog(this,
					meta.displayName + " unlocked for " + team.getName() + ".", "Region unlocked",
					javax.swing.JOptionPane.INFORMATION_MESSAGE);
			}),
			error -> SwingUtilities.invokeLater(() -> {
				picking = false;
				render(false);
				javax.swing.JOptionPane.showMessageDialog(this,
					"Could not unlock: " + error.getMessage(), "Unlock failed", javax.swing.JOptionPane.WARNING_MESSAGE);
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

		// Unlocked boards in the order the team unlocked them, locked ones after.
		List<Board> boards = new ArrayList<>(payload.getBoards());
		boards.sort(Comparator.comparing((Board b) -> team.hasUnlocked(b.getRegion()) ? 0 : 1)
			.thenComparing(b -> unlockInstant(team, b.getRegion()))
			.thenComparingInt(b -> LeaguesRegions.order(b.getRegion())));

		for (Board board : boards) {
			body.add(buildBoardRow(team, board));
			body.add(Box.createVerticalStrut(6));
		}
	}

	private static String unlockInstant(Team team, String region) {
		UnlockedRegion u = team.unlockFor(region);
		// ISO-8601 timestamps sort correctly as strings; missing ones sort last.
		return u != null && u.getUnlockedAt() != null ? u.getUnlockedAt() : "~";
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
		if (!stats.locked) {
			top.add(label(stats.percent() + "%", FontManager.getRunescapeSmallFont(), stats.completed == stats.total && stats.total > 0 ? TileCell.COMPLETED : UIConstants.TEXT_SECONDARY), BorderLayout.EAST);
		}

		JPanel lines = new JPanel();
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		lines.setOpaque(false);
		lines.add(top);
		lines.add(Box.createVerticalStrut(4));
		lines.add(label(stats.line(board), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
		lines.add(Box.createVerticalStrut(6));
		if (canUnlock(team, board, stats)) {
			lines.add(unlockButton(team, board));
		} else {
			lines.add(new ProgressBar(stats.percent(), meta.accent));
		}

		card.add(withBanner(meta, stats, lines));

		Clickable.onPress(card, () -> showBoard(board.getRegion()), card::setHovered);
		return card;
	}

	private static final int BANNER_SIZE = 32;

	/**
	 * Region banner (game-cache shield) beside the given content. The banner
	 * arrives asynchronously from the sprite cache; the slot is reserved so
	 * the row does not jump when it lands.
	 */
	private JComponent withBanner(LeaguesRegions.Region meta, BoardStats stats, JComponent content) {
		JPanel wrap = new JPanel(new BorderLayout(10, 0));
		wrap.setOpaque(false);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel banner = new JLabel();
		banner.setPreferredSize(new Dimension(BANNER_SIZE, BANNER_SIZE));
		banner.setHorizontalAlignment(JLabel.CENTER);
		banner.setVerticalAlignment(JLabel.CENTER);
		int spriteId = stats.boardComplete && meta.bannerHighlightSprite >= 0 ? meta.bannerHighlightSprite : meta.bannerSprite;
		if (spriteManager != null && spriteId >= 0) {
			boolean dim = stats.locked;
			SpriteIcons.load(spriteManager, spriteId, BANNER_SIZE, icon -> banner.setIcon(dim ? dimmed(icon) : icon));
		}
		JPanel bannerBox = new JPanel(new BorderLayout());
		bannerBox.setOpaque(false);
		bannerBox.add(banner, BorderLayout.CENTER);

		wrap.add(bannerBox, BorderLayout.WEST);
		wrap.add(content, BorderLayout.CENTER);
		return wrap;
	}

	private static javax.swing.ImageIcon dimmed(javax.swing.ImageIcon icon) {
		java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(icon.getIconWidth(), icon.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = out.createGraphics();
		g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f));
		icon.paintIcon(null, g2, 0, 0);
		g2.dispose();
		return new javax.swing.ImageIcon(out);
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
		if (!stats.locked) {
			top.add(label(stats.percent() + "%", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY), BorderLayout.EAST);
		}
		JPanel headerLines = new JPanel();
		headerLines.setLayout(new BoxLayout(headerLines, BoxLayout.Y_AXIS));
		headerLines.setOpaque(false);
		headerLines.add(top);
		headerLines.add(Box.createVerticalStrut(4));
		headerLines.add(label(stats.line(board), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
		headerLines.add(Box.createVerticalStrut(6));
		if (canUnlock(team, board, stats)) {
			headerLines.add(unlockButton(team, board));
		} else {
			headerLines.add(new ProgressBar(stats.percent(), meta.accent));
		}
		header.add(withBanner(meta, stats, headerLines));
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
					loadTileIcon(tile, target);
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

	// ==================== Tile icons (game cache) ====================

	/**
	 * Tile icons come from the RuneLite item cache, never the wiki. The tile's
	 * icon name is matched against the item price list first (it is the
	 * representative item the admin picked), then against the items the tile's
	 * own requirements name, and finally the first required item.
	 */
	private void loadTileIcon(Tile tile, TileCell cell) {
		if (itemManager == null) return;
		resolveItemId(tile, itemId -> {
			if (itemId == null) return;
			try {
				AsyncBufferedImage image = itemManager.getImage(itemId);
				cell.setIcon(image);
				image.onLoaded(() -> SwingUtilities.invokeLater(cell::repaint));
			} catch (Exception ignored) {
				// A bad id must never break the grid.
			}
		});
	}

	private void resolveItemId(Tile tile, java.util.function.Consumer<Integer> callback) {
		String icon = tile.getIcon() != null ? tile.getIcon().replace('_', ' ').trim() : "";
		if (icon.isEmpty()) {
			callback.accept(firstRequirementItem(tile));
			return;
		}
		if (iconItemIds.containsKey(icon)) {
			Integer cached = iconItemIds.get(icon);
			callback.accept(cached != null ? cached : firstRequirementItem(tile));
			return;
		}

		Integer found = requirementItemNamed(tile, icon);
		if (found == null) found = searchItem(icon);
		String bare = icon.replaceAll("\\s*\\([^)]*\\)$", "").replaceAll("\\s+\\d+$", "").trim();
		if (found == null && !bare.equals(icon)) found = searchItem(bare);
		if (found != null) {
			iconItemIds.put(icon, found);
			callback.accept(found);
			return;
		}

		// Untradeables are absent from the price list: ask the game cache.
		if (itemNames == null) {
			iconItemIds.put(icon, null);
			callback.accept(firstRequirementItem(tile));
			return;
		}
		itemNames.resolve(icon, id -> {
			if (id == null && !bare.equals(icon)) {
				itemNames.resolve(bare, id2 -> {
					iconItemIds.put(icon, id2);
					callback.accept(id2 != null ? id2 : firstRequirementItem(tile));
				});
				return;
			}
			iconItemIds.put(icon, id);
			callback.accept(id != null ? id : firstRequirementItem(tile));
		});
	}

	private Integer searchItem(String name) {
		try {
			List<ItemPrice> results = itemManager.search(name);
			for (ItemPrice p : results) {
				if (p.getName() != null && p.getName().equalsIgnoreCase(name)) return p.getId();
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static Integer requirementItemNamed(Tile tile, String name) {
		if (tile.getRequirements() == null) return null;
		for (JsonObject r : tile.getRequirements().getRequirements()) {
			if (!r.has("items") || !r.get("items").isJsonArray()) continue;
			for (JsonElement e : r.getAsJsonArray("items")) {
				if (!e.isJsonObject()) continue;
				JsonObject item = e.getAsJsonObject();
				String itemName = RequirementText.str(item, "itemName", "");
				Double id = RequirementText.number(item, "itemId");
				if (id != null && itemName.equalsIgnoreCase(name)) return id.intValue();
			}
		}
		return null;
	}

	private static Integer firstRequirementItem(Tile tile) {
		if (tile.getRequirements() == null) return null;
		for (JsonObject r : tile.getRequirements().getRequirements()) {
			if (!r.has("items") || !r.get("items").isJsonArray()) continue;
			for (JsonElement e : r.getAsJsonArray("items")) {
				if (!e.isJsonObject()) continue;
				Double id = RequirementText.number(e.getAsJsonObject(), "itemId");
				if (id != null) return id.intValue();
			}
		}
		return null;
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
			boolean anyMatch = "any".equalsIgnoreCase(reqs.getMatchType());
			for (int i = 0; i < list.size(); i++) {
				LeaguesBingoResponse.RequirementProgress rp = progress != null ? progress.requirement(i) : null;
				// 'any' tiles finish on one requirement, so only rows the
				// progress data flags count as done; 'all' tiles finish only
				// when every row is done.
				boolean rowDone = rp != null && rp.isCompleted() || (completed && !anyMatch);
				card.add(Box.createVerticalStrut(5));
				card.add(buildRequirementRow(list.get(i), rp, rowDone));
			}
		}

		if (tile.getDescription() != null && !tile.getDescription().trim().isEmpty()) {
			card.add(Box.createVerticalStrut(8));
			card.add(wrapped(tile.getDescription(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED, TEXT_WIDTH));
		}
		return card;
	}

	private static final int DOT_SIZE = 9;
	private static final int DOT_GAP = 6;
	private static final int REQ_INDENT = DOT_SIZE + DOT_GAP;

	private JComponent buildRequirementRow(JsonObject requirement, LeaguesBingoResponse.RequirementProgress rp, boolean done) {
		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		text.setAlignmentX(Component.LEFT_ALIGNMENT);

		Font font = FontManager.getRunescapeSmallFont();
		Color color = done ? TileCell.COMPLETED : UIConstants.TEXT_SECONDARY;
		java.awt.FontMetrics fm = new JLabel().getFontMetrics(font);

		// Count ("5/5") sits at the end of the first line, next to the task.
		String count = progressCount(rp);
		int countWidth = count != null ? fm.stringWidth(count) + 8 : 0;

		// The first line is a plain label carrying the dot as its icon, so
		// Swing centers the two on one line; wrapped continuation lines hang
		// under the text. HTML wrapping would put the dot at the block's top.
		List<String> lines = wrapLines(RequirementText.describe(requirement), fm, TEXT_WIDTH - REQ_INDENT - countWidth);
		JLabel first = new JLabel(lines.isEmpty() ? "" : lines.get(0), new DotIcon(done), JLabel.LEFT);
		first.setFont(font);
		first.setForeground(color);
		first.setIconTextGap(DOT_GAP);

		JPanel firstLine = new JPanel(new BorderLayout(8, 0));
		firstLine.setOpaque(false);
		firstLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		firstLine.add(first, BorderLayout.CENTER);
		if (count != null) {
			JLabel countLabel = label(count, font, done ? TileCell.COMPLETED : TileCell.IN_PROGRESS);
			firstLine.add(countLabel, BorderLayout.EAST);
		}
		firstLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, firstLine.getPreferredSize().height));
		text.add(firstLine);
		for (int i = 1; i < lines.size(); i++) {
			JLabel more = label(lines.get(i), font, color);
			more.setBorder(new EmptyBorder(0, REQ_INDENT, 0, 0));
			text.add(more);
		}

		List<String> options = RequirementText.optionNames(requirement);
		if (options.size() > 1) {
			java.util.Set<String> obtained = obtainedNames(rp != null ? rp.getProgressMetadata() : null);
			// Obtained names first so they survive the cap on long lists (49 pets...).
			List<String> ordered = new ArrayList<>();
			for (String n : options) if (obtained.contains(n.toLowerCase())) ordered.add(n);
			for (String n : options) if (!obtained.contains(n.toLowerCase())) ordered.add(n);
			StringBuilder html = new StringBuilder();
			int shown = Math.min(ordered.size(), 8);
			for (int i = 0; i < shown; i++) {
				if (i > 0) html.append(", ");
				String name = ordered.get(i);
				String escaped = escapeHtml(name);
				if (obtained.contains(name.toLowerCase())) {
					html.append("<span style='color:#4caf50'>").append(escaped).append("</span>");
				} else {
					html.append(escaped);
				}
			}
			if (ordered.size() > shown) html.append(" +").append(ordered.size() - shown).append(" more");
			JLabel optionsLabel = wrappedHtml(html.toString(), font, UIConstants.TEXT_MUTED, TEXT_WIDTH - REQ_INDENT);
			optionsLabel.setBorder(new EmptyBorder(0, REQ_INDENT, 0, 0));
			text.add(optionsLabel);
		}

		String who = rp != null ? contributors(rp.getProgressMetadata()) : "";
		if (!who.isEmpty()) {
			JLabel subLabel = wrapped("by " + who, font, done ? TileCell.COMPLETED : TileCell.IN_PROGRESS, TEXT_WIDTH - REQ_INDENT);
			subLabel.setBorder(new EmptyBorder(0, REQ_INDENT, 0, 0));
			text.add(subLabel);
		}

		return text;
	}

	/** "current/target" when the requirement counts past one, else null. */
	private static String progressCount(LeaguesBingoResponse.RequirementProgress rp) {
		if (rp == null) return null;
		JsonObject meta = rp.getProgressMetadata();
		Double target = RequirementText.number(meta, "targetValue");
		Double current = RequirementText.number(meta, "currentTotalCount");
		if (current == null) current = rp.getProgressValue();
		if (target == null || target <= 1 || current == null) return null;
		return RequirementText.fmt(Math.round(current)) + "/" + RequirementText.fmt(Math.round(target));
	}

	/** Greedy word wrap measured with the real font, for plain (non-HTML) labels. */
	private static List<String> wrapLines(String text, java.awt.FontMetrics fm, int width) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.trim().isEmpty()) return lines;
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (fm.stringWidth(candidate) <= width || line.length() == 0) {
				line.setLength(0);
				line.append(candidate);
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (line.length() > 0) lines.add(line.toString());
		return lines;
	}

	/** Item and pet names (lowercased) the team has already turned in for this requirement. */
	private static java.util.Set<String> obtainedNames(JsonObject meta) {
		java.util.Set<String> names = new java.util.HashSet<>();
		if (meta == null) return names;
		collectNames(meta.get("lastItemsObtained"), names);
		JsonElement contributions = meta.get("playerContributions");
		if (contributions != null && contributions.isJsonArray()) {
			for (JsonElement c : contributions.getAsJsonArray()) {
				if (!c.isJsonObject()) continue;
				collectNames(c.getAsJsonObject().get("items"), names);
				collectNames(c.getAsJsonObject().get("pets"), names);
			}
		}
		return names;
	}

	private static void collectNames(JsonElement array, java.util.Set<String> into) {
		if (array == null || !array.isJsonArray()) return;
		for (JsonElement e : array.getAsJsonArray()) {
			if (!e.isJsonObject()) continue;
			String n = RequirementText.str(e.getAsJsonObject(), "itemName", null);
			if (n == null) n = RequirementText.str(e.getAsJsonObject(), "petName", null);
			if (n != null) into.add(n.toLowerCase());
		}
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
		return wrappedHtml(escapeHtml(text), font, color, width);
	}

	private static JLabel wrappedHtml(String html, Font font, Color color, int width) {
		JLabel l = new JLabel("<html><div style='width:" + width + "px'>" + html + "</div></html>");
		l.setFont(font);
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static String escapeHtml(String text) {
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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

	/** Filled green dot when done, hollow muted ring otherwise; an Icon so a label centers it with its text. */
	private static final class DotIcon implements javax.swing.Icon {
		private final boolean done;

		DotIcon(boolean done) {
			this.done = done;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (done) {
				g2.setColor(TileCell.COMPLETED);
				g2.fillOval(x, y, DOT_SIZE, DOT_SIZE);
			} else {
				g2.setColor(UIConstants.TEXT_MUTED);
				g2.setStroke(new BasicStroke(1.5f));
				g2.drawOval(x + 1, y + 1, DOT_SIZE - 2, DOT_SIZE - 2);
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth() {
			return DOT_SIZE;
		}

		@Override
		public int getIconHeight() {
			return DOT_SIZE;
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

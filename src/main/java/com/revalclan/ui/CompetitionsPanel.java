package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.competitions.*;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompetitionsPanel extends JPanel {
	private RevalApiService apiService;
	private Client client;
	private RefreshButton refreshButton;

	private final CardLayout cardLayout;
	private final JPanel cardContainer;
	private final JPanel contentPanel;
	private final JPanel detailViewPanel;

	private List<VotesResponse.Vote> activeVotes = new ArrayList<>();
	private List<CompetitionsResponse.Competition> activeCompetitions = new ArrayList<>();
	private List<CompetitionsResponse.Competition> scheduledCompetitions = new ArrayList<>();
	private Map<String, String> myVotes = new HashMap<>();

	public CompetitionsPanel() {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		cardLayout = new CardLayout();
		cardContainer = new JPanel(cardLayout);
		cardContainer.setBackground(UIConstants.BACKGROUND);

		// List view
		JPanel listViewPanel = new JPanel(new BorderLayout());
		listViewPanel.setBackground(UIConstants.BACKGROUND);
		listViewPanel.add(createHeader(), BorderLayout.NORTH);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(UIConstants.BACKGROUND);
		contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

		listViewPanel.add(wrapScrollable(contentPanel), BorderLayout.CENTER);

		// Detail view
		detailViewPanel = new JPanel(new BorderLayout());
		detailViewPanel.setBackground(UIConstants.BACKGROUND);

		cardContainer.add(listViewPanel, "LIST");
		cardContainer.add(detailViewPanel, "DETAIL");
		add(cardContainer, BorderLayout.CENTER);
	}

	private JPanel createHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.CARD_BG);
		header.setBorder(new EmptyBorder(10, 12, 10, 12));
		header.add(new PanelTitle("Competitions", UIConstants.TEXT_PRIMARY), BorderLayout.WEST);
		refreshButton = new RefreshButton(this::refresh);
		header.add(refreshButton, BorderLayout.EAST);
		return header;
	}

	public void init(RevalApiService apiService, Client client) {
		this.apiService = apiService;
		this.client = client;
		loadData(true);
	}

	public void refresh() {
		loadData(false);
	}

	private void loadData(boolean showLoadingState) {
		if (apiService == null) return;
		if (showLoadingState) showLoading();
		refreshButton.setLoading(true);

		final boolean[] loaded = {false, false, false};
		Runnable checkComplete = () -> {
			if (loaded[0] && loaded[1] && loaded[2]) {
				SwingUtilities.invokeLater(() -> { refreshButton.setLoading(false); buildContent(); });
			}
		};

		apiService.fetchVotes(
			response -> {
				activeVotes = response.getData() != null && response.getData().getVotes() != null
					? response.getData().getVotes() : new ArrayList<>();
				fetchMyVotes(activeVotes);
				loaded[0] = true;
				checkComplete.run();
			},
			error -> { activeVotes = new ArrayList<>(); loaded[0] = true; checkComplete.run(); }
		);

		apiService.fetchActiveCompetitions(
			response -> {
				activeCompetitions = response.getData() != null && response.getData().getCompetitions() != null
					? response.getData().getCompetitions() : new ArrayList<>();
				loaded[1] = true;
				checkComplete.run();
			},
			error -> { activeCompetitions = new ArrayList<>(); loaded[1] = true; checkComplete.run(); }
		);

		apiService.fetchScheduledCompetitions(
			response -> {
				scheduledCompetitions = response.getData() != null && response.getData().getCompetitions() != null
					? response.getData().getCompetitions() : new ArrayList<>();
				loaded[2] = true;
				checkComplete.run();
			},
			error -> { scheduledCompetitions = new ArrayList<>(); loaded[2] = true; checkComplete.run(); }
		);
	}

	private void fetchMyVotes(List<VotesResponse.Vote> votes) {
		if (client == null || client.getAccountHash() == -1) return;
		myVotes.clear();
		for (VotesResponse.Vote vote : votes) {
			apiService.fetchMyVote(vote.getId(), client.getAccountHash(),
				response -> {
					if (response.getData() != null && response.getData().getHasVoted() && response.getData().getOptionId() != null) {
						myVotes.put(vote.getId(), response.getData().getOptionId());
						SwingUtilities.invokeLater(this::buildContent);
					}
				},
				error -> {}
			);
		}
	}

	private void showLoading() {
		contentPanel.removeAll();
		contentPanel.add(Box.createVerticalStrut(30));
		contentPanel.add(label("Loading competitions...", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY, Component.CENTER_ALIGNMENT));
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void buildContent() {
		contentPanel.removeAll();
		boolean hasContent = false;

		if (!activeVotes.isEmpty()) {
			hasContent = true;
			addSection("Active Votes");
			for (VotesResponse.Vote vote : activeVotes) {
				contentPanel.add(createVoteCard(vote));
				contentPanel.add(Box.createVerticalStrut(8));
			}
			contentPanel.add(Box.createVerticalStrut(12));
		}

		if (!activeCompetitions.isEmpty()) {
			hasContent = true;
			addSection("Active Competitions");
			for (CompetitionsResponse.Competition comp : activeCompetitions) {
				contentPanel.add(createCompetitionCard(comp, true));
				contentPanel.add(Box.createVerticalStrut(8));
			}
			contentPanel.add(Box.createVerticalStrut(12));
		}

		if (!scheduledCompetitions.isEmpty()) {
			hasContent = true;
			addSection("Upcoming Competitions");
			for (CompetitionsResponse.Competition comp : scheduledCompetitions) {
				contentPanel.add(createCompetitionCard(comp, false));
				contentPanel.add(Box.createVerticalStrut(8));
			}
		}

		if (!hasContent) {
			contentPanel.add(Box.createVerticalStrut(30));
			contentPanel.add(label("No active competitions or votes", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY, Component.CENTER_ALIGNMENT));
		}

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void addSection(String text) {
		contentPanel.add(label(text, FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY));
		contentPanel.add(Box.createVerticalStrut(6));
	}

	private JPanel createVoteCard(VotesResponse.Vote vote) {
		JPanel card = createCard();

		card.add(label(vote.getTitle(), FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_GOLD));
		if (vote.getDescription() != null && !vote.getDescription().isEmpty()) {
			card.add(Box.createVerticalStrut(4));
			card.add(label(vote.getDescription(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
		}

		card.add(Box.createVerticalStrut(6));
		card.add(label("Ends: " + formatTimeRemaining(vote.getVoteEndDate()), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));
		card.add(label(vote.getTotalVotes() + " total votes", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));

		if (vote.getOptions() != null && !vote.getOptions().isEmpty()) {
			card.add(Box.createVerticalStrut(10));
			String myVotedId = myVotes.get(vote.getId());
			for (VotesResponse.VoteOption option : vote.getOptions()) {
				boolean isMyVote = option.getId() != null && option.getId().equals(myVotedId);
				card.add(createVoteOptionRow(vote, option, isMyVote));
				card.add(Box.createVerticalStrut(4));
			}
		}

		return roundedWrapper(card);
	}

	private JPanel createVoteOptionRow(VotesResponse.Vote vote, VotesResponse.VoteOption option, boolean isMyVote) {
		Color selectedBg = new Color(UIConstants.ACCENT_BLUE.getRed(), UIConstants.ACCENT_BLUE.getGreen(), UIConstants.ACCENT_BLUE.getBlue(), 40);
		Color defaultBg = isMyVote ? selectedBg : UIConstants.BACKGROUND;

		JPanel row = roundedPanel(defaultBg, isMyVote ? UIConstants.ACCENT_BLUE : null);
		row.setLayout(new BorderLayout(8, 0));
		row.setBorder(new EmptyBorder(8, 10, 8, 10));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		row.add(label(option.getTemplateName(), FontManager.getRunescapeSmallFont(),
			isMyVote ? UIConstants.ACCENT_BLUE : UIConstants.TEXT_PRIMARY, -1), BorderLayout.WEST);

		int pct = vote.getTotalVotes() > 0 ? (int) Math.round((option.getVoteCount() * 100.0) / vote.getTotalVotes()) : 0;
		row.add(label(option.getVoteCount() + " (" + pct + "%)", FontManager.getRunescapeSmallFont(),
			UIConstants.ACCENT_BLUE, -1), BorderLayout.EAST);

		row.addMouseListener(new MouseAdapter() {
			public void mouseEntered(MouseEvent e) { row.setBackground(UIConstants.CARD_HOVER); row.repaint(); }
			public void mouseExited(MouseEvent e) { row.setBackground(defaultBg); row.repaint(); }
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) castVote(vote.getId(), option.getId());
			}
		});

		return row;
	}

	private void castVote(String voteId, String optionId) {
		if (apiService == null || client == null || client.getAccountHash() == -1) {
			JOptionPane.showMessageDialog(this, "Please log in to vote", "Not Logged In", JOptionPane.WARNING_MESSAGE);
			return;
		}

		apiService.castVote(voteId, optionId, client.getAccountHash(),
			response -> SwingUtilities.invokeLater(() -> {
				String msg = response.getData() != null && response.getData().getPreviousOptionId() != null
					? "Vote changed!" : "Vote cast!";
				JOptionPane.showMessageDialog(this, msg, "Voted", JOptionPane.INFORMATION_MESSAGE);
				refresh();
			}),
			error -> SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(this, "Failed: " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE))
		);
	}

	private JPanel createCompetitionCard(CompetitionsResponse.Competition comp, boolean isActive) {
		JPanel card = createCard();
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		card.add(label(comp.getName(), FontManager.getRunescapeBoldFont(), isActive ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_PRIMARY));
		if (comp.getDescription() != null && !comp.getDescription().isEmpty()) {
			card.add(Box.createVerticalStrut(4));
			card.add(label(comp.getDescription(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
		}

		card.add(Box.createVerticalStrut(6));
		String timeText = isActive ? "Ends: " + formatTimeRemaining(comp.getEndDate())
			: "Starts: " + formatTimeRemaining(comp.getStartDate());
		card.add(label(timeText, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));

		if (comp.getParticipantCount() != null && comp.getParticipantCount() > 0) {
			card.add(label(comp.getParticipantCount() + " participants", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));
		}

		if (isActive && comp.getLeaderboard() != null && !comp.getLeaderboard().isEmpty()) {
			card.add(Box.createVerticalStrut(8));
			card.add(createLeaderboardPreview(comp.getLeaderboard(), comp.getCompetitionType()));
		}

		if (comp.getRewardConfig() != null && !comp.getRewardConfig().isEmpty()) {
			card.add(Box.createVerticalStrut(6));
			card.add(label(formatRewards(comp.getRewardConfig()), FontManager.getRunescapeSmallFont(), UIConstants.ACCENT_GOLD));
		}

		card.addMouseListener(new MouseAdapter() {
			public void mouseEntered(MouseEvent e) { card.setBackground(UIConstants.CARD_HOVER); }
			public void mouseExited(MouseEvent e) { card.setBackground(UIConstants.CARD_BG); }
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) showCompetitionDetails(comp);
			}
		});

		return roundedWrapper(card);
	}

	private JPanel createLeaderboardPreview(List<CompetitionsResponse.LeaderboardEntry> leaderboard, String type) {
		JPanel preview = new JPanel();
		preview.setLayout(new BoxLayout(preview, BoxLayout.Y_AXIS));
		preview.setOpaque(false);
		preview.setAlignmentX(Component.LEFT_ALIGNMENT);

		String suffix = getValueSuffix(type);
		for (int i = 0; i < Math.min(3, leaderboard.size()); i++) {
			CompetitionsResponse.LeaderboardEntry entry = leaderboard.get(i);
			Integer value = entry.getCurrentValue() != null ? entry.getCurrentValue() : entry.getFinalValue();

			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

			row.add(label((i + 1) + ". " + entry.getOsrsNickname(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_PRIMARY, -1), BorderLayout.WEST);
			row.add(label(value != null ? formatValue(value) + suffix : "-", FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_BLUE, -1), BorderLayout.EAST);
			preview.add(row);
		}

		return preview;
	}

	private void showCompetitionDetails(CompetitionsResponse.Competition comp) {
		detailViewPanel.removeAll();

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.CARD_BG);
		header.setBorder(new EmptyBorder(8, 10, 8, 10));
		header.add(new BackButton("< Back", this::showListView), BorderLayout.WEST);
		header.add(label(comp.getName(), FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_GOLD, -1), BorderLayout.EAST);
		detailViewPanel.add(header, BorderLayout.NORTH);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(UIConstants.BACKGROUND);
		content.setBorder(new EmptyBorder(12, 12, 12, 12));
		content.add(Box.createVerticalStrut(20));
		content.add(label("Loading leaderboard...", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY, Component.CENTER_ALIGNMENT));

		detailViewPanel.add(wrapScrollable(content), BorderLayout.CENTER);
		detailViewPanel.revalidate();
		detailViewPanel.repaint();
		cardLayout.show(cardContainer, "DETAIL");

		apiService.fetchCompetitionDetails(comp.getId(),
			response -> SwingUtilities.invokeLater(() -> buildDetailsContent(content, response.getData())),
			error -> SwingUtilities.invokeLater(() -> {
				content.removeAll();
				content.add(Box.createVerticalStrut(20));
				content.add(label("Failed to load: " + error.getMessage(), FontManager.getRunescapeSmallFont(), UIConstants.ERROR_COLOR, Component.CENTER_ALIGNMENT));
				content.revalidate();
				content.repaint();
			})
		);
	}

	private void buildDetailsContent(JPanel content, CompetitionsResponse.Competition comp) {
		content.removeAll();

		if (comp == null) {
			content.add(label("Competition not found", FontManager.getRunescapeSmallFont(), UIConstants.ERROR_COLOR, Component.CENTER_ALIGNMENT));
			content.revalidate();
			content.repaint();
			return;
		}

		if (comp.getDescription() != null) {
			content.add(label(comp.getDescription(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
			content.add(Box.createVerticalStrut(8));
		}

		boolean isActive = "active".equalsIgnoreCase(comp.getStatus());
		String timeText = isActive ? "Ends: " + formatTimeRemaining(comp.getEndDate())
			: "Starts: " + formatTimeRemaining(comp.getStartDate());
		content.add(label(timeText, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));

		if (comp.getParticipantCount() != null) {
			content.add(label(comp.getParticipantCount() + " participants", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));
		}

		content.add(Box.createVerticalStrut(12));
		content.add(label("Leaderboard", FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY));
		content.add(Box.createVerticalStrut(6));

		if (comp.getLeaderboard() != null && !comp.getLeaderboard().isEmpty()) {
			for (int i = 0; i < comp.getLeaderboard().size(); i++) {
				content.add(createLeaderboardRow(comp.getLeaderboard().get(i), i + 1, comp.getCompetitionType()));
				content.add(Box.createVerticalStrut(2));
			}
		} else {
			content.add(label("No participants yet", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));
		}

		if (comp.getSideQuests() != null && !comp.getSideQuests().isEmpty()) {
			content.add(Box.createVerticalStrut(16));
			content.add(label("Side Quests", FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY));
			content.add(Box.createVerticalStrut(6));
			for (CompetitionsResponse.SideQuest sq : comp.getSideQuests()) {
				content.add(createSideQuestCard(sq));
				content.add(Box.createVerticalStrut(8));
			}
		}

		content.revalidate();
		content.repaint();
	}

	private JPanel createLeaderboardRow(CompetitionsResponse.LeaderboardEntry entry, int rank, String type) {
		JPanel row = roundedPanel(UIConstants.CARD_BG, null);
		row.setLayout(new BorderLayout(4, 0));
		row.setBorder(new EmptyBorder(6, 10, 6, 10));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

		Color rankColor = rank == 1 ? new Color(255, 215, 0)
			: rank == 2 ? new Color(192, 192, 192)
			: rank == 3 ? new Color(205, 127, 50)
			: UIConstants.TEXT_SECONDARY;

		JLabel rankLabel = label("#" + rank, FontManager.getRunescapeBoldFont(), rankColor, -1);
		rankLabel.setPreferredSize(new Dimension(28, 20));

		Integer value = entry.getCurrentValue() != null ? entry.getCurrentValue() : entry.getFinalValue();

		row.add(rankLabel, BorderLayout.WEST);
		row.add(label(entry.getOsrsNickname(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_PRIMARY, -1), BorderLayout.CENTER);
		row.add(label(value != null ? formatValue(value) + getValueSuffix(type) : "-", FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_GOLD, -1), BorderLayout.EAST);

		return row;
	}

	private JPanel createSideQuestCard(CompetitionsResponse.SideQuest sq) {
		JPanel card = createCard();
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

		card.add(label(sq.getName(), FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_PURPLE));
		if (sq.getDescription() != null) {
			card.add(label(sq.getDescription(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));
		}

		if (sq.getLeaderboard() != null && !sq.getLeaderboard().isEmpty()) {
			card.add(Box.createVerticalStrut(6));
			String suffix = getValueSuffix(sq.getSideQuestType());
			for (int i = 0; i < Math.min(3, sq.getLeaderboard().size()); i++) {
				CompetitionsResponse.LeaderboardEntry entry = sq.getLeaderboard().get(i);
				card.add(label((i + 1) + ". " + entry.getOsrsNickname() + " - " + formatValue(entry.getCurrentValue()) + suffix,
					FontManager.getRunescapeSmallFont(), UIConstants.TEXT_PRIMARY));
			}
		}

		return roundedWrapper(card);
	}

	private void showListView() {
		cardLayout.show(cardContainer, "LIST");
	}

	// ── UI Helpers ──────────────────────────────────────────────────────

	/** Creates a left-aligned JLabel. */
	private JLabel label(String text, Font font, Color color) {
		return label(text, font, color, Component.LEFT_ALIGNMENT);
	}

	/** Creates a JLabel with optional alignment (-1 to skip alignment). */
	private JLabel label(String text, Font font, Color color, float alignment) {
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		if (alignment >= 0) l.setAlignmentX(alignment);
		return l;
	}

	/** Creates a card panel with BoxLayout.Y_AXIS. */
	private JPanel createCard() {
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(UIConstants.CARD_BG);
		card.setBorder(new EmptyBorder(12, 14, 12, 14));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
		return card;
	}

	/** Creates a non-opaque JPanel with rounded paintComponent and optional border. */
	private JPanel roundedPanel(Color bg, Color borderColor) {
		JPanel panel = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(getBackground());
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
				if (borderColor != null) {
					g2d.setColor(borderColor);
					g2d.setStroke(new BasicStroke(2));
					g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 4, 4);
				}
				g2d.dispose();
			}
		};
		panel.setOpaque(false);
		panel.setBackground(bg);
		return panel;
	}

	/** Wraps an inner card panel with a rounded-corner outer wrapper. */
	private JPanel roundedWrapper(JPanel inner) {
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(inner.getBackground());
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2d.dispose();
			}
		};
		wrapper.setOpaque(false);
		wrapper.add(inner, BorderLayout.CENTER);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.setMaximumSize(inner.getMaximumSize());
		return wrapper;
	}

	/** Wraps a content panel in a scroll pane pinned to the top. */
	private JScrollPane wrapScrollable(JPanel content) {
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(UIConstants.BACKGROUND);
		wrapper.add(content, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(wrapper);
		scroll.setBackground(UIConstants.BACKGROUND);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(UIConstants.BACKGROUND);
		return scroll;
	}

	// ── Formatting ──────────────────────────────────────────────────────

	private String formatTimeRemaining(String dateStr) {
		if (dateStr == null) return "Unknown";
		try {
			ZonedDateTime target = ZonedDateTime.parse(dateStr);
			ZonedDateTime now = ZonedDateTime.now();
			long days = ChronoUnit.DAYS.between(now, target);
			long hours = ChronoUnit.HOURS.between(now, target) % 24;
			if (days < 0 || (days == 0 && hours < 0)) return "Ended";
			if (days > 0) return days + "d " + hours + "h";
			return hours + "h " + (ChronoUnit.MINUTES.between(now, target) % 60) + "m";
		} catch (Exception e) {
			return dateStr;
		}
	}

	private String formatRewards(Map<String, Integer> rewards) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= 3; i++) {
			if (rewards.containsKey(String.valueOf(i))) {
				if (sb.length() > 0) sb.append(" • ");
				sb.append(i == 1 ? "1st" : i == 2 ? "2nd" : "3rd").append(": ").append(rewards.get(String.valueOf(i))).append(" pts");
			}
		}
		return sb.toString();
	}

	private String formatValue(Integer value) {
		if (value == null) return "-";
		if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
		if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
		return String.valueOf(value);
	}

	private String getValueSuffix(String type) {
		if (type == null) return "";
		String t = type.toLowerCase();
		if (t.contains("xp") || t.contains("experience") || t.contains("skill")) return " XP";
		if (t.contains("kc") || t.contains("kill") || t.contains("boss") || t.contains("npc")) return " KC";
		return "";
	}
}

package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.leaderboard.LeaderboardResponse;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LeaderboardPanel extends JPanel {
	private RevalApiService apiService;
	private UIAssetLoader assetLoader;

	private final CardLayout cardLayout;
	private final JPanel cardContainer;
	private final JPanel contentPanel;
	private final JPanel profileViewPanel;
	private JTextField searchField;

	private List<LeaderboardResponse.LeaderboardEntry> allEntries = new ArrayList<>();
	private List<LeaderboardResponse.LeaderboardEntry> filteredEntries = new ArrayList<>();

	public LeaderboardPanel() {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		cardLayout = new CardLayout();
		cardContainer = new JPanel(cardLayout);
		cardContainer.setBackground(UIConstants.BACKGROUND);

		// List view
		JPanel listViewPanel = new JPanel(new BorderLayout());
		listViewPanel.setBackground(UIConstants.BACKGROUND);
		listViewPanel.add(createSearchPanel(), BorderLayout.NORTH);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(UIConstants.BACKGROUND);
		contentPanel.setBorder(new EmptyBorder(4, 6, 6, 6));

		JPanel contentWrapper = new JPanel(new BorderLayout());
		contentWrapper.setBackground(UIConstants.BACKGROUND);
		contentWrapper.add(contentPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(contentWrapper);
		scrollPane.setBackground(UIConstants.BACKGROUND);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);

		listViewPanel.add(scrollPane, BorderLayout.CENTER);

		// Profile view
		profileViewPanel = new JPanel(new BorderLayout());
		profileViewPanel.setBackground(UIConstants.BACKGROUND);

		cardContainer.add(listViewPanel, "LIST");
		cardContainer.add(profileViewPanel, "PROFILE");

		add(cardContainer, BorderLayout.CENTER);
	}

	private JPanel createSearchPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(UIConstants.CARD_BG);
		panel.setBorder(new EmptyBorder(8, 10, 8, 10));

		searchField = new JTextField();
		searchField.setBackground(UIConstants.BACKGROUND);
		searchField.setForeground(UIConstants.TEXT_PRIMARY);
		searchField.setCaretColor(UIConstants.TEXT_PRIMARY);
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(UIConstants.CARD_HOVER, 1),
			new EmptyBorder(6, 10, 6, 10)
		));
		searchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		searchField.putClientProperty("JTextField.placeholderText", "Search player...");

		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { filterLeaderboard(); }
			public void removeUpdate(DocumentEvent e) { filterLeaderboard(); }
			public void changedUpdate(DocumentEvent e) { filterLeaderboard(); }
		});

		panel.add(searchField, BorderLayout.CENTER);
		return panel;
	}

	private JPanel createBackHeader(String playerName) {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.CARD_BG);
		header.setBorder(new EmptyBorder(8, 10, 8, 10));

		BackButton backButton = new BackButton("← Back", this::showListView);

		JLabel nameLabel = new JLabel(playerName);
		nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
		nameLabel.setForeground(UIConstants.ACCENT_GOLD);

		header.add(backButton, BorderLayout.WEST);
		header.add(nameLabel, BorderLayout.EAST);

		return header;
	}

	public void init(RevalApiService apiService, UIAssetLoader assetLoader) {
		this.apiService = apiService;
		this.assetLoader = assetLoader;
		loadLeaderboard();
	}

	public void refresh() {
		loadLeaderboard();
	}

	public void showListView() {
		cardLayout.show(cardContainer, "LIST");
	}

	private void showPlayerProfile(int osrsAccountId, String playerName) {
		profileViewPanel.removeAll();
		profileViewPanel.add(createBackHeader(playerName), BorderLayout.NORTH);

		ProfilePanel profile = new ProfilePanel();
		profile.init(apiService, null, assetLoader);
		profile.loadAccountById(osrsAccountId);

		profileViewPanel.add(profile, BorderLayout.CENTER);
		profileViewPanel.revalidate();
		profileViewPanel.repaint();

		cardLayout.show(cardContainer, "PROFILE");
	}

	private void loadLeaderboard() {
		if (apiService == null) return;

		showLoading();
		apiService.fetchLeaderboard(
			response -> {
				if (response.getData() != null && response.getData().getLeaderboard() != null) {
					allEntries = response.getData().getLeaderboard();
					filteredEntries = new ArrayList<>(allEntries);
					SwingUtilities.invokeLater(this::buildLeaderboard);
				} else {
					SwingUtilities.invokeLater(() -> showMessage("No leaderboard data"));
				}
			},
			error -> SwingUtilities.invokeLater(() -> showMessage("Failed to load leaderboard"))
		);
	}

	private void filterLeaderboard() {
		String query = searchField.getText().toLowerCase().trim();
		filteredEntries = query.isEmpty() ? new ArrayList<>(allEntries) :
			allEntries.stream()
				.filter(e -> e.getOsrsNickname().toLowerCase().contains(query))
				.collect(Collectors.toList());
		buildLeaderboard();
	}

	private void showLoading() {
		contentPanel.removeAll();
		contentPanel.add(Box.createVerticalStrut(20));
		JLabel label = new JLabel("Loading...");
		label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		label.setForeground(UIConstants.TEXT_SECONDARY);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		contentPanel.add(label);
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void showMessage(String message) {
		contentPanel.removeAll();
		contentPanel.add(Box.createVerticalStrut(20));
		JLabel label = new JLabel(message);
		label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		label.setForeground(UIConstants.TEXT_SECONDARY);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		contentPanel.add(label);
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void buildLeaderboard() {
		contentPanel.removeAll();

		if (filteredEntries.isEmpty()) {
			showMessage(allEntries.isEmpty() ? "No players found" : "No matching players");
			return;
		}

		String countText = filteredEntries.size() == allEntries.size()
			? allEntries.size() + " players"
			: filteredEntries.size() + " of " + allEntries.size() + " players";

		JLabel countLabel = new JLabel(countText);
		countLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
		countLabel.setForeground(UIConstants.TEXT_MUTED);
		countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		contentPanel.add(countLabel);
		contentPanel.add(Box.createVerticalStrut(6));

		for (LeaderboardResponse.LeaderboardEntry entry : filteredEntries) {
			contentPanel.add(createPlayerRow(entry));
			contentPanel.add(Box.createVerticalStrut(2));
		}

    contentPanel.revalidate();
		contentPanel.repaint();
	}

	private JPanel createPlayerRow(LeaderboardResponse.LeaderboardEntry entry) {
		JPanel row = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(getBackground());
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2d.dispose();
			}
		};

		row.setOpaque(false);
		row.setBackground(UIConstants.CARD_BG);
		row.setBorder(new EmptyBorder(8, 10, 8, 12));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		row.addMouseListener(new MouseAdapter() {
			public void mouseEntered(MouseEvent e) {
				row.setBackground(UIConstants.CARD_HOVER);
				row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			public void mouseExited(MouseEvent e) {
				row.setBackground(UIConstants.CARD_BG);
				row.setCursor(Cursor.getDefaultCursor());
			}
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					showPlayerProfile(entry.getOsrsAccountId(), entry.getOsrsNickname());
				}
			}
		});

		// Left: Rank + Name
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.X_AXIS));
		leftPanel.setOpaque(false);

		JLabel rankLabel = new JLabel("#" + entry.getRank());
		rankLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
		rankLabel.setForeground(getRankColor(entry.getRank()));
		rankLabel.setPreferredSize(new Dimension(32, 20));

		JLabel nameLabel = new JLabel(entry.getOsrsNickname());
		nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		nameLabel.setForeground(UIConstants.TEXT_PRIMARY);

		leftPanel.add(rankLabel);
		leftPanel.add(Box.createRigidArea(new Dimension(6, 0)));
		leftPanel.add(nameLabel);

		if (entry.getPrestigeLevel() > 0) {
			JLabel prestigeLabel = new JLabel(" ★" + entry.getPrestigeLevel());
			prestigeLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
			prestigeLabel.setForeground(UIConstants.ACCENT_PURPLE);
			leftPanel.add(prestigeLabel);
		}

		// Right: Points + Rank
		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setOpaque(false);

		JLabel pointsLabel = new JLabel(formatPoints(entry.getActivityPoints()) + " pts");
		pointsLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
		pointsLabel.setForeground(UIConstants.ACCENT_GOLD);
		pointsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

		JLabel clanRankLabel = new JLabel(capitalize(entry.getClanRank()));
		clanRankLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
		clanRankLabel.setForeground(UIConstants.TEXT_MUTED);
		clanRankLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

		rightPanel.add(pointsLabel);
		rightPanel.add(clanRankLabel);

		row.add(leftPanel, BorderLayout.WEST);
		row.add(rightPanel, BorderLayout.EAST);

		return row;
	}

	private Color getRankColor(int rank) {
		if (rank == 1) return new Color(255, 215, 0);
		if (rank == 2) return new Color(192, 192, 192);
		if (rank == 3) return new Color(205, 127, 50);
		if (rank <= 10) return UIConstants.ACCENT_BLUE;
		return UIConstants.TEXT_SECONDARY;
	}

	private String formatPoints(int points) {
		if (points >= 1_000_000) return String.format("%.1fM", points / 1_000_000.0);
		if (points >= 1_000) return String.format("%.1fK", points / 1_000.0);
		return String.valueOf(points);
	}

	private String capitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}
}

package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.admin.PendingRankChangesResponse;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import com.revalclan.util.WikiIconLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;

/**
 * Panel displaying list of players needing rankups
 */
public class PendingRankupsPanel extends JPanel {

	// Map rank names to OSRS Wiki clan icon names
	private static final Map<String, String> RANK_ICON_MAP = Map.ofEntries(
		Map.entry("member", "Mentor"),
		Map.entry("advanced", "Prefect"),
		Map.entry("elite", "Leader"),
		Map.entry("veteran", "Supervisor"),
		Map.entry("hero", "Superior"),
		Map.entry("champion", "Executive"),
		Map.entry("legend", "Senator"),
		Map.entry("mythic", "Monarch"),
		Map.entry("paragon", "Brigadier"),
		Map.entry("ascended", "Admiral"),
		Map.entry("eternal", "Marshal"),
		// Also handle lowercase versions
		Map.entry("recruit", "Recruit"),
		Map.entry("corporal", "Corporal"),
		Map.entry("sergeant", "Sergeant"),
		Map.entry("lieutenant", "Lieutenant"),
		Map.entry("captain", "Captain"),
		Map.entry("general", "General")
	);

	private final RevalApiService apiService;
	private final String memberCode;
	private final Runnable onBack;
	private final WikiIconLoader wikiIconLoader;
	private final UIAssetLoader assetLoader;

	private JPanel contentPanel;
	private boolean isLoading = false;

	public PendingRankupsPanel(RevalApiService apiService, String memberCode, Runnable onBack, WikiIconLoader wikiIconLoader, UIAssetLoader assetLoader) {
		this.apiService = apiService;
		this.memberCode = memberCode;
		this.onBack = onBack;
		this.wikiIconLoader = wikiIconLoader;
		this.assetLoader = assetLoader;

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(12, 12, 12, 12));

		buildUI();
		loadData();
	}

	private void buildUI() {
		// Header container with back button above, then title
		JPanel headerContainer = new JPanel();
		headerContainer.setLayout(new BoxLayout(headerContainer, BoxLayout.Y_AXIS));
		headerContainer.setBackground(UIConstants.BACKGROUND);
		headerContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerContainer.setBorder(new EmptyBorder(0, 0, 12, 0));

		// Back button
		BackButton backButton = new BackButton(onBack);

		// Title row
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(UIConstants.BACKGROUND);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		PanelTitle title = new PanelTitle("PENDING RANKUPS");
		titleRow.add(title, BorderLayout.CENTER);

		headerContainer.add(backButton);
		headerContainer.add(Box.createRigidArea(new Dimension(0, 6)));
		headerContainer.add(titleRow);

		// Content panel
		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(UIConstants.BACKGROUND);

		// Wrapper to constrain width to parent
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = super.getPreferredSize();
				if (getParent() != null) {
					size.width = getParent().getWidth();
				}
				return size;
			}
		};
		wrapper.setBackground(UIConstants.BACKGROUND);
		wrapper.add(contentPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(wrapper);
		scrollPane.setBackground(UIConstants.BACKGROUND);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);

		add(headerContainer, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	public void loadData() {
		if (isLoading) return;
		isLoading = true;

		showLoading();

		apiService.fetchPendingRankChanges(memberCode,
			response -> {
				SwingUtilities.invokeLater(() -> {
					isLoading = false;
					if (response.getData() != null && response.getData().getPendingRankChanges() != null) {
						displayRankChanges(response.getData().getPendingRankChanges());
					} else {
						showError("No pending rank changes found");
					}
				});
			},
			error -> {
				SwingUtilities.invokeLater(() -> {
					isLoading = false;
					showError("Failed to load pending rank changes: " + error.getMessage());
				});
			}
		);
	}

	private void displayRankChanges(java.util.List<PendingRankChangesResponse.RankChange> rankChanges) {
		contentPanel.removeAll();

		if (rankChanges.isEmpty()) {
			JLabel emptyLabel = new JLabel("No pending rank changes");
			emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
			emptyLabel.setForeground(UIConstants.TEXT_SECONDARY);
			emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			contentPanel.add(emptyLabel);
		} else {
			for (PendingRankChangesResponse.RankChange change : rankChanges) {
				contentPanel.add(createRankChangeCard(change));
				contentPanel.add(Box.createRigidArea(new Dimension(0, 6)));
			}
		}

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private JPanel createRankChangeCard(PendingRankChangesResponse.RankChange change) {
		JPanel card = new JPanel(new BorderLayout(8, 0));
		card.setBackground(UIConstants.CARD_BG);
		card.setBorder(new EmptyBorder(10, 12, 10, 12));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		// Set tooltip with timestamp
		String tooltip = "<html><b>" + (change.getOsrsNickname() != null ? change.getOsrsNickname() : "Unknown") + 
			"</b><br>Created: " + formatTimestamp(change.getChangedAt()) + "</html>";
		card.setToolTipText(tooltip);

		// Left side - player name on top, rank icons below
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setOpaque(false);

		// Player name
		JLabel nameLabel = new JLabel(change.getOsrsNickname() != null ? change.getOsrsNickname() : "Unknown");
		nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
		nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Rank icons row - with more space
		JPanel rankPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		rankPanel.setOpaque(false);
		rankPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Previous rank icon (16px)
		JLabel prevRankIcon = new JLabel();
		prevRankIcon.setPreferredSize(new Dimension(16, 16));
		prevRankIcon.setMinimumSize(new Dimension(16, 16));
		prevRankIcon.setMaximumSize(new Dimension(16, 16));
		loadRankIcon(prevRankIcon, change.getPreviousRank(), 16);

		// Arrow
		JLabel arrowLabel = new JLabel("→");
		arrowLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
		arrowLabel.setForeground(UIConstants.ACCENT_GOLD);

		// New rank icon (16px)
		JLabel newRankIcon = new JLabel();
		newRankIcon.setPreferredSize(new Dimension(16, 16));
		newRankIcon.setMinimumSize(new Dimension(16, 16));
		newRankIcon.setMaximumSize(new Dimension(16, 16));
		loadRankIcon(newRankIcon, change.getNewRank(), 16);

		rankPanel.add(prevRankIcon);
		rankPanel.add(arrowLabel);
		rankPanel.add(newRankIcon);

		leftPanel.add(nameLabel);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		leftPanel.add(rankPanel);

		// Right side - action button with checkmark icon, vertically centered
		JPanel rightPanel = new JPanel(new GridBagLayout());
		rightPanel.setOpaque(false);

		JButton actualizeButton = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(getBackground());
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2d.dispose();
				super.paintComponent(g);
			}
		};
		
		// Load checkmark icon
		ImageIcon checkIcon = assetLoader != null ? assetLoader.getIcon("checkmark.png", 14) : null;
		if (checkIcon != null) {
			actualizeButton.setIcon(checkIcon);
		} else {
			actualizeButton.setText("✓");
			actualizeButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
		}
		
		actualizeButton.setForeground(Color.WHITE);
		actualizeButton.setBackground(new Color(70, 70, 70)); // Light gray background for green checkmark
		actualizeButton.setFocusPainted(false);
		actualizeButton.setBorderPainted(false);
		actualizeButton.setContentAreaFilled(false);
		actualizeButton.setOpaque(false);
		actualizeButton.setPreferredSize(new Dimension(28, 28));
		actualizeButton.setMinimumSize(new Dimension(28, 28));
		actualizeButton.setMaximumSize(new Dimension(28, 28));
		actualizeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		actualizeButton.setToolTipText("Actualize rank change");
		actualizeButton.addActionListener(e -> actualizeRankChange(change.getId(), actualizeButton));

		rightPanel.add(actualizeButton);

		card.add(leftPanel, BorderLayout.CENTER);
		card.add(rightPanel, BorderLayout.EAST);

		return wrapInRoundedPanel(card);
	}

	private void loadRankIcon(JLabel iconLabel, String rankName, int size) {
		if (rankName == null || rankName.isEmpty() || wikiIconLoader == null) {
			iconLabel.setText("?");
			iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
			iconLabel.setForeground(UIConstants.TEXT_SECONDARY);
			iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
			return;
		}

		String iconName = RANK_ICON_MAP.get(rankName.toLowerCase());
		if (iconName != null) {
			wikiIconLoader.loadClanIcon(iconName, size, iconLabel);
		} else {
			// Fallback - show first letter
			iconLabel.setText(rankName.substring(0, 1).toUpperCase());
			iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
			iconLabel.setForeground(UIConstants.ACCENT_GOLD);
			iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		}
	}

	private String formatTimestamp(String timestamp) {
		if (timestamp == null || timestamp.isEmpty()) {
			return "";
		}
		try {
			// Parse ISO 8601 date
			java.time.ZonedDateTime dateTime = java.time.ZonedDateTime.parse(timestamp);
			java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
			return dateTime.format(formatter);
		} catch (Exception e) {
			// Fallback - return as is or simplified
			if (timestamp.contains("T")) {
				return timestamp.substring(0, Math.min(16, timestamp.length())).replace("T", " ");
			}
			return timestamp;
		}
	}

	private JPanel wrapInRoundedPanel(JPanel inner) {
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(inner.getBackground());
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2d.dispose();
			}
		};
		wrapper.setOpaque(false);
		wrapper.add(inner, BorderLayout.CENTER);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.setMaximumSize(inner.getMaximumSize());
		return wrapper;
	}

	private void actualizeRankChange(int rankChangeId, JButton button) {
		button.setEnabled(false);
		button.setText("...");

		apiService.actualizeRankChange(memberCode, rankChangeId,
			response -> {
				SwingUtilities.invokeLater(() -> {
					if (response.isSuccess()) {
						// Reload data
						loadData();
					} else {
						button.setEnabled(true);
						button.setText("✓");
						JOptionPane.showMessageDialog(this,
							"Failed to actualize rank change: " + response.getMessage(),
							"Error",
							JOptionPane.ERROR_MESSAGE);
					}
				});
			},
			error -> {
				SwingUtilities.invokeLater(() -> {
					button.setEnabled(true);
					button.setText("✓");
					JOptionPane.showMessageDialog(this,
						"Error: " + error.getMessage(),
						"Error",
						JOptionPane.ERROR_MESSAGE);
				});
			}
		);
	}

	private void showLoading() {
		contentPanel.removeAll();
		JLabel loading = new JLabel("Loading...");
		loading.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		loading.setForeground(UIConstants.TEXT_SECONDARY);
		loading.setAlignmentX(Component.CENTER_ALIGNMENT);
		contentPanel.add(loading);
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void showError(String message) {
		contentPanel.removeAll();
		JLabel error = new JLabel("<html><center>" + message + "</center></html>");
		error.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		error.setForeground(UIConstants.ERROR_COLOR);
		error.setAlignmentX(Component.CENTER_ALIGNMENT);
		contentPanel.add(error);
		contentPanel.revalidate();
		contentPanel.repaint();
	}
}

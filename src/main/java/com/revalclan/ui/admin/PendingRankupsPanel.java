package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.admin.PendingRankChangesResponse;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class PendingRankupsPanel extends JPanel {
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

	// Map rank names to OSRS Wiki clan icon names
	private static final Map<String, String> RANK_DISPLAY = Map.ofEntries(
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
	private final UIAssetLoader assetLoader;

	private JPanel contentPanel;
	private boolean isLoading = false;

	public PendingRankupsPanel(RevalApiService apiService, String memberCode,
	                           Runnable onBack, UIAssetLoader assetLoader) {
		this.apiService = apiService;
		this.memberCode = memberCode;
		this.onBack = onBack;
		this.assetLoader = assetLoader;

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(12, 12, 12, 12));

		buildUI();
		loadData();
	}

	private void buildUI() {
		// Header
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(UIConstants.BACKGROUND);
		header.setBorder(new EmptyBorder(0, 0, 12, 0));

		BackButton backButton = new BackButton("< Back", onBack);
		backButton.setAlignmentX(Component.LEFT_ALIGNMENT);

		PanelTitle title = new PanelTitle("PENDING RANKUPS");
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		header.add(backButton);
		header.add(Box.createRigidArea(new Dimension(0, 6)));
		header.add(title);

		// Content
		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(UIConstants.BACKGROUND);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(UIConstants.BACKGROUND);
		wrapper.add(contentPanel, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(wrapper);
		scroll.setBackground(UIConstants.BACKGROUND);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getViewport().setBackground(UIConstants.BACKGROUND);

		add(header, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
	}

	public void loadData() {
		if (isLoading) return;
		isLoading = true;
		showMessage("Loading...", UIConstants.TEXT_SECONDARY);

		apiService.fetchPendingRankChanges(memberCode,
			response -> SwingUtilities.invokeLater(() -> {
				isLoading = false;
				if (response.getData() != null && response.getData().getPendingRankChanges() != null) {
					displayRankChanges(response.getData().getPendingRankChanges());
				} else {
					showMessage("No pending rank changes found", UIConstants.TEXT_SECONDARY);
				}
			}),
			error -> SwingUtilities.invokeLater(() -> {
				isLoading = false;
				showMessage("Failed to load: " + error.getMessage(), UIConstants.ERROR_COLOR);
			})
		);
	}

	private void displayRankChanges(List<PendingRankChangesResponse.RankChange> changes) {
		contentPanel.removeAll();
		if (changes.isEmpty()) {
			showMessage("No pending rank changes", UIConstants.TEXT_SECONDARY);
			return;
		}
		for (PendingRankChangesResponse.RankChange change : changes) {
			contentPanel.add(createCard(change));
			contentPanel.add(Box.createRigidArea(new Dimension(0, 6)));
		}
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private JPanel createCard(PendingRankChangesResponse.RankChange change) {
		JPanel card = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(UIConstants.CARD_BG);
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2d.dispose();
			}
		};
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(10, 12, 10, 12));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		String name = change.getOsrsNickname() != null ? change.getOsrsNickname() : "Unknown";
		card.setToolTipText(name + " • " + formatTimestamp(change.getChangedAt()));

		// Left: name + rank transition
		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);

		JLabel nameLabel = new JLabel(name);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		String prevRank = rankDisplay(change.getPreviousRank());
		String newRank = rankDisplay(change.getNewRank());
		JLabel rankLabel = new JLabel(prevRank + " → " + newRank);
		rankLabel.setFont(FontManager.getRunescapeSmallFont());
		rankLabel.setForeground(UIConstants.ACCENT_GOLD);
		rankLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		left.add(nameLabel);
		left.add(Box.createRigidArea(new Dimension(0, 2)));
		left.add(rankLabel);

		// Right: actualize button
		JPanel right = new JPanel(new GridBagLayout());
		right.setOpaque(false);

		JButton actualizeBtn = new JButton() {
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

		ImageIcon checkIcon = assetLoader != null ? assetLoader.getIcon("checkmark.png", 14) : null;
		if (checkIcon != null) {
			actualizeBtn.setIcon(checkIcon);
		} else {
			actualizeBtn.setText("✓");
			actualizeBtn.setFont(FontManager.getRunescapeSmallFont());
		}

		actualizeBtn.setForeground(Color.WHITE);
		actualizeBtn.setBackground(new Color(70, 70, 70));
		actualizeBtn.setFocusPainted(false);
		actualizeBtn.setBorderPainted(false);
		actualizeBtn.setContentAreaFilled(false);
		actualizeBtn.setOpaque(false);
		actualizeBtn.setPreferredSize(new Dimension(28, 28));
		actualizeBtn.setMinimumSize(new Dimension(28, 28));
		actualizeBtn.setMaximumSize(new Dimension(28, 28));
		actualizeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		actualizeBtn.setToolTipText("Actualize rank change");
		actualizeBtn.addActionListener(e -> actualizeRankChange(change.getId(), actualizeBtn));

		right.add(actualizeBtn);

		card.add(left, BorderLayout.CENTER);
		card.add(right, BorderLayout.EAST);
		return card;
	}

	private void actualizeRankChange(int id, JButton button) {
		button.setEnabled(false);

		apiService.actualizeRankChange(memberCode, id,
			response -> SwingUtilities.invokeLater(() -> {
				if (response.isSuccess()) {
					loadData();
				} else {
					button.setEnabled(true);
					JOptionPane.showMessageDialog(this,
						"Failed: " + response.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}),
			error -> SwingUtilities.invokeLater(() -> {
				button.setEnabled(true);
				JOptionPane.showMessageDialog(this,
					"Error: " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			})
		);
	}

	private void showMessage(String text, Color color) {
		contentPanel.removeAll();
		JLabel lbl = new JLabel(text);
		lbl.setFont(FontManager.getRunescapeSmallFont());
		lbl.setForeground(color);
		lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
		contentPanel.add(lbl);
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private String rankDisplay(String rank) {
		if (rank == null) return "?";
		String display = RANK_DISPLAY.get(rank.toLowerCase());
		return display != null ? display : rank;
	}

	private String formatTimestamp(String timestamp) {
		if (timestamp == null || timestamp.isEmpty()) return "";
		try {
			return ZonedDateTime.parse(timestamp).format(DATE_FMT);
		} catch (Exception e) {
			return timestamp.length() > 16 ? timestamp.substring(0, 16).replace("T", " ") : timestamp;
		}
	}
}

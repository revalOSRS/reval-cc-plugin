package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.admin.PendingRankChangesResponse;
import com.revalclan.ui.components.ArrowIcon;
import com.revalclan.ui.components.CheckIcon;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.Labels;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.components.RoundedPanel;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.RankNames;
import com.revalclan.util.UIAssetLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PendingRankupsPanel extends JPanel {
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

	private final RevalApiService apiService;
	private final AdminManager.Session session;
	private final Runnable onBack;
	private final UIAssetLoader assetLoader;
	private final ClanRankIconResolver rankIconResolver;

	private JPanel contentPanel;
	private boolean isLoading = false;

	public PendingRankupsPanel(RevalApiService apiService, AdminManager.Session session,
	                           Runnable onBack, UIAssetLoader assetLoader,
	                           ClanRankIconResolver rankIconResolver) {
		this.apiService = apiService;
		this.session = session;
		this.onBack = onBack;
		this.assetLoader = assetLoader;
		this.rankIconResolver = rankIconResolver;

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
		if (isLoading || !session.isCurrent()) return;
		isLoading = true;
		showMessage("Loading...", UIConstants.TEXT_SECONDARY);

		apiService.fetchPendingRankChanges(
			response -> SwingUtilities.invokeLater(() -> {
				if (!session.isCurrent()) return;
				isLoading = false;
				if (response.getData() != null && response.getData().getPendingRankChanges() != null) {
					displayRankChanges(response.getData().getPendingRankChanges());
				} else {
					showMessage("No pending rank changes found", UIConstants.TEXT_SECONDARY);
				}
			}),
			error -> SwingUtilities.invokeLater(() -> {
				if (!session.isCurrent()) return;
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
				RoundedPanel.fill(g, this, UIConstants.CARD_BG, 6);
			}
		};
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(10, 12, 10, 12));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		String name = change.getOsrsNickname() != null ? change.getOsrsNickname() : "Unknown";
		card.setToolTipText(name + " - " + formatTimestamp(change.getChangedAt()));

		// Left: name + rank transition
		JPanel left = Labels.column();

		JPanel rankLine = Labels.flow();
		rankLine.add(rankIcon(change.getPreviousRank()));
		rankLine.add(new JLabel(new ArrowIcon(12, UIConstants.ACCENT_GOLD)));
		rankLine.add(rankIcon(change.getNewRank()));
		rankLine.setToolTipText(RankNames.display(change.getPreviousRank()) + " -> " + RankNames.display(change.getNewRank()));
		rankLine.setAlignmentX(Component.LEFT_ALIGNMENT);

		left.add(Labels.small(name, UIConstants.TEXT_PRIMARY));
		left.add(Box.createRigidArea(new Dimension(0, 2)));
		left.add(rankLine);

		// Right: actualize button
		JPanel right = new JPanel(new GridBagLayout());
		right.setOpaque(false);

		JButton actualizeBtn = new JButton() {
			@Override
			protected void paintComponent(Graphics g) {
				RoundedPanel.fill(g, this, getBackground(), 6);
				super.paintComponent(g);
			}
		};

		ImageIcon checkIcon = assetLoader != null ? assetLoader.getIcon("checkmark.png", 14) : null;
		actualizeBtn.setIcon(checkIcon != null ? checkIcon : new CheckIcon(14, Color.WHITE));

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

	private JLabel rankIcon(String rank) {
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(18, 18));
		rankIconResolver.apply(rank, icon, 18);
		return icon;
	}

	private void actualizeRankChange(int id, JButton button) {
		if (!session.isCurrent()) return;
		button.setEnabled(false);

		apiService.actualizeRankChange(id,
			response -> SwingUtilities.invokeLater(() -> {
				if (!session.isCurrent()) return;
				if (response.isSuccess()) {
					loadData();
				} else {
					button.setEnabled(true);
					JOptionPane.showMessageDialog(this,
						"Failed: " + response.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}),
			error -> SwingUtilities.invokeLater(() -> {
				if (!session.isCurrent()) return;
				button.setEnabled(true);
				JOptionPane.showMessageDialog(this,
					"Error: " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			})
		);
	}

	private void showMessage(String text, Color color) {
		contentPanel.removeAll();
		JLabel lbl = Labels.small(text, color);
		lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
		contentPanel.add(lbl);
		contentPanel.revalidate();
		contentPanel.repaint();
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

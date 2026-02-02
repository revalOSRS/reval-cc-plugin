package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Admin dashboard panel - shows stats and navigation buttons
 */
public class AdminDashboardPanel extends JPanel {

	private final RevalApiService apiService;
	private final String memberCode;
	private final Consumer<String> onNavigate;

	private JPanel statsPanel;
	private JPanel buttonsPanel;
	private int pendingRankupsCount = 0;
	
	/**
	 * Refresh dashboard stats
	 */
	public void refreshStats() {
		loadStats();
	}

	public AdminDashboardPanel(RevalApiService apiService, String memberCode,
	                           AdminAuthResponse.AdminAuthData authData,
	                           Consumer<String> onNavigate) {
		this.apiService = apiService;
		this.memberCode = memberCode;
		this.onNavigate = onNavigate;

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(12, 0, 12, 12)); // Remove left padding

		buildUI();
		loadStats();
	}

	private void buildUI() {
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(UIConstants.BACKGROUND);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.setBorder(new EmptyBorder(0, 0, 0, 0)); // Remove any default padding

		// Header
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.BACKGROUND);
		header.setBorder(new EmptyBorder(4, 8, 12, 0)); // Add left padding like Tasks panel
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

		JLabel title = new JLabel("ADMIN DASHBOARD");
		title.setFont(new Font("Segoe UI", Font.BOLD, 18));
		title.setForeground(UIConstants.ACCENT_GOLD);

		header.add(title, BorderLayout.WEST);

		// Stats panel - wrap to prevent overflow
		statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
		statsPanel.setBackground(UIConstants.BACKGROUND);
		statsPanel.setBorder(new EmptyBorder(0, 8, 16, 0)); // Add left padding like Tasks panel
		statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

		// Buttons panel
		buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));
		buttonsPanel.setBackground(UIConstants.BACKGROUND);
		buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttonsPanel.setBorder(new EmptyBorder(0, 8, 0, 0)); // Add left padding like Tasks panel

		// Add section buttons
		addSectionButton("Pending Rankups", null, "View players needing rank changes", () -> onNavigate.accept("PENDING_RANKUPS"));
		// TODO: Add more admin sections here in the future
		
		content.add(header);
		content.add(statsPanel);
		content.add(Box.createRigidArea(new Dimension(0, 8)));
		content.add(buttonsPanel);

		JScrollPane scrollPane = new JScrollPane(content);
		scrollPane.setBackground(UIConstants.BACKGROUND);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);
		scrollPane.getViewport().setPreferredSize(new Dimension(0, 0));

		add(scrollPane, BorderLayout.CENTER);
	}

	private void loadStats() {
		// Load pending rankups count
		apiService.fetchPendingRankChanges(memberCode,
			response -> {
				SwingUtilities.invokeLater(() -> {
					if (response.getData() != null) {
						pendingRankupsCount = response.getData().getTotal();
						updateStats();
					}
				});
			},
			error -> {
				SwingUtilities.invokeLater(this::updateStats);
			}
		);
	}

	private void updateStats() {
		statsPanel.removeAll();

		// Pending rankups stat card
		JPanel rankupsCard = createStatCard("Pending Rankups", String.valueOf(pendingRankupsCount), UIConstants.ACCENT_BLUE);
		statsPanel.add(rankupsCard);

		// TODO: Add more stats here in the future

		statsPanel.revalidate();
		statsPanel.repaint();
	}

	private JPanel createStatCard(String label, String value, Color accentColor) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(UIConstants.CARD_BG);
		card.setBorder(new EmptyBorder(12, 16, 12, 16));
		card.setPreferredSize(new Dimension(160, 70));
		card.setMaximumSize(new Dimension(160, 70));

		JLabel labelLabel = new JLabel(label);
		labelLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		labelLabel.setForeground(UIConstants.TEXT_SECONDARY);

		JLabel valueLabel = new JLabel(value);
		valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
		valueLabel.setForeground(accentColor);

		card.add(labelLabel, BorderLayout.NORTH);
		card.add(valueLabel, BorderLayout.CENTER);

		return card;
	}

	private void addSectionButton(String title, String icon, String description, Runnable onClick) {
		JPanel button = new JPanel(new BorderLayout(12, 0));
		button.setBackground(UIConstants.CARD_BG);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(60, 63, 65), 1),
			new EmptyBorder(16, 16, 16, 16)
		));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		// Full width of sidebar
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Left side: Text only (no icon)
		JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.setBackground(UIConstants.CARD_BG);
		leftPanel.setOpaque(false);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(UIConstants.CARD_BG);
		textPanel.setOpaque(false);

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
		titleLabel.setForeground(UIConstants.TEXT_PRIMARY);
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel descLabel = new JLabel(description);
		descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
		descLabel.setForeground(UIConstants.TEXT_SECONDARY);
		descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		textPanel.add(titleLabel);
		textPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		textPanel.add(descLabel);

		leftPanel.add(textPanel, BorderLayout.CENTER);
		
		// Only add icon if provided
		if (icon != null) {
			JLabel iconLabel = new JLabel(icon);
			iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
			iconLabel.setVerticalAlignment(SwingConstants.CENTER);
			leftPanel.add(iconLabel, BorderLayout.WEST);
		}

		JLabel arrowLabel = new JLabel("→");
		arrowLabel.setFont(new Font("Segoe UI", Font.PLAIN, 20));
		arrowLabel.setForeground(UIConstants.TEXT_SECONDARY);
		arrowLabel.setVerticalAlignment(SwingConstants.CENTER);

		button.add(leftPanel, BorderLayout.CENTER);
		button.add(arrowLabel, BorderLayout.EAST);

		button.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				onClick.run();
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e) {
				button.setBackground(new Color(35, 38, 40));
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e) {
				button.setBackground(UIConstants.CARD_BG);
			}
		});

		buttonsPanel.add(button);
		buttonsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
	}
}

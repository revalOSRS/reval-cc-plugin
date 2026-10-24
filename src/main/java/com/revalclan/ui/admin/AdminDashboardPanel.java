package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.components.ArrowIcon;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.components.Labels;
import com.revalclan.ui.components.RoundedPanel;
import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

public class AdminDashboardPanel extends JPanel {
	private final RevalApiService apiService;
	private final String memberCode;
	private final Consumer<String> onNavigate;

	private JPanel statsPanel;
	private int pendingRankupsCount = 0;

	public AdminDashboardPanel(RevalApiService apiService, String memberCode,
	                           AdminAuthResponse.AdminAuthData authData,
	                           Consumer<String> onNavigate) {
		this.apiService = apiService;
		this.memberCode = memberCode;
		this.onNavigate = onNavigate;

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(12, 12, 12, 12));

		buildUI();
		loadStats();
	}

	public void refreshStats() {
		loadStats();
	}

	private void buildUI() {
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(UIConstants.BACKGROUND);

		// Header
		PanelTitle title = new PanelTitle("ADMIN DASHBOARD");
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Stats
		statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		statsPanel.setBackground(UIConstants.BACKGROUND);
		statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

		// Buttons
		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));
		buttonsPanel.setBackground(UIConstants.BACKGROUND);
		buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		addSectionButton(buttonsPanel, "Pending Rankups", "View players needing rank changes",
			() -> onNavigate.accept("PENDING_RANKUPS"));

		content.add(title);
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		content.add(statsPanel);
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		content.add(buttonsPanel);

		JScrollPane scroll = new JScrollPane(content);
		scroll.setBackground(UIConstants.BACKGROUND);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getViewport().setBackground(UIConstants.BACKGROUND);

		add(scroll, BorderLayout.CENTER);
	}

	private void loadStats() {
		apiService.fetchPendingRankChanges(memberCode,
			response -> SwingUtilities.invokeLater(() -> {
				if (response.getData() != null) {
					pendingRankupsCount = response.getData().getTotal();
				}
				updateStats();
			}),
			error -> SwingUtilities.invokeLater(this::updateStats)
		);
	}

	private void updateStats() {
		statsPanel.removeAll();
		statsPanel.add(createStatCard("Pending Rankups", String.valueOf(pendingRankupsCount), UIConstants.ACCENT_BLUE));
		statsPanel.revalidate();
		statsPanel.repaint();
	}

	private JPanel createStatCard(String label, String value, Color accentColor) {
		JPanel card = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				RoundedPanel.fill(g, this, UIConstants.CARD_BG, 6);
			}
		};
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(10, 14, 10, 14));
		card.setPreferredSize(new Dimension(140, 60));

		card.add(Labels.small(label, UIConstants.TEXT_SECONDARY), BorderLayout.NORTH);
		card.add(Labels.bold(value, accentColor), BorderLayout.CENTER);
		return card;
	}

	private void addSectionButton(JPanel parent, String title, String description, Runnable onClick) {
		JPanel btn = new RoundedPanel(new BorderLayout(8, 0), 6);
		btn.setBackground(UIConstants.CARD_BG);
		btn.setBorder(new EmptyBorder(14, 14, 14, 14));
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
		btn.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel textPanel = Labels.column();
		textPanel.add(Labels.small(title, UIConstants.TEXT_PRIMARY));
		textPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		textPanel.add(Labels.small(description, UIConstants.TEXT_MUTED));

		JLabel arrow = new JLabel(new ArrowIcon(12, UIConstants.TEXT_SECONDARY));
		arrow.setVerticalAlignment(SwingConstants.CENTER);

		btn.add(textPanel, BorderLayout.CENTER);
		btn.add(arrow, BorderLayout.EAST);

		// onPress also sets the hand cursor
		Clickable.onPress(btn, onClick, UIConstants.CARD_HOVER, UIConstants.CARD_BG);

		parent.add(btn);
		parent.add(Box.createRigidArea(new Dimension(0, 6)));
	}
}

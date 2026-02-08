package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(UIConstants.CARD_BG);
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2d.dispose();
			}
		};
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(10, 14, 10, 14));
		card.setPreferredSize(new Dimension(140, 60));

		JLabel labelLbl = new JLabel(label);
		labelLbl.setFont(FontManager.getRunescapeSmallFont());
		labelLbl.setForeground(UIConstants.TEXT_SECONDARY);

		JLabel valueLbl = new JLabel(value);
		valueLbl.setFont(FontManager.getRunescapeBoldFont());
		valueLbl.setForeground(accentColor);

		card.add(labelLbl, BorderLayout.NORTH);
		card.add(valueLbl, BorderLayout.CENTER);
		return card;
	}

	private void addSectionButton(JPanel parent, String title, String description, Runnable onClick) {
		JPanel btn = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(getBackground());
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2d.dispose();
			}
		};
		btn.setOpaque(false);
		btn.setBackground(UIConstants.CARD_BG);
		btn.setBorder(new EmptyBorder(14, 14, 14, 14));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
		btn.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setOpaque(false);

		JLabel titleLbl = new JLabel(title);
		titleLbl.setFont(FontManager.getRunescapeSmallFont());
		titleLbl.setForeground(UIConstants.TEXT_PRIMARY);
		titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel descLbl = new JLabel(description);
		descLbl.setFont(FontManager.getRunescapeSmallFont());
		descLbl.setForeground(UIConstants.TEXT_MUTED);
		descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

		textPanel.add(titleLbl);
		textPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		textPanel.add(descLbl);

		JLabel arrow = new JLabel("→");
		arrow.setFont(FontManager.getRunescapeSmallFont());
		arrow.setForeground(UIConstants.TEXT_SECONDARY);
		arrow.setVerticalAlignment(SwingConstants.CENTER);

		btn.add(textPanel, BorderLayout.CENTER);
		btn.add(arrow, BorderLayout.EAST);

		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) { onClick.run(); }
			@Override
			public void mouseEntered(MouseEvent e) { btn.setBackground(UIConstants.CARD_HOVER); btn.repaint(); }
			@Override
			public void mouseExited(MouseEvent e) { btn.setBackground(UIConstants.CARD_BG); btn.repaint(); }
		});

		parent.add(btn);
		parent.add(Box.createRigidArea(new Dimension(0, 6)));
	}
}

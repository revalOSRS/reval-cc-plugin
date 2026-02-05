package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.achievements.AchievementsResponse;
import com.revalclan.ui.components.Badge;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanValidator;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

public class AchievementsPanel extends JPanel {
	private RevalApiService apiService;
	private Client client;
	private final JPanel contentPanel;
	private RefreshButton refreshBtn;
	private List<AchievementsResponse.Achievement> achievements = new ArrayList<>();

	public AchievementsPanel() {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(UIConstants.BACKGROUND);
		contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = super.getPreferredSize();
				if (getParent() != null) size.width = getParent().getWidth();
				return size;
			}
		};
		wrapper.setBackground(UIConstants.BACKGROUND);
		wrapper.add(contentPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(wrapper);
		scrollPane.setBackground(UIConstants.BACKGROUND);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);

		add(scrollPane, BorderLayout.CENTER);
		showNotLoggedIn();
	}

	public void init(RevalApiService apiService, Client client) {
		this.apiService = apiService;
		this.client = client;
		if (!isAuthorized()) showNotLoggedIn();
	}

	public void onLoggedIn() {
		if (isAuthorized()) {
			loadData();
		} else {
			scheduleRetry(1);
		}
	}

	public void onLoggedOut() {
		SwingUtilities.invokeLater(this::showNotLoggedIn);
	}

	public void refresh() {
		loadData();
	}

	private void scheduleRetry(int attempt) {
		if (attempt > 10) {
			showNotLoggedIn();
			return;
		}
		Timer timer = new Timer(attempt * 1000, e -> {
			if (ClanValidator.validateClan(client)) loadData();
			else scheduleRetry(attempt + 1);
		});
		timer.setRepeats(false);
		timer.start();
	}

	private boolean isAuthorized() {
		return client != null && 
			client.getGameState() == GameState.LOGGED_IN && 
			client.getAccountHash() != -1 && 
			ClanValidator.validateClan(client);
	}

	private void loadData() {
		if (!isAuthorized() || apiService == null) {
			showNotLoggedIn();
			return;
		}

		if (refreshBtn != null) refreshBtn.setLoading(true);

		apiService.fetchAchievementDefinitions(client.getAccountHash(),
			response -> SwingUtilities.invokeLater(() -> {
				if (refreshBtn != null) refreshBtn.setLoading(false);
				achievements = (response != null && response.isSuccess() && response.getData() != null)
					? response.getData().getAchievements() : new ArrayList<>();
				if (achievements == null) achievements = new ArrayList<>();
				buildUI();
			}),
			error -> SwingUtilities.invokeLater(() -> {
				if (refreshBtn != null) refreshBtn.setLoading(false);
				buildUI();
			})
		);
	}

	private void showNotLoggedIn() {
		contentPanel.removeAll();
		contentPanel.add(new LoginPrompt("Achievements"));
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void buildUI() {
		contentPanel.removeAll();

		if (!isAuthorized()) {
			showNotLoggedIn();
			return;
		}

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.BACKGROUND);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel("ACHIEVEMENTS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(UIConstants.ACCENT_GOLD);

		refreshBtn = new RefreshButton(this::refresh);
		header.add(title, BorderLayout.WEST);
		header.add(refreshBtn, BorderLayout.EAST);

		contentPanel.add(header);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));

		if (achievements.isEmpty()) {
			JLabel empty = new JLabel("No achievements available");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(UIConstants.TEXT_SECONDARY);
			empty.setAlignmentX(Component.CENTER_ALIGNMENT);
			contentPanel.add(empty);
		} else {
			JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
			grid.setOpaque(false);
			grid.setAlignmentX(Component.LEFT_ALIGNMENT);
			for (AchievementsResponse.Achievement achievement : achievements) {
				grid.add(createCard(achievement));
			}
			contentPanel.add(grid);
		}

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private JPanel createCard(AchievementsResponse.Achievement achievement) {
		boolean completed = achievement.getProgress() != null && achievement.getProgress().isCompleted();
		Color rarityColor = getRarityColor(achievement.getRarity());

		JPanel card = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(completed ? UIConstants.CARD_BG : new Color(35, 35, 40));
				g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				if (completed) {
					g2d.setColor(rarityColor);
					g2d.setStroke(new BasicStroke(2f));
					g2d.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
				}
				g2d.dispose();
			}
		};

		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(8, 8, 8, 8));
		card.setPreferredSize(new Dimension(0, 100));

		Badge badge = completed ? Badge.rarity(achievement.getRarity()) : new Badge(capitalize(achievement.getRarity()), UIConstants.TEXT_MUTED);
		badge.setAlignmentX(Component.LEFT_ALIGNMENT);

		JTextArea name = new JTextArea(achievement.getName() != null ? achievement.getName() : "Unknown");
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(completed ? rarityColor : UIConstants.TEXT_SECONDARY);
		name.setEditable(false);
		name.setFocusable(false);
		name.setLineWrap(true);
		name.setWrapStyleWord(true);
		name.setOpaque(false);
		name.setBorder(null);
		name.setMargin(new Insets(0, 0, 0, 0));
		name.setAlignmentX(Component.LEFT_ALIGNMENT);

		JTextArea desc = new JTextArea(achievement.getDescription() != null ? achievement.getDescription() : "");
		desc.setFont(FontManager.getRunescapeSmallFont());
		desc.setForeground(UIConstants.TEXT_MUTED);
		desc.setEditable(false);
		desc.setFocusable(false);
		desc.setLineWrap(true);
		desc.setWrapStyleWord(true);
		desc.setOpaque(false);
		desc.setBorder(null);
		desc.setMargin(new Insets(0, 0, 0, 0));
		desc.setAlignmentX(Component.LEFT_ALIGNMENT);

		card.add(badge);
		card.add(Box.createRigidArea(new Dimension(0, 3)));
		card.add(name);
		card.add(Box.createRigidArea(new Dimension(0, 3)));
		card.add(desc);

		return card;
	}

	private Color getRarityColor(String rarity) {
		if (rarity == null) return UIConstants.RARITY_COMMON;
		switch (rarity.toLowerCase()) {
			case "uncommon": return UIConstants.RARITY_UNCOMMON;
			case "rare": return UIConstants.RARITY_RARE;
			case "epic": return UIConstants.RARITY_EPIC;
			case "legendary": return UIConstants.RARITY_LEGENDARY;
			case "mythic": return UIConstants.RARITY_MYTHIC;
			default: return UIConstants.RARITY_COMMON;
		}
	}

	private String capitalize(String s) {
		if (s == null || s.isEmpty()) return "Unknown";
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}
}

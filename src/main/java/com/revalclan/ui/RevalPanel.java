package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.ui.components.GradientSeparator;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RevalPanel extends PluginPanel {
	private static final String DISCORD_URL = "https://discord.gg/reval";
	private static final String WEBSITE_URL = "https://revalosrs.ee";

	private final CardLayout cardLayout;
	private final JPanel contentPanel;

	@Getter private final ProfilePanel profilePanel;
	@Getter private final RankingPanel rankingPanel;
	@Getter private final LeaderboardPanel leaderboardPanel;
	@Getter private final AchievementsPanel achievementsPanel;

	private JLabel infoButton;
	private JLabel discordIcon;
	private JLabel websiteIcon;

	private JButton profileTab;
	private JButton leaderboardTab;
	private JButton achievementsTab;
	private String selectedTab = "PROFILE";

	public RevalPanel() {
		super(false);

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		profilePanel = new ProfilePanel();
		rankingPanel = new RankingPanel();
		leaderboardPanel = new LeaderboardPanel();
		achievementsPanel = new AchievementsPanel();

		cardLayout = new CardLayout();
		contentPanel = new JPanel(cardLayout);
		contentPanel.setBackground(UIConstants.BACKGROUND);

		contentPanel.add(profilePanel, "PROFILE");
		contentPanel.add(rankingPanel, "RANKING");
		contentPanel.add(leaderboardPanel, "LEADERBOARD");
		contentPanel.add(achievementsPanel, "ACHIEVEMENTS");

		JPanel header = createHeader();
		JPanel navBar = createNavBar();

		JPanel topSection = new JPanel(new BorderLayout());
		topSection.setBackground(UIConstants.BACKGROUND);
		topSection.add(header, BorderLayout.NORTH);
		topSection.add(navBar, BorderLayout.CENTER);

		add(topSection, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);

		selectTab("PROFILE");
	}

	private JPanel createNavBar() {
		JPanel navBar = new JPanel();
		navBar.setLayout(new BoxLayout(navBar, BoxLayout.Y_AXIS));
		navBar.setBackground(UIConstants.BACKGROUND);
		navBar.setBorder(new EmptyBorder(0, 6, 6, 6));

		// First row: Profile, Achievements
		JPanel row1 = new JPanel(new GridLayout(1, 2, 4, 0));
		row1.setOpaque(false);
		row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		profileTab = createTabButton("Profile");
		profileTab.addActionListener(e -> selectTab("PROFILE"));

		achievementsTab = createTabButton("Achievements");
		achievementsTab.addActionListener(e -> selectTab("ACHIEVEMENTS"));

		row1.add(profileTab);
		row1.add(achievementsTab);

		// Second row: Leaderboard
		JPanel row2 = new JPanel(new GridLayout(1, 1, 0, 0));
		row2.setOpaque(false);
		row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		leaderboardTab = createTabButton("Leaderboard");
		leaderboardTab.addActionListener(e -> selectTab("LEADERBOARD"));

		row2.add(leaderboardTab);

		navBar.add(row1);
		navBar.add(Box.createRigidArea(new Dimension(0, 4)));
		navBar.add(row2);

		return navBar;
	}

	private JButton createTabButton(String text) {
		JButton btn = new JButton(text);
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setPreferredSize(new Dimension(0, 28));
		return btn;
	}

	private JPanel createHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.BACKGROUND);
		header.setBorder(new EmptyBorder(6, 12, 8, 12));

		infoButton = new JLabel();
		infoButton.setToolTipText("Ranks & Points");
		infoButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		infoButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					selectTab("RANKING");
				}
			}
		});

		JPanel infoButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		infoButtonPanel.setBackground(UIConstants.BACKGROUND);
		infoButtonPanel.setOpaque(false);
		infoButtonPanel.setPreferredSize(new Dimension(30, 22));
		infoButtonPanel.add(infoButton);

		JPanel socialPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
		socialPanel.setBackground(UIConstants.BACKGROUND);
		socialPanel.setOpaque(false);
		socialPanel.setBorder(new EmptyBorder(0, 0, 6, 0));

		discordIcon = createSocialIcon(DISCORD_URL, "Join our Discord");
		websiteIcon = createSocialIcon(WEBSITE_URL, "Visit our website");

		socialPanel.add(discordIcon);
		socialPanel.add(websiteIcon);

		JPanel topRow = new JPanel(new BorderLayout());
		topRow.setBackground(UIConstants.BACKGROUND);
		topRow.setOpaque(false);

		JPanel centerWrapper = new JPanel();
		centerWrapper.setLayout(new BoxLayout(centerWrapper, BoxLayout.X_AXIS));
		centerWrapper.setOpaque(false);
		centerWrapper.add(Box.createHorizontalGlue());
		centerWrapper.add(socialPanel);
		centerWrapper.add(Box.createHorizontalGlue());

		topRow.add(infoButtonPanel, BorderLayout.WEST);
		topRow.add(centerWrapper, BorderLayout.CENTER);

		JPanel rightPanel = new JPanel();
		rightPanel.setOpaque(false);
		rightPanel.setPreferredSize(new Dimension(30, 22));
		topRow.add(rightPanel, BorderLayout.EAST);

		PanelTitle titleLabel = new PanelTitle("REVAL CLAN", SwingConstants.CENTER);

		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(UIConstants.BACKGROUND);
		titlePanel.add(titleLabel, BorderLayout.CENTER);
		titlePanel.setBorder(new EmptyBorder(4, 0, 6, 0));

		header.add(topRow, BorderLayout.NORTH);
		header.add(titlePanel, BorderLayout.CENTER);
		header.add(new GradientSeparator(), BorderLayout.SOUTH);

		return header;
	}

	private void selectTab(String tabName) {
		selectedTab = tabName;
		updateNavStyles();
		cardLayout.show(contentPanel, tabName);
	}

	private void updateNavStyles() {
		boolean isProfile = "PROFILE".equals(selectedTab);
		boolean isLeaderboard = "LEADERBOARD".equals(selectedTab);
		boolean isAchievements = "ACHIEVEMENTS".equals(selectedTab);

		if (profileTab != null) {
			profileTab.setBackground(isProfile ? UIConstants.ACCENT_BLUE : UIConstants.CARD_BG);
			profileTab.setForeground(isProfile ? Color.WHITE : UIConstants.TEXT_SECONDARY);
		}
		if (leaderboardTab != null) {
			leaderboardTab.setBackground(isLeaderboard ? UIConstants.ACCENT_BLUE : UIConstants.CARD_BG);
			leaderboardTab.setForeground(isLeaderboard ? Color.WHITE : UIConstants.TEXT_SECONDARY);
		}
		if (achievementsTab != null) {
			achievementsTab.setBackground(isAchievements ? UIConstants.ACCENT_BLUE : UIConstants.CARD_BG);
			achievementsTab.setForeground(isAchievements ? Color.WHITE : UIConstants.TEXT_SECONDARY);
		}
	}

	private JLabel createSocialIcon(String url, String tooltip) {
		JLabel label = new JLabel();
		label.setToolTipText(tooltip);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					LinkBrowser.browse(url);
				}
			}
		});
		return label;
	}

	public void showTab(String tabName) {
		selectTab(tabName);
	}

	public String getActiveTab() {
		return selectedTab;
	}

	public void init(RevalApiService apiService, Client client,
					 UIAssetLoader assetLoader, ItemManager itemManager, SpriteManager spriteManager) {
		if (assetLoader != null) {
			ImageIcon infoIcon = assetLoader.getIcon("info.png", 16);
			if (infoIcon != null && infoButton != null) {
				infoButton.setIcon(infoIcon);
			}

			ImageIcon discordImg = assetLoader.getIcon("discord.png", 22);
			if (discordImg != null && discordIcon != null) {
				discordIcon.setIcon(discordImg);
			}

			ImageIcon websiteImg = assetLoader.getIcon("website.png", 16);
			if (websiteImg != null && websiteIcon != null) {
				websiteIcon.setIcon(websiteImg);
			}
		}

		rankingPanel.init(apiService, itemManager, spriteManager);
		profilePanel.init(apiService, client, assetLoader);
		leaderboardPanel.init(apiService, assetLoader);
		achievementsPanel.init(apiService, client);
	}

	public void onLoggedIn() {
		profilePanel.refresh();
		achievementsPanel.onLoggedIn();
	}

	public void onLoggedOut() {
		profilePanel.onLoggedOut();
		achievementsPanel.onLoggedOut();
	}
}

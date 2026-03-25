package com.revalclan.ui;

import com.revalclan.RevalClanConfig;
import com.revalclan.api.RevalApiService;
import com.revalclan.ui.admin.AdminDashboardPanel;
import com.revalclan.ui.admin.AdminLoginPanel;
import com.revalclan.ui.admin.AdminManager;
import com.revalclan.ui.admin.PendingRankupsPanel;
import com.revalclan.ui.components.AdminButton;
import com.revalclan.ui.components.GradientSeparator;
import com.revalclan.ui.components.IndicatorTabButton;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanRank;
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
	@Getter private final CompetitionsPanel competitionsPanel;
	@Getter private final EventsPanel eventsPanel;
	@Getter private final DiaryPanel diaryPanel;

	private JLabel infoButton;
	private JLabel discordIcon;
	private JLabel websiteIcon;
	@Getter private AdminButton adminButton;

	private JButton profileTab;
	private IndicatorTabButton eventsTab;
	private JButton leaderboardTab;
	private JButton achievementsTab;
	private IndicatorTabButton competitionsTab;
	private JButton diaryTab;
	private String selectedTab = "PROFILE";

	// Admin
	private AdminManager adminManager;
	private AdminLoginPanel adminLoginPanel;
	private AdminDashboardPanel adminDashboardPanel;
	private PendingRankupsPanel pendingRankupsPanel;
	private RevalApiService apiService;
	private Client client;
	private UIAssetLoader assetLoader;

	public RevalPanel() {
		super(false);

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		profilePanel = new ProfilePanel();
		rankingPanel = new RankingPanel();
		leaderboardPanel = new LeaderboardPanel();
		achievementsPanel = new AchievementsPanel();
		competitionsPanel = new CompetitionsPanel();
		eventsPanel = new EventsPanel();
		diaryPanel = new DiaryPanel();
		cardLayout = new CardLayout();
		contentPanel = new JPanel(cardLayout) {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(super.getPreferredSize().width, 0);
			}
		};
		contentPanel.setBackground(UIConstants.BACKGROUND);

		contentPanel.add(profilePanel, "PROFILE");
		contentPanel.add(rankingPanel, "RANKING");
		contentPanel.add(leaderboardPanel, "LEADERBOARD");
		contentPanel.add(achievementsPanel, "ACHIEVEMENTS");
		contentPanel.add(competitionsPanel, "COMPETITIONS");
		contentPanel.add(eventsPanel, "EVENTS");
		contentPanel.add(diaryPanel, "DIARY");
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

		// Second row: Events, 🏆 (small), Competitions
		JPanel row2 = new JPanel();
		row2.setLayout(new BoxLayout(row2, BoxLayout.X_AXIS));
		row2.setOpaque(false);
		row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		eventsTab = createIndicatorTabButton("Events");
		eventsTab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		eventsTab.addActionListener(e -> selectTab("EVENTS"));

		leaderboardTab = createTabButton("\uD83C\uDFC6");
		leaderboardTab.setPreferredSize(new Dimension(36, 28));
		leaderboardTab.setMinimumSize(new Dimension(36, 28));
		leaderboardTab.setMaximumSize(new Dimension(36, 28));
		leaderboardTab.setToolTipText("Leaderboard");
		leaderboardTab.addActionListener(e -> selectTab("LEADERBOARD"));

		competitionsTab = createIndicatorTabButton("Competitions");
		competitionsTab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		competitionsTab.addActionListener(e -> selectTab("COMPETITIONS"));

		row2.add(eventsTab);
		row2.add(Box.createRigidArea(new Dimension(4, 0)));
		row2.add(leaderboardTab);
		row2.add(Box.createRigidArea(new Dimension(4, 0)));
		row2.add(competitionsTab);

		// Third row: Diary
		JPanel row3 = new JPanel(new GridLayout(1, 1, 4, 0));
		row3.setOpaque(false);
		row3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		diaryTab = createTabButton("Diary");
		diaryTab.addActionListener(e -> selectTab("DIARY"));

		row3.add(diaryTab);

		navBar.add(row1);
		navBar.add(Box.createRigidArea(new Dimension(0, 4)));
		navBar.add(row2);
		navBar.add(Box.createRigidArea(new Dimension(0, 4)));
		navBar.add(row3);

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

	private IndicatorTabButton createIndicatorTabButton(String text) {
		IndicatorTabButton btn = new IndicatorTabButton(text);
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

		// Info button (left)
		infoButton = new JLabel();
		infoButton.setToolTipText("Ranks & Points");
		infoButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		infoButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) selectTab("RANKING");
			}
		});

		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		leftPanel.setOpaque(false);
		leftPanel.setPreferredSize(new Dimension(30, 22));
		leftPanel.add(infoButton);

		// Admin button (right)
		adminButton = new AdminButton();
		adminButton.addActionListener(e -> {
			if (adminButton.isEnabled() && adminManager != null) {
				navigateToAdmin(adminManager.isLoggedIn() ? "DASHBOARD" : "LOGIN");
			}
		});

		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightPanel.setOpaque(false);
		rightPanel.setPreferredSize(new Dimension(30, 22));
		rightPanel.add(adminButton);

		// Social icons (center)
		JPanel socialPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
		socialPanel.setOpaque(false);
		socialPanel.setBorder(new EmptyBorder(0, 0, 6, 0));

		discordIcon = createSocialIcon(DISCORD_URL, "Join our Discord");
		websiteIcon = createSocialIcon(WEBSITE_URL, "Visit our website");

		socialPanel.add(discordIcon);
		socialPanel.add(websiteIcon);

		JPanel centerWrapper = new JPanel();
		centerWrapper.setLayout(new BoxLayout(centerWrapper, BoxLayout.X_AXIS));
		centerWrapper.setOpaque(false);
		centerWrapper.add(Box.createHorizontalGlue());
		centerWrapper.add(socialPanel);
		centerWrapper.add(Box.createHorizontalGlue());

		JPanel topRow = new JPanel(new BorderLayout());
		topRow.setOpaque(false);
		topRow.add(leftPanel, BorderLayout.WEST);
		topRow.add(centerWrapper, BorderLayout.CENTER);
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
		styleTab(profileTab, "PROFILE".equals(selectedTab));
		styleTab(eventsTab, "EVENTS".equals(selectedTab));
		styleTab(leaderboardTab, "LEADERBOARD".equals(selectedTab));
		styleTab(achievementsTab, "ACHIEVEMENTS".equals(selectedTab));
		styleTab(competitionsTab, "COMPETITIONS".equals(selectedTab));
		styleTab(diaryTab, "DIARY".equals(selectedTab));
	}

	private void styleTab(JButton tab, boolean active) {
		if (tab == null) return;
		tab.setBackground(active ? UIConstants.ACCENT_BLUE : UIConstants.CARD_BG);
		tab.setForeground(active ? Color.WHITE : UIConstants.TEXT_SECONDARY);
	}

	private JLabel createSocialIcon(String url, String tooltip) {
		JLabel label = new JLabel();
		label.setToolTipText(tooltip);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) LinkBrowser.browse(url);
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

	// ==================== Admin Navigation ====================

	private void navigateToAdmin(String section) {
		if (adminManager == null || apiService == null || client == null) return;

		switch (section) {
			case "LOGIN":
				if (adminManager.isLoggedIn()) {
					navigateToAdmin("DASHBOARD");
					return;
				}
				if (adminLoginPanel == null) {
					adminLoginPanel = new AdminLoginPanel(apiService, client,
						authData -> {
							adminManager.setSession(adminLoginPanel.getEnteredCode(), authData);
							navigateToAdmin("DASHBOARD");
						},
						() -> selectTab("PROFILE")
					);
					contentPanel.add(adminLoginPanel, "ADMIN_LOGIN");
				}
				cardLayout.show(contentPanel, "ADMIN_LOGIN");
				break;

			case "DASHBOARD":
				if (!adminManager.isLoggedIn()) {
					navigateToAdmin("LOGIN");
					return;
				}
				if (adminDashboardPanel == null) {
					adminDashboardPanel = new AdminDashboardPanel(
						apiService, adminManager.getMemberCode(),
						adminManager.getAuthData(), this::navigateToAdmin
					);
					contentPanel.add(adminDashboardPanel, "ADMIN_DASHBOARD");
				} else {
					adminDashboardPanel.refreshStats();
				}
				cardLayout.show(contentPanel, "ADMIN_DASHBOARD");
				break;

			case "PENDING_RANKUPS":
				if (!adminManager.isLoggedIn()) {
					navigateToAdmin("LOGIN");
					return;
				}
				if (pendingRankupsPanel == null) {
					pendingRankupsPanel = new PendingRankupsPanel(
						apiService, adminManager.getMemberCode(),
						() -> navigateToAdmin("DASHBOARD"), assetLoader
					);
					contentPanel.add(pendingRankupsPanel, "ADMIN_PENDING_RANKUPS");
				} else {
					pendingRankupsPanel.loadData();
				}
				cardLayout.show(contentPanel, "ADMIN_PENDING_RANKUPS");
				break;
		}
	}

	// ==================== Initialization ====================

	public void init(RevalApiService apiService, Client client,
					 UIAssetLoader assetLoader, ItemManager itemManager, SpriteManager spriteManager,
					 RevalClanConfig config) {
		this.apiService = apiService;
		this.client = client;
		this.assetLoader = assetLoader;

		if (assetLoader != null) {
			ImageIcon infoIcon = assetLoader.getIcon("info.png", 16);
			if (infoIcon != null && infoButton != null) infoButton.setIcon(infoIcon);

			ImageIcon discordImg = assetLoader.getIcon("discord.png", 22);
			if (discordImg != null && discordIcon != null) discordIcon.setIcon(discordImg);

			ImageIcon websiteImg = assetLoader.getIcon("website.png", 16);
			if (websiteImg != null && websiteIcon != null) websiteIcon.setIcon(websiteImg);
		}

		rankingPanel.init(apiService, itemManager, spriteManager);
		profilePanel.init(apiService, client, assetLoader, config, itemManager);
		leaderboardPanel.init(apiService, assetLoader, itemManager);
		achievementsPanel.init(apiService, client);
		competitionsPanel.init(apiService, client);
		eventsPanel.init(apiService, client);
		diaryPanel.init(apiService, client, assetLoader);
		// Wire up tab indicator callbacks
		eventsPanel.setOnIndicatorUpdate(this::setEventsIndicator);
		competitionsPanel.setOnIndicatorUpdate(this::setCompetitionsIndicator);

		adminManager = new AdminManager();

		// Enable admin button when account loads — check in-game clan rank
		profilePanel.setOnAccountLoaded(account -> {
			if (account != null) {
				checkAdminEligibility(0);
			}
		});
	}

	private static final ClanRank ADMIN_MIN_RANK = new ClanRank(125); // Deputy Owner+

	private void checkAdminEligibility(int attempt) {
		if (attempt > 10) return;

		ClanChannel clanChannel = client.getClanChannel();
		String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		if (clanChannel == null || playerName == null) {
			// Clan channel not ready yet — retry with delay
			Timer timer = new Timer(attempt * 1000, e -> checkAdminEligibility(attempt + 1));
			timer.setRepeats(false);
			timer.start();
			return;
		}

		ClanChannelMember member = clanChannel.findMember(playerName);
		if (member != null && member.getRank().getRank() >= ADMIN_MIN_RANK.getRank()) {
			setAdminEnabled(true);
		}
	}

	/**
	 * Set admin status — call after verifying admin eligibility (e.g. from account data).
	 */
	public void setAdminEnabled(boolean enabled) {
		if (adminButton != null) {
			adminButton.setAdmin(enabled);
		}
	}

	// ==================== Lifecycle ====================

	public void onLoggedIn() {
		profilePanel.refresh();
		achievementsPanel.onLoggedIn();
		competitionsPanel.refresh();
		eventsPanel.onLoggedIn();
		diaryPanel.onLoggedIn();
	}

	public void onLoggedOut() {
		profilePanel.onLoggedOut();
		achievementsPanel.onLoggedOut();
		eventsPanel.onLoggedOut();
		diaryPanel.onLoggedOut();
		if (adminButton != null) adminButton.setAdmin(false);

		setEventsIndicator(false);
	}

	// ==================== Tab Indicators ====================

	public void setEventsIndicator(boolean active) {
		if (eventsTab != null) {
			eventsTab.setIndicator(active, UIConstants.ACCENT_GREEN);
		}
	}

	public void setCompetitionsIndicator(boolean active) {
		if (competitionsTab != null) {
			competitionsTab.setIndicator(active, UIConstants.ACCENT_GOLD);
		}
	}

}

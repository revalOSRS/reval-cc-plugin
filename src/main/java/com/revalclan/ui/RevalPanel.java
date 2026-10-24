package com.revalclan.ui;

import com.revalclan.RevalClanConfig;
import com.revalclan.api.RevalApiService;
import com.revalclan.ui.admin.AdminDashboardPanel;
import com.revalclan.ui.admin.AdminLoginPanel;
import com.revalclan.ui.admin.AdminManager;
import com.revalclan.ui.admin.PendingRankupsPanel;
import com.revalclan.ui.components.AdminButton;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.components.GradientSeparator;
import com.revalclan.ui.components.HalloweenDecoration;
import com.revalclan.ui.components.HalloweenTabButton;
import com.revalclan.ui.components.Labels;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.ClanRanks;
import com.revalclan.util.SpriteIcons;
import com.revalclan.util.UIAssetLoader;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RevalPanel extends PluginPanel {
	private static final String DISCORD_URL = "https://discord.gg/reval";
	private static final String WEBSITE_URL = "https://revalosrs.ee";

	private final CardLayout cardLayout;
	private final JPanel contentPanel;

	@Getter private final ProfilePanel profilePanel;
	private final RankingPanel rankingPanel;
	@Getter private final LeaderboardPanel leaderboardPanel;
	private final AchievementsPanel achievementsPanel;
	private final CompetitionsPanel competitionsPanel;
	@Getter private final EventsPanel eventsPanel;
	private final DiaryPanel diaryPanel;

	private JLabel infoButton;
	private JLabel discordIcon;
	private JLabel websiteIcon;
	private AdminButton adminButton;

	private JButton leaderboardTab;
	private final Map<String, JButton> navTabs = new HashMap<>();
	private String selectedTab = "PROFILE";
	// Accessed only on the EDT. Public data survives validated login changes.
	private final Map<String, Runnable> publicLoads = new HashMap<>();
	private final Map<String, Runnable> memberLoads = new HashMap<>();
	private final Map<String, Runnable> memberClickLoads = new HashMap<>();
	private final Set<String> memberTabsLoaded = new HashSet<>();
	private boolean clanValidated;
	private boolean disposed;
	private Timer adminRetry;

	// Admin
	private AdminManager adminManager;
	private AdminLoginPanel adminLoginPanel;
	private AdminDashboardPanel adminDashboardPanel;
	private PendingRankupsPanel pendingRankupsPanel;
	private RevalApiService apiService;
	private Client client;
	private ClanRankIconResolver rankIconResolver;
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

		showTab("PROFILE");
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

		row1.add(createTabButton("Profile", "PROFILE"));
		row1.add(createTabButton("Achievements", "ACHIEVEMENTS"));

		// Second row: Events, trophy (small), Competitions
		JPanel row2 = new JPanel();
		row2.setLayout(new BoxLayout(row2, BoxLayout.X_AXIS));
		row2.setOpaque(false);
		row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		JButton eventsTab = createTabButton("Events", "EVENTS");
		eventsTab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		// Text is a fallback until init() swaps in the trophy sprite — the 🏆
		// emoji has no glyph in the RuneScape font (missing-glyph box on macOS)
		leaderboardTab = createTabButton("LB", "LEADERBOARD");
		leaderboardTab.setPreferredSize(new Dimension(36, 28));
		leaderboardTab.setMinimumSize(new Dimension(36, 28));
		leaderboardTab.setMaximumSize(new Dimension(36, 28));
		leaderboardTab.setToolTipText("Leaderboard");

		JButton competitionsTab = createTabButton("Competitions", "COMPETITIONS");
		competitionsTab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		row2.add(eventsTab);
		row2.add(Box.createRigidArea(new Dimension(4, 0)));
		row2.add(leaderboardTab);
		row2.add(Box.createRigidArea(new Dimension(4, 0)));
		row2.add(competitionsTab);

		// Third row: Diary
		JPanel row3 = new JPanel(new GridLayout(1, 1, 4, 0));
		row3.setOpaque(false);
		row3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		row3.add(createTabButton("Diary", "DIARY"));

		navBar.add(row1);
		navBar.add(Box.createRigidArea(new Dimension(0, 4)));
		navBar.add(row2);
		navBar.add(Box.createRigidArea(new Dimension(0, 4)));
		navBar.add(row3);

		return navBar;
	}

	/** Nav button that opens the given content card; registered for selection styling. */
	private JButton createTabButton(String text, String tabName) {
		JButton btn = new HalloweenTabButton(text);
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setPreferredSize(new Dimension(0, 28));
		btn.addActionListener(e -> showTab(tabName));
		navTabs.put(tabName, btn);
		return btn;
	}


	private JPanel createHeader() {
		JPanel header = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (UIConstants.isHalloweenTheme()) {
					HalloweenDecoration.paintHeader(g, getWidth(), getHeight());
				}
			}
		};
		header.setBackground(UIConstants.BACKGROUND);
		header.setBorder(new EmptyBorder(6, 12, 8, 12));

		// Info button (left)
		infoButton = new JLabel();
		infoButton.setToolTipText("Ranks & Points");
		Clickable.onPress(infoButton, () -> showTab("RANKING"));

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
		titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		if (UIConstants.isHalloweenTheme()) {
			titleLabel.setForeground(UIConstants.HALLOWEEN_ACCENT);
			titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
			titleLabel.setIcon(HalloweenDecoration.pumpkinIcon(20));
			titleLabel.setIconTextGap(5);
		}

		JPanel titlePanel = Labels.column();
		titlePanel.add(titleLabel);
		if (UIConstants.isHalloweenTheme()) {
			JLabel seasonalLabel = Labels.small("HALLOWEEN AT REVAL", UIConstants.TEXT_SECONDARY);
			seasonalLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			titlePanel.add(Box.createVerticalStrut(4));
			titlePanel.add(seasonalLabel);
		}
		titlePanel.setBorder(new EmptyBorder(4, 0, 6, 0));

		header.add(topRow, BorderLayout.NORTH);
		header.add(titlePanel, BorderLayout.CENTER);
		header.add(new GradientSeparator(), BorderLayout.SOUTH);

		return header;
	}

	public void showTab(String tabName) {
		selectedTab = tabName;
		updateNavStyles();
		cardLayout.show(contentPanel, tabName);
		loadSelectedTab(true);
	}

	private void loadSelectedTab(boolean explicitClick) {
		Runnable publicLoad = publicLoads.remove(selectedTab);
		if (publicLoad != null) publicLoad.run();
		Runnable memberLoad = memberLoads.get(selectedTab);
		if (memberLoad != null && clanValidated && memberTabsLoaded.add(selectedTab)) memberLoad.run();
		Runnable clickLoad = memberClickLoads.get(selectedTab);
		if (explicitClick && clanValidated && clickLoad != null) clickLoad.run();
	}

	private void updateNavStyles() {
		navTabs.forEach((tabName, tab) -> styleTab(tab, tabName.equals(selectedTab)));
	}

	private void styleTab(JButton tab, boolean active) {
		tab.setSelected(active);
		tab.setBackground(active
			? (UIConstants.isHalloweenTheme() ? UIConstants.HALLOWEEN_TAB : UIConstants.ACCENT_BLUE)
			: UIConstants.CARD_BG);
		tab.setForeground(active
			? (UIConstants.isHalloweenTheme() ? UIConstants.HALLOWEEN_TAB_TEXT : Color.WHITE)
			: UIConstants.TEXT_SECONDARY);
	}

	private JLabel createSocialIcon(String url, String tooltip) {
		JLabel label = new JLabel();
		label.setToolTipText(tooltip);
		Clickable.onPress(label, () -> LinkBrowser.browse(url));
		return label;
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
						() -> showTab("PROFILE")
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
						() -> navigateToAdmin("DASHBOARD"), assetLoader,
						rankIconResolver
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
					 RevalClanConfig config, ClanRankIconResolver rankIconResolver) {
		this.apiService = apiService;
		this.client = client;
		this.assetLoader = assetLoader;
		this.rankIconResolver = rankIconResolver;

		// Leaderboard tab icon: leagues trophy from the game cache
		if (leaderboardTab != null) {
			SpriteIcons.load(spriteManager, net.runelite.api.gameval.SpriteID.LeagueTrophyIcons._5, 16, icon -> {
				leaderboardTab.setIcon(icon);
				leaderboardTab.setText("");
			});
		}

		if (assetLoader != null) {
			ImageIcon infoIcon = assetLoader.getIcon("info.png", 16);
			if (infoIcon != null && infoButton != null) infoButton.setIcon(infoIcon);

			ImageIcon discordImg = assetLoader.getIcon("discord.png", 22);
			if (discordImg != null && discordIcon != null) discordIcon.setIcon(discordImg);

			ImageIcon websiteImg = assetLoader.getIcon("website.png", 16);
			if (websiteImg != null && websiteIcon != null) websiteIcon.setIcon(websiteImg);
		}

		rankingPanel.init(apiService, itemManager, spriteManager, rankIconResolver);
		profilePanel.init(apiService, client, assetLoader, config, itemManager, rankIconResolver);
		profilePanel.setOnOpenRanks(() -> showTab("RANKING"));
		leaderboardPanel.init(apiService, assetLoader, itemManager, rankIconResolver);
		achievementsPanel.init(apiService, client);
		competitionsPanel.init(apiService, client, itemManager);
		eventsPanel.init(apiService, client);
		diaryPanel.init(apiService, client, assetLoader);
		publicLoads.put("RANKING", rankingPanel::load);
		publicLoads.put("LEADERBOARD", leaderboardPanel::refresh);
		publicLoads.put("COMPETITIONS", competitionsPanel::refreshRace);
		memberLoads.put("ACHIEVEMENTS", achievementsPanel::refresh);
		memberLoads.put("COMPETITIONS", competitionsPanel::refresh);
		memberClickLoads.put("EVENTS", eventsPanel::refresh);
		memberLoads.put("DIARY", diaryPanel::refresh);

		adminManager = new AdminManager();

		// Enable admin button when account loads — check in-game clan rank
		profilePanel.setOnAccountLoaded(account -> {
			if (account != null) {
				checkAdminEligibility(0);
			}
		});
	}

	private void checkAdminEligibility(int attempt) {
		if (disposed || !clanValidated || attempt > 10) return;
		if (adminRetry != null) { adminRetry.stop(); adminRetry = null; }

		ClanChannel clanChannel = client.getClanChannel();
		String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		if (clanChannel == null || playerName == null) {
			// Clan channel not ready yet — retry with delay
			adminRetry = new Timer(Math.max(1, attempt) * 1000, e -> checkAdminEligibility(attempt + 1));
			adminRetry.setRepeats(false);
			adminRetry.start();
			return;
		}

		if (ClanRanks.isDeputyOwnerPlus(client)) {
			adminButton.setAdmin(true);
		}
	}

	/** Forwarded so the plugin never reaches into child panels. */
	public void setOnSyncGuide(Runnable onSyncGuide) {
		profilePanel.setOnSyncGuide(onSyncGuide);
	}

	/** Tears down anything living outside the panel tree (detached windows). */
	public void shutDown() {
		disposed = true;
		clanValidated = false;
		if (adminRetry != null) { adminRetry.stop(); adminRetry = null; }
		profilePanel.shutDown();
		eventsPanel.onLoggedOut();
	}

	// ==================== Lifecycle ====================

	public void onLoggedIn() {
		SwingUtilities.invokeLater(() -> {
			if (disposed) return;
			clanValidated = true;
			memberTabsLoaded.clear();
			profilePanel.refresh();
			eventsPanel.onLoginReady();
			loadSelectedTab(false);
		});
	}

	public void onLoggedOut() {
		SwingUtilities.invokeLater(() -> {
			if (disposed) return;
			if (adminRetry != null) { adminRetry.stop(); adminRetry = null; }
			clanValidated = false;
			memberTabsLoaded.clear();
			competitionsPanel.onLoggedOut();
			profilePanel.onLoggedOut();
			achievementsPanel.onLoggedOut();
			eventsPanel.onLoggedOut();
			diaryPanel.onLoggedOut();
			if (adminButton != null) adminButton.setAdmin(false);
		});
	}
}

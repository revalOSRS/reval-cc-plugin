package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.ui.admin.AdminDashboardPanel;
import com.revalclan.ui.admin.AdminLoginPanel;
import com.revalclan.ui.admin.AdminManager;
import com.revalclan.ui.admin.PendingRankupsPanel;
import com.revalclan.ui.components.AdminButton;
import com.revalclan.ui.components.AchievementsButton;
import com.revalclan.ui.components.CompetitionsButton;
import com.revalclan.ui.components.LeaderboardButton;
import com.revalclan.ui.components.GradientSeparator;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import com.revalclan.util.WikiIconLoader;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Main side panel for Reval Clan plugin.
 * Features tabbed navigation for Profile, Ranking, Events, Diary, and Tasks.
 */
public class RevalPanel extends PluginPanel {
    // URLs
    private static final String DISCORD_URL = "https://discord.gg/reval";
    private static final String WEBSITE_URL = "https://revalosrs.ee";
    
    // Components
    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final RevalTabGroup tabGroup;
    
    // Tab panels
    @Getter private final ProfilePanel profilePanel;
    @Getter private final RankingPanel rankingPanel;
    @Getter private final EventsPanel eventsPanel;
    private final DiaryPanel diaryPanel;
    private final TasksPanel tasksPanel;
    private final AchievementsPanel achievementsPanel;
    @Getter private final LeaderboardPanel leaderboardPanel;
    @Getter private final CompetitionsPanel competitionsPanel;
    
    // Admin panels
    private AdminLoginPanel adminLoginPanel;
    private AdminDashboardPanel adminDashboardPanel;
    private PendingRankupsPanel pendingRankupsPanel;
    private AdminManager adminManager;
    @Getter private AdminButton adminButton;
    private AchievementsButton achievementsButton;
    private LeaderboardButton leaderboardButton;
    private CompetitionsButton competitionsButton;
    
    // Header icons (initialized later when assetLoader is available)
    private JLabel infoButton;
    private JLabel discordIcon;
    private JLabel websiteIcon;
    
    private UIAssetLoader assetLoader;
    private WikiIconLoader wikiIconLoader;
    
    public RevalPanel() {
        super(false);
        
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);
        
        // Initialize tab panels
        profilePanel = new ProfilePanel();
        rankingPanel = new RankingPanel();
        eventsPanel = new EventsPanel();
        diaryPanel = new DiaryPanel();
        tasksPanel = new TasksPanel();
        achievementsPanel = new AchievementsPanel();
        leaderboardPanel = new LeaderboardPanel();
        competitionsPanel = new CompetitionsPanel();
        
        // Create card layout for content switching
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(UIConstants.BACKGROUND);
        
        // Add panels to card layout
        contentPanel.add(profilePanel, "PROFILE");
        contentPanel.add(rankingPanel, "RANKING");
        contentPanel.add(eventsPanel, "EVENTS");
        contentPanel.add(diaryPanel, "DIARY");
        contentPanel.add(tasksPanel, "TASKS");
        contentPanel.add(achievementsPanel, "ACHIEVEMENTS");
        contentPanel.add(leaderboardPanel, "LEADERBOARD");
        contentPanel.add(competitionsPanel, "COMPETITIONS");
        
        // Create tab group
        tabGroup = new RevalTabGroup(this::switchTab);
        
        // Create achievements button (second navigation line)
        JPanel achievementsNav = createAchievementsNavigation();
        
        // Create header with social icons and admin button
        JPanel header = createHeader();
        
        // Assemble the panel
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setBackground(UIConstants.BACKGROUND);
        topSection.add(header, BorderLayout.NORTH);
        topSection.add(tabGroup, BorderLayout.CENTER);
        topSection.add(achievementsNav, BorderLayout.SOUTH);
        
        add(topSection, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        
        // Select default tab
        tabGroup.selectTab("PROFILE");
    }
    
    /**
     * Creates the header section with Discord and Website icons
     */
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BACKGROUND);
        header.setBorder(new EmptyBorder(6, 12, 8, 12));
        
        // Info button (top left) - opens rankings
        infoButton = createSocialIcon(null, null, "Ranks & Points");
        infoButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    switchTab("RANKING");
                }
            }
        });
        
        JPanel infoButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        infoButtonPanel.setBackground(UIConstants.BACKGROUND);
        infoButtonPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        infoButtonPanel.setOpaque(false);
        infoButtonPanel.setPreferredSize(new Dimension(30, 22));
        infoButtonPanel.add(infoButton);
        
        // Admin button (top right) - always visible, grayed out if not admin
        adminButton = createAdminHeaderButton();
        JPanel adminButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        adminButtonPanel.setBackground(UIConstants.BACKGROUND);
        adminButtonPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        adminButtonPanel.setOpaque(false);
        adminButtonPanel.setPreferredSize(new Dimension(30, 22));
        adminButtonPanel.add(adminButton);
        
        // Social icons panel at the very top (centered)
        JPanel socialPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        socialPanel.setBackground(UIConstants.BACKGROUND);
        socialPanel.setOpaque(false);
        socialPanel.setBorder(new EmptyBorder(0, 0, 6, 0));
        
        discordIcon = createSocialIcon(null, DISCORD_URL, "Join our Discord");
        websiteIcon = createSocialIcon(null, WEBSITE_URL, "Visit revalosrs.ee");
        
        socialPanel.add(discordIcon);
        socialPanel.add(websiteIcon);
        
        // Top row: use BorderLayout with centered social icons and admin button on right
        // Use a wrapper to ensure social icons stay centered
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(UIConstants.BACKGROUND);
        topRow.setOpaque(false);
        
        // Center panel for social icons - uses BoxLayout with glue to center
        JPanel centerWrapper = new JPanel();
        centerWrapper.setLayout(new BoxLayout(centerWrapper, BoxLayout.X_AXIS));
        centerWrapper.setOpaque(false);
        centerWrapper.add(Box.createHorizontalGlue());
        centerWrapper.add(socialPanel);
        centerWrapper.add(Box.createHorizontalGlue());
        
        topRow.add(infoButtonPanel, BorderLayout.WEST);
        topRow.add(centerWrapper, BorderLayout.CENTER);
        topRow.add(adminButtonPanel, BorderLayout.EAST);
        
        PanelTitle titleLabel = new PanelTitle("REVAL CLAN", SwingConstants.CENTER);
        
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(UIConstants.BACKGROUND);
        titlePanel.add(titleLabel, BorderLayout.CENTER);
        titlePanel.setBorder(new EmptyBorder(4, 0, 6, 0));
        
        header.add(topRow, BorderLayout.NORTH);
        header.add(titlePanel, BorderLayout.CENTER);
        
        GradientSeparator separator = new GradientSeparator();
        header.add(separator, BorderLayout.SOUTH);
        
        return header;
    }
    
    /**
     * Creates the admin button for the header (top right)
     */
    private AdminButton createAdminHeaderButton() {
        AdminButton button = new AdminButton();
        
        button.addActionListener(e -> {
            if (button.isEnabled() && adminManager != null) {
                if (adminManager.isLoggedIn()) {
                    navigateToAdmin("DASHBOARD");
                } else {
                    navigateToAdmin("LOGIN");
                }
            }
        });
        
        return button;
    }
    
    /**
     * Creates the secondary navigation buttons (Leaderboard, Competitions, Achievements)
     */
    private JPanel createAchievementsNavigation() {
        JPanel navPanel = new JPanel(new GridBagLayout());
        navPanel.setBackground(UIConstants.BACKGROUND);
        navPanel.setBorder(new EmptyBorder(0, 6, 6, 6));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, 0, 2);
        
        // Leaderboard button
        leaderboardButton = new LeaderboardButton();
        leaderboardButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    switchTab("LEADERBOARD");
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!leaderboardButton.isSelected()) {
                    leaderboardButton.setHovered(true);
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                leaderboardButton.setHovered(false);
            }
        });
        
        // Competitions button
        competitionsButton = new CompetitionsButton();
        competitionsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    switchTab("COMPETITIONS");
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!competitionsButton.isSelected()) {
                    competitionsButton.setHovered(true);
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                competitionsButton.setHovered(false);
            }
        });
        
        // Achievements button
        achievementsButton = new AchievementsButton();
        achievementsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    switchTab("ACHIEVEMENTS");
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!achievementsButton.isSelected()) {
                    achievementsButton.setHovered(true);
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                achievementsButton.setHovered(false);
            }
        });
        
        // Competitions button (left) - wider
        gbc.gridx = 0;
        gbc.weightx = 0.4;
        navPanel.add(competitionsButton, gbc);
        
        // Leaderboard button (center) - smaller for trophy emoji
        gbc.gridx = 1;
        gbc.weightx = 0.2;
        gbc.insets = new Insets(0, 2, 0, 2);
        navPanel.add(leaderboardButton, gbc);
        
        // Achievements button (right) - wider
        gbc.gridx = 2;
        gbc.weightx = 0.4;
        gbc.insets = new Insets(0, 2, 0, 0);
        navPanel.add(achievementsButton, gbc);
        
        return navPanel;
    }
    
    /**
     * Update achievements button selection state
     */
    private void setAchievementsButtonSelected(boolean selected) {
        if (achievementsButton != null) {
            achievementsButton.setSelected(selected);
        }
    }
    
    /**
     * Update leaderboard button selection state
     */
    private void setLeaderboardButtonSelected(boolean selected) {
        if (leaderboardButton != null) {
            leaderboardButton.setSelected(selected);
        }
    }
    
    /**
     * Update competitions button selection state
     */
    private void setCompetitionsButtonSelected(boolean selected) {
        if (competitionsButton != null) {
            competitionsButton.setSelected(selected);
        }
    }
    
    
    /**
     * Creates a clickable social icon with hover effects
     */
    private JLabel createSocialIcon(ImageIcon icon, String url, String tooltip) {
        JLabel label = new JLabel(icon);
        label.setToolTipText(tooltip);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        if (url != null) {
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        LinkBrowser.browse(url);
                    }
                }
            });
        }
        
        return label;
    }
    
    /**
     * Switches to the specified tab content
     */
    private void switchTab(String tabName) {
        if (!tabName.startsWith("ADMIN_") && !tabName.equals("RANKING") && !tabName.equals("ACHIEVEMENTS") && !tabName.equals("LEADERBOARD") && !tabName.equals("COMPETITIONS")) {
            tabGroup.selectTab(tabName);
            setAchievementsButtonSelected(false);
            setLeaderboardButtonSelected(false);
            setCompetitionsButtonSelected(false);
        } else if (tabName.equals("ACHIEVEMENTS")) {
            tabGroup.deselectCurrentTab();
            setAchievementsButtonSelected(true);
            setLeaderboardButtonSelected(false);
            setCompetitionsButtonSelected(false);
        } else if (tabName.equals("LEADERBOARD")) {
            tabGroup.deselectCurrentTab();
            setAchievementsButtonSelected(false);
            setLeaderboardButtonSelected(true);
            setCompetitionsButtonSelected(false);
        } else if (tabName.equals("COMPETITIONS")) {
            tabGroup.deselectCurrentTab();
            setAchievementsButtonSelected(false);
            setLeaderboardButtonSelected(false);
            setCompetitionsButtonSelected(true);
        } else if (tabName.equals("RANKING")) { 
            tabGroup.deselectCurrentTab();
            setAchievementsButtonSelected(false);
            setLeaderboardButtonSelected(false);
            setCompetitionsButtonSelected(false);
        }
        cardLayout.show(contentPanel, tabName);
    }
    
    /**
     * Public method to switch tabs (for admin navigation)
     */
    public void showTab(String tabName) {
        switchTab(tabName);
    }
    
    /**
     * Gets the currently active tab name
     */
    public String getActiveTab() {
        return tabGroup.getSelectedTab();
    }
    
    /**
     * Initialize panels with dependencies from the plugin
     */
    public void init(RevalApiService apiService, ItemManager itemManager, 
                     SkillIconManager skillIconManager, Client client, WikiIconLoader wikiIconLoader, UIAssetLoader assetLoader) {
        this.assetLoader = assetLoader;
        this.wikiIconLoader = wikiIconLoader;
        
        // Load header icons now that assetLoader is available
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
        
        rankingPanel.init(apiService, itemManager, wikiIconLoader);
        profilePanel.init(apiService, client, assetLoader);
        eventsPanel.init(apiService, client);
        tasksPanel.init(apiService, client, assetLoader);
        diaryPanel.init(apiService, client, assetLoader);
        achievementsPanel.init(apiService, client);
        leaderboardPanel.init(apiService, wikiIconLoader, assetLoader);
        competitionsPanel.init(apiService, client);
        
        // Initialize admin manager
        adminManager = new AdminManager();
        profilePanel.setAdminManager(adminManager);
        profilePanel.setAdminNavigationCallback(this::navigateToAdmin);
        profilePanel.setAdminButton(adminButton);
    }
    
    /**
     * Navigate to admin section
     */
    public void navigateToAdmin(String section) {
        if (adminManager == null) {
            return;
        }
        
        RevalApiService apiService = profilePanel.getApiService();
        Client client = profilePanel.getClient();
        
        if (apiService == null || client == null) {
            return;
        }
        
        switch (section) {
            case "LOGIN":
                if (adminManager.isLoggedIn()) {
                    navigateToAdmin("DASHBOARD");
                    return;
                }
                if (adminLoginPanel == null) {
                    adminLoginPanel = new AdminLoginPanel(
                        apiService,
                        client,
                        authData -> {
                            String code = adminLoginPanel.getEnteredCode();
                            adminManager.setSession(code, authData);
                            navigateToAdmin("DASHBOARD");
                        },
                        () -> switchTab("PROFILE")
                    );
                    contentPanel.add(adminLoginPanel, "ADMIN_LOGIN");
                }
                switchTab("ADMIN_LOGIN");
                break;
                
            case "DASHBOARD":
                if (!adminManager.isLoggedIn()) {
                    navigateToAdmin("LOGIN");
                    return;
                }
                if (adminDashboardPanel == null) {
                    adminDashboardPanel = new AdminDashboardPanel(
                        apiService,
                        adminManager.getMemberCode(),
                        adminManager.getAuthData(),
                        this::navigateToAdmin
                    );
                    contentPanel.add(adminDashboardPanel, "ADMIN_DASHBOARD");
                } else {
                    adminDashboardPanel.refreshStats();
                }
                tabGroup.deselectCurrentTab();
                setAchievementsButtonSelected(false);
                switchTab("ADMIN_DASHBOARD");
                break;
                
            case "PENDING_RANKUPS":
                if (!adminManager.isLoggedIn()) {
                    navigateToAdmin("LOGIN");
                    return;
                }
                if (pendingRankupsPanel == null) {
                    pendingRankupsPanel = new PendingRankupsPanel(
                        apiService,
                        adminManager.getMemberCode(),
                        () -> navigateToAdmin("DASHBOARD"),
                        wikiIconLoader,
                        assetLoader
                    );
                    contentPanel.add(pendingRankupsPanel, "ADMIN_PENDING_RANKUPS");
                } else {
                    pendingRankupsPanel.loadData();
                }
                tabGroup.deselectCurrentTab();
                setAchievementsButtonSelected(false);
                switchTab("ADMIN_PENDING_RANKUPS");
                break;
                
            case "BACK":
                switchTab("PROFILE");
                break;
        }
    }
    
    /**
     * Called when player logs in - load profile and events data
     */
    public void onLoggedIn() {
        profilePanel.loadCurrentAccount();
        eventsPanel.onLoggedIn();
        tasksPanel.onLoggedIn();
        diaryPanel.onLoggedIn();
        achievementsPanel.onLoggedIn();
    }
    
    /**
     * Called when player logs out - show login messages on all panels
     */
    public void onLoggedOut() {
        profilePanel.onLoggedOut();
        eventsPanel.onLoggedOut();
        tasksPanel.onLoggedOut();
        diaryPanel.onLoggedOut();
        achievementsPanel.onLoggedOut();
        
        if (adminButton != null) {
            adminButton.setAdmin(false);
        }
    }
    
    /**
     * Called when clan channel becomes available - retry loading panels if they failed initially
     * This acts as a fallback when the player was AFK and clan data wasn't ready on login
     */
    public void onClanChannelReady() {
        // Retry loading panels that might still be showing the login prompt
        eventsPanel.onLoggedIn();
        tasksPanel.onLoggedIn();
        diaryPanel.onLoggedIn();
        achievementsPanel.onLoggedIn();
    }
    
    /**
     * Set notification badge on the Events tab
     * @param show true to show badge, false to hide
     */
    public void setEventsBadge(boolean show) {
        tabGroup.setBadge("EVENTS", show);
    }
    
    /**
     * Check if Events tab has a badge showing
     */
    public boolean hasEventsBadge() {
        return tabGroup.hasBadge("EVENTS");
    }
    
}

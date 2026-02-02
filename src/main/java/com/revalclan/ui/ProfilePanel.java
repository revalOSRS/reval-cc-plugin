package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.admin.AdminManager;
import com.revalclan.ui.components.ChecklistItem;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Profile tab panel - displays comprehensive player profile information
 */
public class ProfilePanel extends JPanel {
    private final JPanel contentPanel;
    private final GridBagConstraints gbc;
    private int gridY = 0;

    private RevalApiService apiService;
    private Client client;
    private UIAssetLoader assetLoader;
    
    private AdminManager adminManager;
    private Consumer<String> adminNavigationCallback;
    private JButton adminButton;

    private AccountResponse.AccountData currentAccount;
    private List<PointsResponse.Rank> ranks;
    private PointsResponse.PointsData pointsData;
    private List<AccountResponse.PointsLogEntry> pointsLog;
    private boolean isLoading = false;
    private Timer adminCheckTimer;
    private boolean isUpdatingAdminButton = false;

    public ProfilePanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);

        // Content panel with GridBagLayout for width control
        contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(UIConstants.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(4, 2, 4, 2));

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(2, 0, 2, 0);

        // Wrapper for proper width constraint
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                if (getParent() != null) {
                    size.width = getParent().getWidth();
                }
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


        showNotLoggedIn();

        add(scrollPane, BorderLayout.CENTER);
    }

    private void addComponent(JComponent comp) {
        gbc.gridy = gridY++;
        contentPanel.add(comp, gbc);
    }

    private void addSpacing(int height) {
        gbc.gridy = gridY++;
        contentPanel.add(Box.createVerticalStrut(height), gbc);
    }

    public void init(RevalApiService apiService, Client client, UIAssetLoader assetLoader) {
        this.apiService = apiService;
        this.client = client;
        this.assetLoader = assetLoader;
        fetchRanks();
    }
    
    /**
     * Set admin manager for admin functionality
     */
    public void setAdminManager(AdminManager adminManager) {
        this.adminManager = adminManager;
    }
    
    /**
     * Set admin navigation callback
     */
    public void setAdminNavigationCallback(Consumer<String> callback) {
        this.adminNavigationCallback = callback;
    }
    
    /**
     * Set admin button for updating visibility
     */
    public void setAdminButton(JButton adminButton) {
        this.adminButton = adminButton;
        if (adminButton != null) {
            adminButton.addActionListener(e -> {
                if (adminNavigationCallback != null) {
                    // Check if already logged in
                    if (adminManager != null && adminManager.isLoggedIn()) {
                        adminNavigationCallback.accept("DASHBOARD");
                    } else {
                        adminNavigationCallback.accept("LOGIN");
                    }
                }
            });
        }
        updateAdminButton();
    }
    
    /**
     * Update admin button visibility in header
     */
    private void updateAdminButton() {
        if (adminButton == null || isUpdatingAdminButton) {
            return;
        }
        
        isUpdatingAdminButton = true;
        try {
            boolean hasAdmin = hasAdminRank(() -> {
                javax.swing.Timer delayTimer = new javax.swing.Timer(100, e -> updateAdminButton());
                delayTimer.setRepeats(false);
                delayTimer.start();
            });
            if (hasAdmin && adminNavigationCallback != null) {
                try {
                    java.lang.reflect.Method setAdminMethod = adminButton.getClass().getMethod("setAdmin", boolean.class);
                    setAdminMethod.invoke(adminButton, true);
                } catch (Exception e) {
                    adminButton.setEnabled(true);
                    adminButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            } else {
                try {
                    java.lang.reflect.Method setAdminMethod = adminButton.getClass().getMethod("setAdmin", boolean.class);
                    setAdminMethod.invoke(adminButton, false);
                } catch (Exception e) {
                    adminButton.setEnabled(false);
                    adminButton.setCursor(Cursor.getDefaultCursor());
                }
            }
        } finally {
            isUpdatingAdminButton = false;
        }
    }
    
    /**
     * Get API service (for admin panels)
     */
    public RevalApiService getApiService() {
        return apiService;
    }
    
    /**
     * Get client (for admin panels)
     */
    public Client getClient() {
        return client;
    }
    
    /**
     * Check if user has admin rank (>= 125) with delayed retry if clan channel is not available
     * @param retryCallback Optional callback to refresh UI if admin status changes after retry
     */
    private boolean hasAdminRank(Runnable retryCallback) {
        if (client == null) {
            return false;
        }
        
        GameState gameState = client.getGameState();
        if (gameState != GameState.LOGGED_IN) {
            if (adminCheckTimer != null && adminCheckTimer.isRunning()) {
                adminCheckTimer.stop();
            }
            return false;
        }
        
        ClanChannel clanChannel = client.getClanChannel();
        if (clanChannel == null) {
            if (retryCallback != null && (adminCheckTimer == null || !adminCheckTimer.isRunning())) {
                adminCheckTimer = new javax.swing.Timer(1000, e -> {
                    if (client.getGameState() != GameState.LOGGED_IN) {
                        adminCheckTimer.stop();
                        return;
                    }
                    if (hasAdminRank(null)) {
                        adminCheckTimer.stop();
                        SwingUtilities.invokeLater(() -> {
                            javax.swing.Timer delayTimer = new javax.swing.Timer(100, ev -> retryCallback.run());
                            delayTimer.setRepeats(false);
                            delayTimer.start();
                        });
                    }
                });
                adminCheckTimer.setRepeats(true);
                adminCheckTimer.start();
            }
            return false;
        }
        
        if (adminCheckTimer != null && adminCheckTimer.isRunning()) {
            adminCheckTimer.stop();
        }
        
        String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
        if (playerName == null) {
            return false;
        }
        
        ClanChannelMember member = clanChannel.findMember(playerName);
        if (member == null) {
            return false;
        }
        
        return member.getRank().getRank() >= 125;
    }
    

    private void fetchRanks() {
        if (apiService != null) {
            // Fetch points which now includes ranks
            apiService.fetchPoints(
                response -> {
                    if (response.getData() != null) {
                        pointsData = response.getData();
                        if (response.getData().getRanks() != null) {
                            ranks = response.getData().getRanks();
                        }
                        if (currentAccount != null) {
                            SwingUtilities.invokeLater(this::buildProfile);
                        }
                    }
                },
                error -> {}
            );
        }
    }

    /**
     * Called when the player logs out - show login message
     */
    public void onLoggedOut() {
        SwingUtilities.invokeLater(() -> showNotLoggedIn());
    }
    
    /**
     * Load profile data for the currently logged in account
     */
    public void loadCurrentAccount() {
        loadCurrentAccount(false);
    }

    /**
     * Load profile data for the currently logged in account
     * @param retry If true and hash is -1, will retry once after a delay
     */
    public void loadCurrentAccount(boolean retry) {
        if (client == null || apiService == null) {
            showNotLoggedIn();
            return;
        }

        long accountHash = client.getAccountHash();

        if (accountHash == -1) {
            if (retry) {
                showError("Not logged into RuneLite account");
            } else {
                javax.swing.Timer timer = new javax.swing.Timer(2000, e -> loadCurrentAccount(true));
                timer.setRepeats(false);
                timer.start();
                showLoading();
            }
            return;
        }

        loadAccount(accountHash);
    }

    /**
     * Load profile for a specific account hash
     */
    public void loadAccount(long accountHash) {
        if (isLoading) return;
        isLoading = true;

        showLoading();
        
        apiService.fetchAccount(accountHash,
            response -> {
                isLoading = false;
                SwingUtilities.invokeLater(() -> {
                    currentAccount = response.getData();
                    if (currentAccount != null) {
                        pointsLog = currentAccount.getPointsLog();
                    }
                    // Only build profile if pointsData is already loaded
                    // Otherwise, fetchRanks will trigger the build when it completes
                    if (pointsData != null) {
                        buildProfile();
                    }
                    // Always fetch ranks/pointsData - it will rebuild the profile when done
                    if (ranks == null || ranks.isEmpty() || pointsData == null) {
                        fetchRanks();
                    }
                });
            },
            error -> {
                isLoading = false;
                SwingUtilities.invokeLater(() -> {
                    String errorMsg = error.getMessage() != null ? error.getMessage() : "Failed to fetch account data";
                    showError(errorMsg);
                });
            }
        );
    }

    /**
     * Load profile for any player by their OSRS account ID.
     * Used when viewing another player's profile from the leaderboard.
     */
    public void loadAccountById(int osrsAccountId) {
        if (isLoading) return;
        isLoading = true;

        showLoading();
        
        apiService.fetchAccountById(osrsAccountId,
            response -> {
                isLoading = false;
                SwingUtilities.invokeLater(() -> {
                    currentAccount = response.getData();
                    if (currentAccount != null) {
                        pointsLog = currentAccount.getPointsLog();
                    }
                    // Only build profile if pointsData is already loaded
                    // Otherwise, fetchRanks will trigger the build when it completes
                    if (pointsData != null) {
                        buildProfile();
                    }
                    // Always fetch ranks/pointsData - it will rebuild the profile when done
                    if (ranks == null || ranks.isEmpty() || pointsData == null) {
                        fetchRanks();
                    }
                });
            },
            error -> {
                isLoading = false;
                SwingUtilities.invokeLater(() -> {
                    String errorMsg = error.getMessage() != null ? error.getMessage() : "Player not found";
                    showError(errorMsg);
                });
            }
        );
    }

    /**
     * Refresh current profile
     */
    public void refresh() {
        if (apiService != null && client != null) {
            apiService.clearAccountCache();
            loadCurrentAccount();
        }
    }

    /**
     * Get the current account's clan rank
     * @return The clan rank string, or null if not loaded
     */
    public String getClanRank() {
        if (currentAccount != null && currentAccount.getOsrsAccount() != null) {
            return currentAccount.getOsrsAccount().getClanRank();
        }
        return null;
    }

    /**
     * Check if account data is loaded
     */
    public boolean isAccountLoaded() {
        return currentAccount != null && currentAccount.getOsrsAccount() != null;
    }

    private void showNotLoggedIn() {
        contentPanel.removeAll();
        gridY = 0;

        JPanel placeholder = createCenteredPanel();
        placeholder.setBorder(new EmptyBorder(30, 20, 20, 20));

        JLabel title = new JLabel("PROFILE");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(UIConstants.ACCENT_GOLD);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("Log in to view your profile");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(UIConstants.TEXT_SECONDARY);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        placeholder.add(title);
        placeholder.add(Box.createRigidArea(new Dimension(0, 6)));
        placeholder.add(hint);
        
        // Admin button is now in navigation row, not here

        addComponent(placeholder);
        revalidateAndRepaint();
    }
    

    private void showLoading() {
        contentPanel.removeAll();
        gridY = 0;

        JPanel placeholder = createCenteredPanel();
        placeholder.setBorder(new EmptyBorder(50, 20, 20, 20));

        JLabel loading = new JLabel("Loading profile...");
        loading.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        loading.setForeground(UIConstants.TEXT_SECONDARY);
        loading.setAlignmentX(Component.CENTER_ALIGNMENT);

        placeholder.add(loading);
        addComponent(placeholder);
        revalidateAndRepaint();
    }

    private void showError(String message) {
        contentPanel.removeAll();
        gridY = 0;

        JPanel placeholder = createCenteredPanel();
        placeholder.setBorder(new EmptyBorder(30, 20, 20, 20));

        JLabel errorIcon = new JLabel("⚠");
        errorIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        errorIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel errorLabel = new JLabel("<html><center>" + message + "</center></html>");
        errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        errorLabel.setForeground(UIConstants.ERROR_COLOR);
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("Make sure you're in the Reval clan");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        hint.setForeground(UIConstants.TEXT_SECONDARY);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        placeholder.add(errorIcon);
        placeholder.add(Box.createRigidArea(new Dimension(0, 6)));
        placeholder.add(errorLabel);
        placeholder.add(Box.createRigidArea(new Dimension(0, 6)));
        placeholder.add(hint);

        addComponent(placeholder);
        revalidateAndRepaint();
    }

    private void buildProfile() {
        contentPanel.removeAll();
        gridY = 0;

        if (currentAccount == null || currentAccount.getOsrsAccount() == null) {
            showError("No profile data");
            return;
        }

        AccountResponse.OsrsAccount account = currentAccount.getOsrsAccount();

        // === HEADER SECTION ===
        addComponent(buildHeaderSection(account));
        addSpacing(6);

        // === POINTS BREAKDOWN ===
        if (currentAccount.getPointsBreakdown() != null) {
            // Add with explicit left alignment and no left insets
            gbc.gridy = gridY++;
            gbc.anchor = GridBagConstraints.NORTHWEST; // Align to top-left
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 0, 2, 0); // No left inset
            contentPanel.add(buildPointsSection(currentAccount.getPointsBreakdown()), gbc);
            // Reset anchor and insets for other components
            gbc.anchor = GridBagConstraints.NORTH;
            gbc.insets = new Insets(2, 0, 2, 0);
            addSpacing(6);
        }

        // === STATS OVERVIEW ===
        addComponent(buildStatsSection(account));
        addSpacing(6);

        // === MILESTONES SECTION ===
        addComponent(buildMilestonesSection(currentAccount.getMilestones()));
        addSpacing(6);

        // === COMBAT ACHIEVEMENTS SECTION ===
        addComponent(buildCombatAchievementsSection());
        addSpacing(6);

        // === COLLECTION LOG SECTION ===
        addComponent(buildCollectionLogSection());
        addSpacing(6);
        
        // Admin button is now in navigation row, not here
        // Update admin button visibility in navigation
        SwingUtilities.invokeLater(this::updateAdminButton);

        addSpacing(16);
        revalidateAndRepaint();
    }

    private JPanel buildHeaderSection(AccountResponse.OsrsAccount account) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(UIConstants.CARD_BG);
        header.setBorder(new EmptyBorder(10, 12, 10, 12));

        // Left side: Name, rank, and rank progress
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        // Top row: Name
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(account.getOsrsNickname());
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameRow.add(nameLabel);

        // Rank badge
        JLabel rankLabel = new JLabel(formatRank(account.getClanRank()));
        rankLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        rankLabel.setForeground(UIConstants.ACCENT_GOLD);
        rankLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        leftPanel.add(nameRow);
        leftPanel.add(rankLabel);

        // Rank progress bar
        JPanel rankProgress = buildRankProgressBar(account);
        if (rankProgress != null) {
            leftPanel.add(Box.createRigidArea(new Dimension(0, 6)));
            rankProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
            leftPanel.add(rankProgress);
        }

        // Right: Points
        JPanel pointsPanel = new JPanel();
        pointsPanel.setLayout(new BoxLayout(pointsPanel, BoxLayout.Y_AXIS));
        pointsPanel.setOpaque(false);

        int exactPoints = account.getActivityPoints() != null ? account.getActivityPoints() : 0;
        JLabel pointsValue = new JLabel(formatNumber(exactPoints));
        pointsValue.setFont(new Font("Segoe UI", Font.BOLD, 20));
        pointsValue.setForeground(UIConstants.ACCENT_GOLD);
        pointsValue.setAlignmentX(Component.RIGHT_ALIGNMENT);
        // Add tooltip with exact points
        pointsValue.setToolTipText(String.valueOf(exactPoints));

        JLabel pointsLabel = new JLabel("Reval Points");
        pointsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        pointsLabel.setForeground(UIConstants.TEXT_SECONDARY);
        pointsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        pointsPanel.add(pointsValue);
        pointsPanel.add(pointsLabel);

        header.add(leftPanel, BorderLayout.CENTER);
        header.add(pointsPanel, BorderLayout.EAST);

        return wrapInRoundedPanel(header);
    }

    private JPanel buildRankProgressBar(AccountResponse.OsrsAccount account) {
        if (ranks == null || ranks.isEmpty()) return null;

        int currentPoints = account.getActivityPoints() != null ? account.getActivityPoints() : 0;
        String currentRank = account.getClanRank();

        // Find current and next rank
        PointsResponse.Rank nextRank = null;
        int previousRankPoints = 0;

        // Sort ranks by points required
        List<PointsResponse.Rank> sortedRanks = new ArrayList<>(ranks);
        sortedRanks.sort((a, b) -> Integer.compare(a.getPointsRequired(), b.getPointsRequired()));

        for (int i = 0; i < sortedRanks.size(); i++) {
            PointsResponse.Rank rank = sortedRanks.get(i);
            if (rank.getName().equalsIgnoreCase(currentRank) ||
                (rank.getDisplayName() != null && rank.getDisplayName().equalsIgnoreCase(currentRank))) {
                previousRankPoints = rank.getPointsRequired();
                if (i + 1 < sortedRanks.size()) {
                    nextRank = sortedRanks.get(i + 1);
                }
                break;
            }
        }

        // If we couldn't find current rank, find first rank above current points
        if (nextRank == null) {
            for (PointsResponse.Rank rank : sortedRanks) {
                if (rank.getPointsRequired() > currentPoints) {
                    nextRank = rank;
                    break;
                }
                previousRankPoints = rank.getPointsRequired();
            }
        }

        if (nextRank == null) {
            // Player is at max rank
            JPanel maxRank = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            maxRank.setOpaque(false);
            JLabel maxLabel = new JLabel("Max Rank!");
            maxLabel.setFont(new Font("Segoe UI", Font.ITALIC, 9));
            maxLabel.setForeground(UIConstants.ACCENT_GOLD);
            maxRank.add(maxLabel);
            return maxRank;
        }

        // Calculate progress
        int pointsNeeded = nextRank.getPointsRequired() - previousRankPoints;
        int pointsProgress = currentPoints - previousRankPoints;
        double progress = pointsNeeded > 0 ? (double) pointsProgress / pointsNeeded : 0;
        progress = Math.min(1.0, Math.max(0.0, progress));

        int pointsRemaining = nextRank.getPointsRequired() - currentPoints;
        boolean needsRankUp = pointsRemaining < 0;

        JPanel progressPanel = new JPanel();
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressPanel.setOpaque(false);

        // Label row with info icon if needed
        JPanel labelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        labelRow.setOpaque(false);
        labelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        String nextRankName = nextRank.getDisplayName() != null ? nextRank.getDisplayName() : nextRank.getName();
        JLabel progressLabel = new JLabel(pointsRemaining + " pts to " + formatRank(nextRankName));
        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        progressLabel.setForeground(UIConstants.TEXT_SECONDARY);
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        labelRow.add(progressLabel);
        
        // Info icon if points are negative (needs rank up) - placed to the right of the label
        if (needsRankUp) {
            ImageIcon infoIcon = getInfoIcon();
            JLabel infoIconLabel;
            if (infoIcon != null) {
                infoIconLabel = new JLabel(infoIcon);
            } else {
                // Fallback to text if image loading failed
                infoIconLabel = new JLabel("ℹ");
                infoIconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                infoIconLabel.setForeground(UIConstants.TEXT_SECONDARY);
            }
            infoIconLabel.setToolTipText("Waiting for a staff member to give you the correct rank");
            infoIconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            infoIconLabel.setVerticalAlignment(SwingConstants.CENTER);
            labelRow.add(infoIconLabel);
        }

        // Progress bar
        final double finalProgress = progress;
        JPanel bar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int barHeight = 4;
                int barWidth = getWidth();

                // Background
                g2d.setColor(UIConstants.PROGRESS_BG);
                g2d.fillRoundRect(0, 0, barWidth, barHeight, barHeight, barHeight);

                // Progress
                if (finalProgress > 0) {
                    int fillWidth = (int) (barWidth * finalProgress);
                    g2d.setColor(UIConstants.ACCENT_GOLD);
                    g2d.fillRoundRect(0, 0, fillWidth, barHeight, barHeight, barHeight);
                }

                g2d.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(100, 4));
        bar.setMaximumSize(new Dimension(120, 4));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        progressPanel.add(labelRow);
        progressPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        progressPanel.add(bar);

        return progressPanel;
    }

    private JPanel buildPointsSection(AccountResponse.PointsBreakdown breakdown) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setBorder(new EmptyBorder(0, 0, 0, 0)); // Remove any default border/padding
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        // "Points from:" label header (using BorderLayout like DiaryPanel)
        JPanel labelHeader = new JPanel(new BorderLayout());
        labelHeader.setOpaque(false);
        labelHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        
        JLabel pointsFromLabel = new JLabel("Points from:");
        pointsFromLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        pointsFromLabel.setForeground(UIConstants.ACCENT_GOLD);
        
        labelHeader.add(pointsFromLabel, BorderLayout.WEST);
        section.add(labelHeader);
        section.add(Box.createRigidArea(new Dimension(0, 6)));

        // Top row: 3 boxes
        JPanel topRow = new JPanel(new GridLayout(1, 3, 4, 0));
        topRow.setOpaque(false);
        StatCard dropsCard = new StatCard(formatNumber(breakdown.getDrops()), "Drops", UIConstants.ACCENT_GREEN);
        dropsCard.addMouseListener(createPointsCardListener("drop", "Drops"));
        topRow.add(dropsCard);
        
        StatCard petsCard = new StatCard(formatNumber(breakdown.getPets()), "Pets", UIConstants.ACCENT_PURPLE);
        petsCard.addMouseListener(createPointsCardListener("pet", "Pets"));
        topRow.add(petsCard);
        
        StatCard milestonesCard = new StatCard(formatNumber(breakdown.getMilestones()), "Milestones", UIConstants.ACCENT_BLUE);
        milestonesCard.addMouseListener(createPointsCardListener("milestone", "Milestones"));
        topRow.add(milestonesCard);

        // Bottom row: 3 boxes (Diaries, Challenges, Events)
        JPanel bottomRow = new JPanel(new GridLayout(1, 3, 4, 0));
        bottomRow.setOpaque(false);
        StatCard diariesCard = new StatCard(formatNumber(breakdown.getRevalDiaries()), "Diaries", UIConstants.ACCENT_GOLD);
        diariesCard.addMouseListener(createPointsCardListener("revalDiaries", "Diaries"));
        bottomRow.add(diariesCard);
        
        StatCard challengesCard = new StatCard(formatNumber(breakdown.getRevalChallenges()), "Challenges", UIConstants.ACCENT_GREEN);
        challengesCard.addMouseListener(createPointsCardListener("revalChallenges", "Challenges"));
        bottomRow.add(challengesCard);
        
        StatCard eventsCard = new StatCard(formatNumber(breakdown.getEvents()), "Events", UIConstants.ACCENT_BLUE);
        eventsCard.addMouseListener(createPointsCardListener("event", "Events"));
        bottomRow.add(eventsCard);

        section.add(topRow);
        section.add(Box.createRigidArea(new Dimension(0, 4)));
        section.add(bottomRow);

        return section;
    }

    private JPanel buildStatsSection(AccountResponse.OsrsAccount account) {
        JPanel section = new JPanel(new GridLayout(1, 2, 4, 4));
        section.setOpaque(false);

        section.add(new StatCard(formatDecimal(account.getEhp() != null ? account.getEhp() : 0.0), "EHP", UIConstants.ACCENT_GREEN));
        section.add(new StatCard(formatDecimal(account.getEhb() != null ? account.getEhb() : 0.0), "EHB", UIConstants.ACCENT_BLUE));

        return section;
    }


    private JPanel buildMilestonesSection(List<AccountResponse.Milestone> completedMilestones) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UIConstants.CARD_BG);
        wrapper.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel title = new JLabel("Milestones");
        title.setFont(new Font("Segoe UI", Font.BOLD, 11));
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel milestonesList = new JPanel();
        milestonesList.setLayout(new BoxLayout(milestonesList, BoxLayout.Y_AXIS));
        milestonesList.setOpaque(false);

        // Get all milestone definitions from points endpoint
        List<PointsResponse.PointSource> allMilestoneDefinitions = new ArrayList<>();
        if (pointsData != null && pointsData.getPointSources() != null) {
            List<PointsResponse.PointSource> milestoneSources = pointsData.getPointSources().get("MILESTONES");
            if (milestoneSources != null) {
                allMilestoneDefinitions = milestoneSources;
            }
        }

        // Create a map of completed milestones by their type/id for quick lookup
        Map<String, AccountResponse.Milestone> completedMap = new HashMap<>();
        if (completedMilestones != null) {
            for (AccountResponse.Milestone milestone : completedMilestones) {
                // Use type as key (assuming type matches the id from pointSources)
                if (milestone.getType() != null) {
                    completedMap.put(milestone.getType(), milestone);
                }
            }
        }

        // Display all milestone definitions, marking completed ones
        if (allMilestoneDefinitions.isEmpty()) {
            // Fallback: show only completed milestones if points data not available
            if (completedMilestones != null) {
                for (AccountResponse.Milestone milestone : completedMilestones) {
                    milestonesList.add(createMilestoneRow(milestone, null));
                    milestonesList.add(Box.createRigidArea(new Dimension(0, 4)));
                }
            }
        } else {
            // Show all milestone definitions
            for (PointsResponse.PointSource milestoneDef : allMilestoneDefinitions) {
                AccountResponse.Milestone completedMilestone = completedMap.get(milestoneDef.getId());
                milestonesList.add(createMilestoneRow(completedMilestone, milestoneDef));
                milestonesList.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }

        wrapper.add(title, BorderLayout.NORTH);
        wrapper.add(milestonesList, BorderLayout.CENTER);

        return wrapInRoundedPanel(wrapper);
    }

    private JPanel createMilestoneRow(AccountResponse.Milestone completedMilestone, PointsResponse.PointSource milestoneDef) {
        // Determine if completed and get description/points
        boolean isCompleted = completedMilestone != null && 
                               completedMilestone.getAchievedAt() != null && 
                               !completedMilestone.getAchievedAt().isEmpty();
        
        String description;
        Integer pointsAwarded = null;
        
        if (milestoneDef != null) {
            description = milestoneDef.getDescription() != null ? milestoneDef.getDescription() : milestoneDef.getName();
            if (isCompleted && completedMilestone.getPointsAwarded() != null) {
                pointsAwarded = completedMilestone.getPointsAwarded();
            } else {
                pointsAwarded = milestoneDef.getPointsValue();
            }
        } else if (completedMilestone != null) {
            description = completedMilestone.getDescription();
            pointsAwarded = completedMilestone.getPointsAwarded();
        } else {
            description = "Unknown milestone";
        }
        
        return new ChecklistItem(description, isCompleted, pointsAwarded);
    }

    private JPanel buildCombatAchievementsSection() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UIConstants.CARD_BG);
        wrapper.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel title = new JLabel("Combat Achievements");
        title.setFont(new Font("Segoe UI", Font.BOLD, 11));
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel tiersList = new JPanel();
        tiersList.setLayout(new BoxLayout(tiersList, BoxLayout.Y_AXIS));
        tiersList.setOpaque(false);

        // Get current progress from account
        Integer currentProgress = currentAccount != null ? currentAccount.getCombatAchievementPoints() : null;
        if (currentProgress == null) {
            currentProgress = 0;
        }

        // Get tier definitions from points endpoint
        List<PointsResponse.PointSource> tierDefinitions = new ArrayList<>();
        if (pointsData != null && pointsData.getPointSources() != null) {
            List<PointsResponse.PointSource> combatAchievementSources = pointsData.getPointSources().get("COMBAT_ACHIEVEMENTS");
            if (combatAchievementSources != null) {
                tierDefinitions = combatAchievementSources;
                // Sort by threshold ascending
                tierDefinitions.sort((a, b) -> {
                    Integer thresholdA = a.getThreshold() != null ? a.getThreshold() : 0;
                    Integer thresholdB = b.getThreshold() != null ? b.getThreshold() : 0;
                    return Integer.compare(thresholdA, thresholdB);
                });
            }
        }

        // Display all tier definitions
        if (tierDefinitions.isEmpty()) {
            // Show empty state if no tiers available
            JLabel emptyLabel = new JLabel("No combat achievement tiers available");
            emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            emptyLabel.setForeground(UIConstants.TEXT_SECONDARY);
            tiersList.add(emptyLabel);
        } else {
            for (PointsResponse.PointSource tierDef : tierDefinitions) {
                boolean isCompleted = tierDef.getThreshold() != null && currentProgress >= tierDef.getThreshold();
                tiersList.add(createTierRow(tierDef, isCompleted));
                tiersList.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }

        wrapper.add(title, BorderLayout.NORTH);
        wrapper.add(tiersList, BorderLayout.CENTER);

        return wrapInRoundedPanel(wrapper);
    }

    private JPanel buildCollectionLogSection() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UIConstants.CARD_BG);
        wrapper.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel title = new JLabel("Collection Log");
        title.setFont(new Font("Segoe UI", Font.BOLD, 11));
        title.setForeground(UIConstants.TEXT_PRIMARY);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel tiersList = new JPanel();
        tiersList.setLayout(new BoxLayout(tiersList, BoxLayout.Y_AXIS));
        tiersList.setOpaque(false);

        // Get current progress from account
        Integer currentProgress = currentAccount != null ? currentAccount.getCollectionLogUniqueObtained() : null;
        if (currentProgress == null) {
            currentProgress = 0;
        }

        // Get tier definitions from points endpoint
        List<PointsResponse.PointSource> tierDefinitions = new ArrayList<>();
        if (pointsData != null && pointsData.getPointSources() != null) {
            List<PointsResponse.PointSource> collectionLogSources = pointsData.getPointSources().get("COLLECTION_LOG");
            if (collectionLogSources != null) {
                tierDefinitions = collectionLogSources;
                // Sort by threshold ascending
                tierDefinitions.sort((a, b) -> {
                    Integer thresholdA = a.getThreshold() != null ? a.getThreshold() : 0;
                    Integer thresholdB = b.getThreshold() != null ? b.getThreshold() : 0;
                    return Integer.compare(thresholdA, thresholdB);
                });
            }
        }

        // Display all tier definitions
        if (tierDefinitions.isEmpty()) {
            // Show empty state if no tiers available
            JLabel emptyLabel = new JLabel("No collection log tiers available");
            emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            emptyLabel.setForeground(UIConstants.TEXT_SECONDARY);
            tiersList.add(emptyLabel);
        } else {
            for (PointsResponse.PointSource tierDef : tierDefinitions) {
                boolean isCompleted = tierDef.getThreshold() != null && currentProgress >= tierDef.getThreshold();
                tiersList.add(createTierRow(tierDef, isCompleted));
                tiersList.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }

        wrapper.add(title, BorderLayout.NORTH);
        wrapper.add(tiersList, BorderLayout.CENTER);

        return wrapInRoundedPanel(wrapper);
    }

    private JPanel createTierRow(PointsResponse.PointSource tierDef, boolean isCompleted) {
        String description = tierDef.getDescription() != null ? tierDef.getDescription() : tierDef.getName();
        return new ChecklistItem(description, isCompleted, tierDef.getPointsValue());
    }

    // === HELPER METHODS ===

    private JPanel createCenteredPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UIConstants.BACKGROUND);
        return panel;
    }

    private JPanel wrapInRoundedPanel(JPanel content) {
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(UIConstants.CARD_BG);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2d.dispose();
            }
        };
        wrapper.setOpaque(false);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private void revalidateAndRepaint() {
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private String formatNumber(long num) {
        if (num >= 1_000_000) return new DecimalFormat("#.#M").format(num / 1_000_000.0);
        if (num >= 1_000) return new DecimalFormat("#.#K").format(num / 1_000.0);
        return String.valueOf(num);
    }

    private String formatDecimal(double num) {
        return new DecimalFormat("#,##0.0").format(num);
    }

    private String formatRank(String rank) {
        if (rank == null) return "Member";
        return rank.substring(0, 1).toUpperCase() + rank.substring(1).toLowerCase().replace("_", " ");
    }
    
    /**
     * Load and return the info icon, caching it for reuse
     */
    private ImageIcon getInfoIcon() {
        if (assetLoader == null) {
            return null;
        }
        return assetLoader.getIcon("info.png", 12);
    }

    /**
     * Create a mouse listener for clickable points cards
     */
    private java.awt.event.MouseListener createPointsCardListener(String sourceType, String title) {
        return new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showPointsBreakdown(sourceType, title);
            }
            
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                ((JPanel) e.getSource()).setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                ((JPanel) e.getSource()).setCursor(Cursor.getDefaultCursor());
            }
        };
    }
    
    /**
     * Show detailed points breakdown for a specific source type
     */
    private void showPointsBreakdown(String sourceType, String title) {
        if (pointsLog == null || pointsLog.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No points log data available.", 
                "Points Breakdown", 
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        List<AccountResponse.PointsLogEntry> filteredEntries = new ArrayList<>();
        
        if (sourceType.equals("revalDiaries")) {
            // Filter by sourceType "reval_diary"
            for (AccountResponse.PointsLogEntry entry : pointsLog) {
                if (entry.getSourceType() != null && entry.getSourceType().equalsIgnoreCase("reval_diary")) {
                    filteredEntries.add(entry);
                }
            }
        } else if (sourceType.equals("revalChallenges")) {
            // Filter by sourceType "reval_challenge"
            for (AccountResponse.PointsLogEntry entry : pointsLog) {
                if (entry.getSourceType() != null && entry.getSourceType().equalsIgnoreCase("reval_challenge")) {
                    filteredEntries.add(entry);
                }
            }
        } else {
            // Filter by exact sourceType match
            for (AccountResponse.PointsLogEntry entry : pointsLog) {
                if (entry.getSourceType() != null && entry.getSourceType().equalsIgnoreCase(sourceType)) {
                    filteredEntries.add(entry);
                }
            }
        }
        
        // Sort by date (newest first)
        filteredEntries.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        
        // Create and show breakdown panel
        PointsBreakdownPanel breakdownPanel = new PointsBreakdownPanel(title, filteredEntries);
        breakdownPanel.setVisible(true);
    }
    
    private class StatCard extends JPanel {
        private boolean isHovered = false;
        
        public StatCard(String value, String label, Color accentColor) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(UIConstants.CARD_BG);
            setBorder(new EmptyBorder(8, 8, 8, 8));
            setOpaque(false);

            JLabel valueLabel = new JLabel(value);
            valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            valueLabel.setForeground(accentColor != null ? accentColor : UIConstants.TEXT_PRIMARY);
            valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel nameLabel = new JLabel(label);
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            nameLabel.setForeground(UIConstants.TEXT_SECONDARY);
            nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            add(valueLabel);
            add(nameLabel);
            
            // Add hover effect listener
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            Color bg = isHovered ? new Color(UIConstants.CARD_BG.getRed() + 5, UIConstants.CARD_BG.getGreen() + 5, UIConstants.CARD_BG.getBlue() + 5) : UIConstants.CARD_BG;
            g2d.setColor(bg);
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            
            g2d.dispose();
        }
    }
}

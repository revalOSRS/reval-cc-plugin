package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.diaries.DiariesResponse;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.EmptyState;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanValidator;
import com.revalclan.util.UIAssetLoader;
import net.runelite.api.Client;
import net.runelite.api.GameState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import java.util.Map;

/**
 * Clan Diaries panel - Custom achievement diary with tiered tasks
 * Features navigation between diary list and individual diary detail views
 */
public class DiaryPanel extends JPanel implements Scrollable {
    
    private RevalApiService apiService;
    private Client client;
    private UIAssetLoader assetLoader;
    
    private final JPanel contentPanel;
    private final CardLayout cardLayout;
    private final JPanel mainContainer;
    
    // Store all diaries from API
    private List<DiariesResponse.Diary> allDiaries = new ArrayList<>();
    private DiariesResponse.Diary selectedDiary = null;
    
    // Track expanded tier states (diaryId + tierIndex)
    private Set<String> expandedTiers = new HashSet<>();
    
    private JLabel statusLabel;
    
    public DiaryPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);
        
        // Use CardLayout for navigation
        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);
        mainContainer.setBackground(UIConstants.BACKGROUND);
        
        // Create scrollable content for list view
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIConstants.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        // Status label
        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusLabel.setForeground(UIConstants.TEXT_SECONDARY);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setVisible(false);
        
        // Wrapper to constrain width
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
        
        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.setBackground(UIConstants.BACKGROUND);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);

        
        // Add list view to card layout
        mainContainer.add(scrollPane, "LIST");
        
        add(mainContainer, BorderLayout.CENTER);
        
        // Build the UI
        buildUI();
    }
    
    public void init(RevalApiService apiService, Client client, UIAssetLoader assetLoader) {
        this.apiService = apiService;
        this.client = client;
        this.assetLoader = assetLoader;
        showNotLoggedIn();
    }
    
    /**
     * Called when the player is logged in - refresh data
     */
    public void onLoggedIn() {
        checkAuthorizationAndLoad();
    }
    
    /**
     * Called when the player logs out - show login message
     */
    public void onLoggedOut() {
        SwingUtilities.invokeLater(() -> showNotLoggedIn());
    }
    
    /**
     * Check authorization and load data, with retry if clan channel not ready
     */
    private void checkAuthorizationAndLoad() {
        if (client == null) {
            showNotLoggedIn();
            return;
        }
        
        GameState gameState = client.getGameState();
        if (gameState != GameState.LOGGED_IN) {
            showNotLoggedIn();
            return;
        }
        
        // Check if account hash is valid
        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            showNotLoggedIn();
            return;
        }
        
        // Check clan membership with retry if clan channel not ready
        boolean isInClan = ClanValidator.validateClan(client);
        
        if (isInClan) {
            // Authorized - load data
            loadData();
        } else {
            // Clan channel might not be ready yet - retry multiple times with increasing delays
            scheduleRetryWithBackoff(1, 10);
        }
    }
    
    /**
     * Schedule retry with exponential backoff, up to maxAttempts attempts
     */
    private void scheduleRetryWithBackoff(int attempt, int maxAttempts) {
        if (attempt > maxAttempts) {
            showNotLoggedIn();
            return;
        }
        
        int delayMs = attempt * 1000; // 1s, 2s, 3s, etc.
        
        javax.swing.Timer retryTimer = new javax.swing.Timer(delayMs, e -> {
            boolean retryIsInClan = ClanValidator.validateClan(client);
            
            if (retryIsInClan) {
                // Now authorized - load data
                loadData();
            } else {
                // Schedule next retry
                scheduleRetryWithBackoff(attempt + 1, maxAttempts);
            }
        });
        retryTimer.setRepeats(false);
        retryTimer.start();
    }
    
    /**
     * Force refresh data
     */
    public void refresh() {
        loadData();
    }
    
    private void loadData() {
        if (apiService == null) {
            showError("API service not initialized");
            return;
        }
        
        showLoading();
        
        // Get account hash to include progress data
        Long accountHash = null;
        if (client != null) {
            long hash = client.getAccountHash();
            if (hash != -1) {
                accountHash = hash;
            }
        }
        
        apiService.fetchDiaries(accountHash,
            response -> SwingUtilities.invokeLater(() -> {
                if (response != null && response.getData() != null && response.getData().getDiaries() != null) {
                    allDiaries = response.getData().getDiaries();
                } else {
                    allDiaries = new ArrayList<>();
                }

                buildUI();
                
                // If we're on detail view, refresh it with updated data
                if (selectedDiary != null) {
                    // Find the updated diary by ID
                    DiariesResponse.Diary updatedDiary = allDiaries.stream()
                        .filter(d -> d.getId() != null && d.getId().equals(selectedDiary.getId()))
                        .findFirst()
                        .orElse(null);
                    if (updatedDiary != null) {
                        showDiaryDetail(updatedDiary);
                    }
                }
            }),
            error -> SwingUtilities.invokeLater(() -> {
                allDiaries = new ArrayList<>();
                showError("Failed to load diaries: " + error.getMessage());
                buildUI();
            })
        );
    }
    
    private void showLoading() {
        statusLabel.setText("Loading diaries...");
        statusLabel.setVisible(true);
    }
    
    private void showError(String message) {
        statusLabel.setText("Error: " + message);
        statusLabel.setVisible(true);
    }
    
    private void buildUI() {
        contentPanel.removeAll();
        
        // Header
        JPanel header = createHeader();
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(header);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        
        // Status label
        if (statusLabel.isVisible() && !allDiaries.isEmpty()) {
            statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(statusLabel);
            contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        
        if (allDiaries.isEmpty()) {
            statusLabel.setVisible(false);
            showEmptyState();
            revalidate();
            repaint();
            return;
        }
        
        statusLabel.setVisible(false);
        
        // Diary cards in a grid-like layout
        JPanel diaryGrid = new JPanel();
        diaryGrid.setLayout(new BoxLayout(diaryGrid, BoxLayout.Y_AXIS));
        diaryGrid.setOpaque(false);
        diaryGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        for (int i = 0; i < allDiaries.size(); i++) {
            DiariesResponse.Diary diary = allDiaries.get(i);
            JPanel diaryCard = createDiaryListCard(diary);
            diaryCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            diaryGrid.add(diaryCard);
            
            if (i < allDiaries.size() - 1) {
                diaryGrid.add(Box.createRigidArea(new Dimension(0, 12)));
            }
        }
        
        contentPanel.add(diaryGrid);
        contentPanel.add(Box.createVerticalGlue());
        revalidate();
        repaint();
    }
    
    private void showDiaryDetail(DiariesResponse.Diary diary) {
        selectedDiary = diary;
        
        // Create detail view panel
        JPanel detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setBackground(UIConstants.BACKGROUND);
        detailPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        // Header with back button above, then title and refresh button
        JPanel headerContainer = new JPanel();
        headerContainer.setLayout(new BoxLayout(headerContainer, BoxLayout.Y_AXIS));
        headerContainer.setBackground(UIConstants.BACKGROUND);
        headerContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Back button
        BackButton backButton = new BackButton(() -> {
            cardLayout.show(mainContainer, "LIST");
            selectedDiary = null;
        });
        
        // Title and refresh button row
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(UIConstants.BACKGROUND);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Center: Diary title
        JLabel diaryTitle = new JLabel(diary.getName());
        diaryTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        diaryTitle.setForeground(UIConstants.ACCENT_GOLD);
        
        // Right: Refresh button (white text with hover background, matching Events panel)
        RefreshButton refreshButton = new RefreshButton(Color.WHITE);
        refreshButton.setRefreshAction(this::loadData);
        
        titleRow.add(diaryTitle, BorderLayout.CENTER);
        titleRow.add(refreshButton, BorderLayout.EAST);
        
        headerContainer.add(backButton);
        headerContainer.add(Box.createRigidArea(new Dimension(0, 8)));
        headerContainer.add(titleRow);
        
        detailPanel.add(headerContainer);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        
        // Diary stats
        JPanel statsPanel = createDiaryStats(diary);
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPanel.add(statsPanel);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 16)));
        
        // Tiers and tasks
        if (diary.getTiers() != null && !diary.getTiers().isEmpty()) {
            for (int i = 0; i < diary.getTiers().size(); i++) {
                final DiariesResponse.Diary.DiaryTier apiTier = diary.getTiers().get(i);
                DiaryTier tier = parseTier(apiTier.getTier());
                if (tier == null) continue;
                
                List<DiariesResponse.Diary.DiaryTask> tasks = apiTier.getTasks() != null ? apiTier.getTasks() : new ArrayList<>();
                
                // Filter out hidden/inactive tasks
                List<DiariesResponse.Diary.DiaryTask> activeTasks = new ArrayList<>();
                for (DiariesResponse.Diary.DiaryTask task : tasks) {
                    if (!task.isHidden() && task.isActive()) {
                        activeTasks.add(task);
                    }
                }
                
                boolean isLocked = false; // All tiers are always unlocked
                
                // Tier section with collapsible tasks
                JPanel tierSection = new JPanel();
                tierSection.setLayout(new BoxLayout(tierSection, BoxLayout.Y_AXIS));
                tierSection.setOpaque(false);
                tierSection.setAlignmentX(Component.LEFT_ALIGNMENT);
                
                // Tier header (clickable to expand/collapse)
                // Check if this tier was previously expanded
                String tierKey = diary.getId() + "_" + i;
                final boolean[] tierExpanded = {expandedTiers.contains(tierKey)};
                final JPanel[] tierHeaderRef = {createTierHeaderForDetail(tier, apiTier, activeTasks, isLocked, tierExpanded[0])};
                tierHeaderRef[0].setAlignmentX(Component.LEFT_ALIGNMENT);
                tierHeaderRef[0].setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                
                // Tasks container (hidden by default, or visible if previously expanded)
                JPanel tasksContainer = new JPanel();
                tasksContainer.setLayout(new BoxLayout(tasksContainer, BoxLayout.Y_AXIS));
                tasksContainer.setOpaque(false);
                tasksContainer.setBorder(new EmptyBorder(8, 0, 0, 0));
                tasksContainer.setVisible(tierExpanded[0]);
                tasksContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
                
                if (activeTasks.isEmpty()) {
                    JLabel emptyLabel = new JLabel("No tasks in this tier");
                    emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
                    emptyLabel.setForeground(UIConstants.TEXT_MUTED);
                    emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    emptyLabel.setBorder(new EmptyBorder(4, 12, 4, 12));
                    tasksContainer.add(emptyLabel);
                } else {
                    for (DiariesResponse.Diary.DiaryTask task : activeTasks) {
                        JPanel taskCard = createTaskLineCard(task, tier.color, isLocked);
                        taskCard.setAlignmentX(Component.LEFT_ALIGNMENT);
                        tasksContainer.add(taskCard);
                        tasksContainer.add(Box.createRigidArea(new Dimension(0, 6)));
                    }
                }
                
                tierSection.add(tierHeaderRef[0]);
                tierSection.add(tasksContainer);
                
                // Toggle on tier header click
                final String finalTierKey = tierKey;
                tierHeaderRef[0].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        tierExpanded[0] = !tierExpanded[0];
                        tasksContainer.setVisible(tierExpanded[0]);
                        
                        // Update expanded state tracking
                        if (tierExpanded[0]) {
                            expandedTiers.add(finalTierKey);
                        } else {
                            expandedTiers.remove(finalTierKey);
                        }
                        
                        // Update header arrow
                        tierSection.remove(tierHeaderRef[0]);
                        tierHeaderRef[0] = createTierHeaderForDetail(tier, apiTier, activeTasks, isLocked, tierExpanded[0]);
                        tierHeaderRef[0].setAlignmentX(Component.LEFT_ALIGNMENT);
                        tierHeaderRef[0].setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        tierHeaderRef[0].addMouseListener(this);
                        tierSection.add(tierHeaderRef[0], 0);
                        
                        tierSection.revalidate();
                        tierSection.repaint();
                    }
                });
                
                detailPanel.add(tierSection);
                if (i < diary.getTiers().size() - 1) {
                    detailPanel.add(Box.createRigidArea(new Dimension(0, 8)));
                }
            }
        } else {
            JLabel emptyLabel = new JLabel("No tiers available");
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            emptyLabel.setForeground(UIConstants.TEXT_MUTED);
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            emptyLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
            detailPanel.add(emptyLabel);
        }
        
        detailPanel.add(Box.createVerticalGlue());
        
        // Wrap detail panel in scroll pane
        JPanel detailWrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                if (getParent() != null) {
                    size.width = getParent().getWidth();
                }
                return size;
            }
        };
        detailWrapper.setBackground(UIConstants.BACKGROUND);
        detailWrapper.add(detailPanel, BorderLayout.NORTH);
        
        JScrollPane detailScrollPane = new JScrollPane(detailWrapper);
        detailScrollPane.setBackground(UIConstants.BACKGROUND);
        detailScrollPane.setBorder(null);
        detailScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        detailScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        detailScrollPane.getViewport().setBackground(UIConstants.BACKGROUND);
        
        // Remove old detail view if exists
        Component[] components = mainContainer.getComponents();
        for (Component comp : components) {
            if (comp != mainContainer.getComponent(0)) { // Keep list view
                mainContainer.remove(comp);
            }
        }
        
        // Add new detail view
        mainContainer.add(detailScrollPane, "DETAIL");
        cardLayout.show(mainContainer, "DETAIL");
    }
    
    private JPanel createDiaryListCard(DiariesResponse.Diary diary) {
        // Use final array for hover state
        final boolean[] isHovered = {false};
        
        JPanel card = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(isHovered[0] ? UIConstants.CARD_HOVER : UIConstants.CARD_BG);
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                
                // Category color accent
                Color categoryColor = getCategoryColor(diary.getCategory());
                g2d.setColor(categoryColor);
                g2d.fillRoundRect(0, 4, 4, getHeight() - 8, 2, 2);
                
                g2d.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // Left: Diary name and category
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        
        JLabel diaryName = new JLabel(diary.getName());
        diaryName.setFont(new Font("Segoe UI", Font.BOLD, 14));
        diaryName.setForeground(UIConstants.TEXT_PRIMARY);
        diaryName.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel categoryLabel = new JLabel(formatCategoryName(diary.getCategory()));
        categoryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        categoryLabel.setForeground(UIConstants.TEXT_SECONDARY);
        categoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        leftPanel.add(diaryName);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        leftPanel.add(categoryLabel);
        
        // Right: Stats and arrow
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);
        
        // Calculate diary stats
        int totalTasks = 0;
        int completedTasks = 0;
        if (diary.getTiers() != null) {
            for (DiariesResponse.Diary.DiaryTier tier : diary.getTiers()) {
                if (tier.getTasks() != null) {
                    for (DiariesResponse.Diary.DiaryTask task : tier.getTasks()) {
                        if (!task.isHidden() && task.isActive()) {
                            totalTasks++;
                            if (task.getProgress() != null && task.getProgress().isCompleted()) {
                                completedTasks++;
                            }
                        }
                    }
                }
            }
        }
        
        JLabel statsLabel = new JLabel(completedTasks + "/" + totalTasks + " tasks");
        statsLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statsLabel.setForeground(UIConstants.ACCENT_GOLD);
        statsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        
        JLabel arrowLabel = new JLabel("→");
        arrowLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        arrowLabel.setForeground(UIConstants.TEXT_SECONDARY);
        arrowLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        
        rightPanel.add(statsLabel);
        rightPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        rightPanel.add(arrowLabel);
        
        card.add(leftPanel, BorderLayout.WEST);
        card.add(rightPanel, BorderLayout.EAST);
        
        // Hover effect
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered[0] = true;
                card.repaint();
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                isHovered[0] = false;
                card.repaint();
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                showDiaryDetail(diary);
            }
        });
        
        return card;
    }
    
    private JPanel createDiaryStats(DiariesResponse.Diary diary) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UIConstants.CARD_BG);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        int totalTasks = 0;
        int completedTasks = 0;
        int totalTierPoints = 0; // fullCompletionBonus + sum of all tier completionBonus
        int earnedTierPoints = 0; // Sum of earned tier points
        
        // Add fullCompletionBonus to total
        int fullCompletionBonus = diary.getFullCompletionBonus();
        totalTierPoints += fullCompletionBonus;
        
        if (diary.getTiers() != null) {
            for (DiariesResponse.Diary.DiaryTier tier : diary.getTiers()) {
                // Add tier completionBonus to total
                int completionBonus = tier.getCompletionBonus();
                totalTierPoints += completionBonus;
                
                // Check if tier is complete
                boolean tierComplete = false;
                if (tier.getTasks() != null) {
                    int tierTotalTasks = 0;
                    int tierCompletedTasks = 0;
                    for (DiariesResponse.Diary.DiaryTask task : tier.getTasks()) {
                        if (!task.isHidden() && task.isActive()) {
                            totalTasks++;
                            tierTotalTasks++;
                            if (task.getProgress() != null && task.getProgress().isCompleted()) {
                                completedTasks++;
                                tierCompletedTasks++;
                            }
                        }
                    }
                    tierComplete = tierTotalTasks > 0 && tierCompletedTasks == tierTotalTasks;
                }
                
                // If tier is complete, add its points to earned
                if (tierComplete) {
                    Integer pointsEarned = tier.getPointsEarned();
                    if (pointsEarned != null) {
                        earnedTierPoints += pointsEarned;
                    } else {
                        earnedTierPoints += completionBonus;
                    }
                }
            }
        }
        
        // Add fullCompletionBonus to earned if all tasks are complete
        if (totalTasks > 0 && completedTasks == totalTasks) {
            earnedTierPoints += fullCompletionBonus;
        }
        
        double progress = totalTasks > 0 ? (double) completedTasks / totalTasks : 0;
        
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        
        JLabel progressLabel = new JLabel("Progress");
        progressLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        progressLabel.setForeground(UIConstants.TEXT_PRIMARY);
        
        JLabel statsLabel = new JLabel(completedTasks + "/" + totalTasks + " tasks • " + earnedTierPoints + "/" + totalTierPoints + " pts");
        statsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        statsLabel.setForeground(UIConstants.TEXT_SECONDARY);
        
        headerRow.add(progressLabel, BorderLayout.WEST);
        headerRow.add(statsLabel, BorderLayout.EAST);
        
        final double finalProgress = progress;
        JPanel progressBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int barHeight = 8;
                int y = (getHeight() - barHeight) / 2;
                
                g2d.setColor(UIConstants.PROGRESS_BG);
                g2d.fillRoundRect(0, y, getWidth(), barHeight, barHeight, barHeight);
                
                if (finalProgress > 0) {
                    int fillWidth = (int) (getWidth() * finalProgress);
                    // Green if all tasks complete (progress == 1.0), yellow otherwise
                    Color progressColor = finalProgress >= 1.0 ? UIConstants.SUCCESS_COLOR : UIConstants.TIER_MEDIUM;
                    g2d.setColor(progressColor);
                    g2d.fillRoundRect(0, y, fillWidth, barHeight, barHeight, barHeight);
                }
                
                g2d.dispose();
            }
        };
        progressBar.setOpaque(false);
        progressBar.setPreferredSize(new Dimension(0, 16));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        
        panel.add(headerRow);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(progressBar);
        
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(UIConstants.CARD_BG);
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2d.dispose();
            }
        };
        wrapper.setOpaque(false);
        wrapper.add(panel, BorderLayout.CENTER);
        return wrapper;
    }
    
    private JPanel createTierHeaderForDetail(DiaryTier tier, DiariesResponse.Diary.DiaryTier apiTier, List<DiariesResponse.Diary.DiaryTask> tasks, boolean isLocked, boolean isExpanded) {
        long completed = tasks.stream()
            .filter(t -> t.getProgress() != null && t.getProgress().isCompleted())
            .count();
        int total = tasks.size();
        boolean tierComplete = total > 0 && completed == total;
        
        // Get tier points (completionBonus only)
        int completionBonus = apiTier.getCompletionBonus();
        
        JPanel header = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                Color bg = isLocked ? UIConstants.CARD_BG : (tierComplete ? UIConstants.TASK_COMPLETE : UIConstants.CARD_BG);
                g2d.setColor(bg);
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
                
                g2d.setColor(isLocked ? UIConstants.TEXT_MUTED : tier.color);
                g2d.fillRoundRect(0, 4, 4, getHeight() - 8, 2, 2);
                
                g2d.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(12, 12, 12, 12));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel tierName = new JLabel(tier.displayName);
        tierName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tierName.setForeground(isLocked ? UIConstants.TEXT_MUTED : tier.color);
        
        if (isLocked) {
            JLabel lockIcon = new JLabel(" 🔒");
            lockIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 10));
            nameRow.add(tierName);
            nameRow.add(lockIcon);
        } else if (tierComplete) {
            // Use checkmark icon instead of text
            ImageIcon icon = getCheckmarkIcon();
            JLabel checkIcon;
            if (icon != null) {
                checkIcon = new JLabel(icon);
            } else {
                // Fallback to text if image loading failed
                checkIcon = new JLabel(" ✓");
                checkIcon.setFont(new Font("Segoe UI", Font.BOLD, 12));
                checkIcon.setForeground(UIConstants.TIER_EASY);
            }
            nameRow.add(tierName);
            // Add spacing between name and checkmark using an invisible spacer
            JLabel spacer = new JLabel(" ");
            spacer.setPreferredSize(new Dimension(6, 1));
            nameRow.add(spacer);
            nameRow.add(checkIcon);
        } else {
            nameRow.add(tierName);
        }
        
        // Show points: "X pts available" if not done, or "X pts earned" if done
        // Always use completionBonus, never pointsEarned
        String pointsText;
        if (tierComplete) {
            pointsText = completionBonus + " pts earned";
        } else {
            pointsText = completionBonus + " pts available";
        }
        
        JLabel progressText = new JLabel(completed + "/" + total + " tasks • " + pointsText);
        progressText.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        progressText.setForeground(isLocked ? UIConstants.TEXT_MUTED : UIConstants.TEXT_SECONDARY);
        progressText.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        leftPanel.add(nameRow);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        leftPanel.add(progressText);
        
        // Expand arrow
        JLabel expandArrow = new JLabel(isExpanded ? "▲" : "▼");
        expandArrow.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        expandArrow.setForeground(isLocked ? UIConstants.TEXT_MUTED : UIConstants.TEXT_SECONDARY);
        
        header.add(leftPanel, BorderLayout.WEST);
        header.add(expandArrow, BorderLayout.EAST);
        
        return header;
    }
    
    private JPanel createTaskLineCard(DiariesResponse.Diary.DiaryTask task, Color tierColor, boolean isLocked) {
        boolean isComplete = task.getProgress() != null && task.getProgress().isCompleted();
        
        // Extract progress from progressData
        int current = 0;
        int target = 1;
        int progressPercent = 0;
        double progress = 0.0;
        
        if (task.getProgress() != null) {
            // Try to get progress from progressData first
            Object progressDataObj = task.getProgress().getProgressData();
            boolean parsedFromProgressData = false;
            
            if (progressDataObj != null) {
                try {
                    // progressData is a Map<String, Object>
                    @SuppressWarnings("unchecked")
                    Map<String, Object> progressData = (Map<String, Object>) progressDataObj;
                    
                    Object currentObj = progressData.get("current");
                    Object targetObj = progressData.get("target");
                    
                    if (currentObj != null && currentObj instanceof Number) {
                        current = ((Number) currentObj).intValue();
                    }
                    if (targetObj != null && targetObj instanceof Number) {
                        target = ((Number) targetObj).intValue();
                    }
                    
                    if (target > 0) {
                        progress = Math.min(1.0, (double) current / target);
                        progressPercent = (int) (progress * 100);
                        parsedFromProgressData = true;
                    }
                } catch (Exception e) {
                    // Fallback to progressPercent
                }
            }
            
            // Fallback to progressPercent if we didn't successfully parse from progressData
            if (!parsedFromProgressData) {
                progressPercent = task.getProgress().getProgressPercent();
                progress = progressPercent / 100.0;
            }
        }
        
        JPanel card = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                Color bg = isLocked ? UIConstants.CARD_BG : (isComplete ? UIConstants.TASK_COMPLETE : UIConstants.CARD_BG);
                g2d.setColor(bg);
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
                
                // Green glow effect for completed tasks
                if (isComplete && !isLocked) {
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                    g2d.setColor(UIConstants.SUCCESS_COLOR);
                    g2d.setStroke(new BasicStroke(2));
                    g2d.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 6, 6));
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                }
                
                g2d.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(12, 12, 12, 12));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        
        // Main content
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setOpaque(false);
        
        // Title with status color
        JLabel titleLabel = new JLabel(task.getName());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        Color titleColor = isLocked ? UIConstants.TEXT_MUTED : (isComplete ? UIConstants.SUCCESS_COLOR : UIConstants.TIER_ELITE);
        titleLabel.setForeground(titleColor);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Description - use JTextArea for proper multiline wrapping
        String description = task.getDescription() != null ? task.getDescription() : "No description";
        JTextArea descArea = new JTextArea(description);
        descArea.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        descArea.setForeground(isLocked ? UIConstants.TEXT_MUTED : UIConstants.TEXT_SECONDARY);
        descArea.setBackground(isLocked ? UIConstants.CARD_BG : (isComplete ? UIConstants.TASK_COMPLETE : UIConstants.CARD_BG));
        descArea.setEditable(false);
        descArea.setFocusable(false);
        descArea.setWrapStyleWord(true);
        descArea.setLineWrap(true);
        descArea.setOpaque(false);
        descArea.setBorder(null);
        descArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Set preferred width to match card width
        descArea.setPreferredSize(new Dimension(200, descArea.getPreferredSize().height));
        descArea.setMaximumSize(new Dimension(200, Integer.MAX_VALUE));
        
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        mainPanel.add(descArea);
        
        // Progress bar (always shown)
        JPanel progressPanel = new JPanel(new BorderLayout(6, 0));
        progressPanel.setOpaque(false);
        progressPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        progressPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        final double finalProgress = progress;
        final int finalProgressPercent = progressPercent;
        final boolean finalIsComplete = isComplete;
        final int finalCurrent = current;
        final int finalTarget = target;
        JPanel barPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int barHeight = 6;
                int y = (getHeight() - barHeight) / 2;
                
                g2d.setColor(UIConstants.PROGRESS_BG);
                g2d.fillRoundRect(0, y, getWidth(), barHeight, barHeight, barHeight);
                
                if (!isLocked) {
                    int fillWidth = (int) (getWidth() * Math.min(1.0, Math.max(0.0, finalProgress)));
                    if (fillWidth > 0) {
                        Color barColor = finalIsComplete ? UIConstants.SUCCESS_COLOR : tierColor;
                        g2d.setColor(barColor);
                        g2d.fillRoundRect(0, y, fillWidth, barHeight, barHeight, barHeight);
                    }
                }
                
                g2d.dispose();
            }
        };
        barPanel.setOpaque(false);
        barPanel.setPreferredSize(new Dimension(0, 12));
        barPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));
        
        // Format progress text with current/target if available
        String progressText;
        if (finalIsComplete) {
            progressText = "Complete!";
        } else if (finalTarget > 1 && finalCurrent >= 0) {
            // Show current/target format
            if (finalTarget >= 1000000) {
                // Format millions
                double currentM = finalCurrent / 1000000.0;
                double targetM = finalTarget / 1000000.0;
                String currentStr = currentM == (int)currentM ? String.valueOf((int)currentM) : String.format("%.1f", currentM);
                String targetStr = targetM == (int)targetM ? String.valueOf((int)targetM) : String.format("%.1f", targetM);
                progressText = currentStr + "M/" + targetStr + "M";
            } else if (finalTarget >= 1000) {
                // Format thousands - only use K format for numbers >= 1000
                String currentStr;
                String targetStr;
                
                if (finalCurrent >= 1000) {
                    double currentK = finalCurrent / 1000.0;
                    currentStr = currentK == (int)currentK ? String.valueOf((int)currentK) : String.format("%.1f", currentK);
                    currentStr += "K";
                } else {
                    currentStr = String.valueOf(finalCurrent);
                }
                
                double targetK = finalTarget / 1000.0;
                targetStr = targetK == (int)targetK ? String.valueOf((int)targetK) : String.format("%.1f", targetK);
                targetStr += "K";
                
                progressText = currentStr + "/" + targetStr;
            } else {
                // Under 1K, show as regular numbers
                progressText = finalCurrent + "/" + finalTarget;
            }
        } else {
            progressText = finalProgressPercent + "%";
        }
        
        JLabel progressLabel = new JLabel(progressText);
        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 8));
        progressLabel.setForeground(isLocked ? UIConstants.TEXT_MUTED : (finalIsComplete ? UIConstants.SUCCESS_COLOR : UIConstants.TEXT_SECONDARY));
        
        progressPanel.add(barPanel, BorderLayout.CENTER);
        progressPanel.add(progressLabel, BorderLayout.EAST);
        
        mainPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        mainPanel.add(progressPanel);
        
        // Hint/Additional info (always shown if available)
        if (task.getHint() != null && !task.getHint().isEmpty()) {
            JLabel hintLabel = new JLabel("<html><body style='width: 200px; color: #969690;'><b>Hint:</b> " + 
                task.getHint() + "</body></html>");
            hintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 9));
            hintLabel.setForeground(UIConstants.TEXT_MUTED);
            hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            mainPanel.add(Box.createRigidArea(new Dimension(0, 4)));
            mainPanel.add(hintLabel);
        }
        
        // Right side: Points (only show if > 0)
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);
        
        int points = task.getPoints();
        if (points > 0) {
            JLabel pointsValue = new JLabel("+" + points);
            pointsValue.setFont(new Font("Segoe UI", Font.BOLD, 12));
            Color pointsColor = isLocked ? UIConstants.TEXT_MUTED : (isComplete ? UIConstants.SUCCESS_COLOR : UIConstants.ACCENT_GOLD);
            pointsValue.setForeground(pointsColor);
            pointsValue.setAlignmentX(Component.RIGHT_ALIGNMENT);
            
            JLabel pointsLabel = new JLabel("pts");
            pointsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 8));
            pointsLabel.setForeground(UIConstants.TEXT_MUTED);
            pointsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            
            rightPanel.add(pointsValue);
            rightPanel.add(pointsLabel);
        }
        // If points is 0, show nothing
        
        card.add(mainPanel, BorderLayout.CENTER);
        card.add(rightPanel, BorderLayout.EAST);
        
        return card;
    }
    
    /**
     * Load and return the checkmark icon, caching it for reuse
     */
    private ImageIcon getCheckmarkIcon() {
        if (assetLoader == null) {
            return null;
        }
        return assetLoader.getIcon("checkmark.png", 16);
    }
    
    private void showNotLoggedIn() {
        contentPanel.removeAll();
        contentPanel.add(new LoginPrompt("Clan Diaries"));
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private void showEmptyState() {
        contentPanel.add(new EmptyState("📋", "No diaries available"));
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                Dimension max = super.getMaximumSize();
                max.width = Integer.MAX_VALUE;
                return max;
            }
        };
        header.setBackground(UIConstants.BACKGROUND);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel title = new JLabel("CLAN DIARIES");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.ACCENT_GOLD);
        
        header.add(title, BorderLayout.WEST);
        
        return header;
    }
    
    
    private Color getCategoryColor(String category) {
        if (category == null) return UIConstants.ACCENT_GOLD;
        switch (category.toLowerCase()) {
            case "combat": return UIConstants.TYPE_COMBAT;
            case "skilling": return UIConstants.TYPE_SKILLING;
            case "collection": return UIConstants.TYPE_COLLECTION;
            case "social": return UIConstants.TYPE_SOCIAL;
            case "exploration": return UIConstants.TYPE_EXPLORATION;
            case "boss": return UIConstants.TYPE_BOSS;
            default: return UIConstants.ACCENT_GOLD;
        }
    }
    
    private String formatCategoryName(String category) {
        if (category == null || category.isEmpty()) return "Uncategorized";
        String formatted = category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase();
        return formatted.replace("_", " ");
    }
    
    // === Data Classes ===
    
    private enum DiaryTier {
        EASY("Easy", UIConstants.TIER_EASY),
        MEDIUM("Medium", UIConstants.TIER_MEDIUM),
        HARD("Hard", UIConstants.TIER_HARD),
        ELITE("Elite", UIConstants.TIER_ELITE),
        MASTER("Master", UIConstants.TIER_MASTER),
        GRANDMASTER("Grandmaster", UIConstants.TIER_GRANDMASTER);
        
        final String displayName;
        final Color color;
        
        DiaryTier(String displayName, Color color) {
            this.displayName = displayName;
            this.color = color;
        }
    }
    
    private DiaryTier parseTier(String tier) {
        if (tier == null) return null;
        switch (tier.toLowerCase()) {
            case "easy": return DiaryTier.EASY;
            case "medium": return DiaryTier.MEDIUM;
            case "hard": return DiaryTier.HARD;
            case "elite": return DiaryTier.ELITE;
            case "master": return DiaryTier.MASTER;
            case "grandmaster": return DiaryTier.GRANDMASTER;
            default: return null;
        }
    }
    
    // === Scrollable Implementation ===
    
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }
    
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }
    
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 50;
    }
    
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }
    
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

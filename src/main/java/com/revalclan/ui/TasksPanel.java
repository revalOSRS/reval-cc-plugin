package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.achievements.AchievementsResponse;
import com.revalclan.api.challenges.ChallengesResponse;
import com.revalclan.ui.components.Badge;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanValidator;
import com.revalclan.util.UIAssetLoader;
import net.runelite.api.Client;
import net.runelite.api.GameState;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tasks tab panel - displays weekly and monthly clan tasks, and achievements
 */
public class TasksPanel extends JPanel implements Scrollable {
    private RevalApiService apiService;
    private Client client;
    private UIAssetLoader assetLoader;
    
    private final JPanel contentPanel;
    private final Map<Task, JPanel> taskCards = new HashMap<>();
    
    // Data from API
    private List<ChallengesResponse.Challenge> weeklyChallenges = new ArrayList<>();
    private List<ChallengesResponse.Challenge> monthlyChallenges = new ArrayList<>();
    private List<AchievementsResponse.Achievement> achievements = new ArrayList<>();
    
    private JLabel statusLabel;
    
    public TasksPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);
        
        // Create scrollable content
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIConstants.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        // Status label for loading/errors
        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusLabel.setForeground(UIConstants.TEXT_SECONDARY);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setVisible(false);
        
        // Build the UI
        buildUI();
        
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
        
        
        add(scrollPane, BorderLayout.CENTER);
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
            loadData();
        } else {
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
        
        Timer retryTimer = new Timer(delayMs, e -> {
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
     * Check if user is authorized (logged in and in clan)
     */
    private boolean isAuthorized() {
        if (client == null) {
            return false;
        }
        
        GameState gameState = client.getGameState();
        if (gameState != GameState.LOGGED_IN) {
            return false;
        }
        
        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            return false;
        }
        
        return ClanValidator.validateClan(client);
    }
    
    /**
     * Force refresh data
     */
    public void refresh() {
        loadData();
    }
    
    private void loadData() {
        if (!isAuthorized()) {
            showNotLoggedIn();
            return;
        }
        
        if (apiService == null) {
            showError("API service not initialized");
            return;
        }
        
        showLoading();
        
        // Get account hash for progress tracking
        Long accountHash = null;
        if (client != null && client.getAccountHash() != -1) {
            accountHash = client.getAccountHash();
        }
        
        // Load challenges with progress data
        apiService.fetchChallenges(accountHash,
            response -> SwingUtilities.invokeLater(() -> {
                List<ChallengesResponse.Challenge> challenges = response.getData() != null ? response.getData().getChallenges() : null;
                if (challenges != null && !challenges.isEmpty()) {
                    // Filter by period
                    weeklyChallenges = challenges.stream()
                        .filter(c -> {
                            String period = c.getPeriod();
                            boolean isWeekly = period != null && "weekly".equalsIgnoreCase(period);
                            return isWeekly;
                        })
                        .filter(c -> c.getIsActive() == null || c.getIsActive()) // Only show active challenges
                        .collect(Collectors.toList());
                    monthlyChallenges = challenges.stream()
                        .filter(c -> {
                            String period = c.getPeriod();
                            boolean isMonthly = period != null && "monthly".equalsIgnoreCase(period);
                            return isMonthly;
                        })
                        .filter(c -> c.getIsActive() == null || c.getIsActive()) // Only show active challenges
                        .collect(Collectors.toList());
                } else {
                    weeklyChallenges = new ArrayList<>();
                    monthlyChallenges = new ArrayList<>();
                }
                // After challenges load, load achievements
                loadAchievements();
            }),
            error -> SwingUtilities.invokeLater(() -> {
                weeklyChallenges = new ArrayList<>();
                monthlyChallenges = new ArrayList<>();
                loadAchievements();
            })
        );
    }
    
    private void loadAchievements() {
        if (apiService == null || client == null || client.getAccountHash() == -1) {
            // Not logged in, just show challenges
            buildUI();
            return;
        }
        
        apiService.fetchAchievementDefinitions(
            client.getAccountHash(),
            response -> SwingUtilities.invokeLater(() -> {
                achievements = new ArrayList<>();
                if (response != null && response.isSuccess() && response.getData() != null) {
                    List<AchievementsResponse.Achievement> definitions = response.getData().getAchievements();
                    if (definitions != null) {
                        // Filter to only include achievements with progress (in-progress or completed)
                        achievements = definitions.stream()
                            .filter(def -> def != null && def.getProgress() != null)
                            .collect(Collectors.toList());
                    } else {
                        achievements = new ArrayList<>();
                    }
                }
                buildUI();
            }),
            error -> SwingUtilities.invokeLater(() -> {
                achievements = new ArrayList<>();
                buildUI();
            })
        );
    }
    
    private void buildUI() {
        contentPanel.removeAll();
        taskCards.clear();
        
        // Hide status label first - we're building the UI now
        statusLabel.setVisible(false);
        
        // Header
        JPanel header = createHeader();
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(header);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 16)));
        
        // Weekly Challenges Section
        List<Task> weeklyTasks = convertChallengesToTasks(weeklyChallenges);
        // isLoading should be false since we're building the UI with actual data
        JPanel weeklySection = createTaskSection("WEEKLY CHALLENGES", "Resets every Monday", weeklyTasks, UIConstants.ACCENT_BLUE, false);
        weeklySection.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(weeklySection);
        
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        // Monthly Challenges Section
        List<Task> monthlyTasks = convertChallengesToTasks(monthlyChallenges);
        // isLoading should be false since we're building the UI with actual data
        JPanel monthlySection = createTaskSection("MONTHLY CHALLENGES", "Resets on the 1st", monthlyTasks, UIConstants.ACCENT_PURPLE, false);
        monthlySection.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(monthlySection);
        
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        // Achievements Section (if any)
        if (!achievements.isEmpty()) {
            List<Task> achievementTasks = convertAchievementsToTasks(achievements);
            JPanel achievementsSection = createTaskSection("ACHIEVEMENTS", "Custom clan achievements", achievementTasks, UIConstants.ACCENT_ORANGE, false);
            achievementsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(achievementsSection);
        }
        
        contentPanel.add(Box.createVerticalGlue());
        
        revalidate();
        repaint();
    }
    
    private List<Task> convertChallengesToTasks(List<ChallengesResponse.Challenge> challenges) {
        if (challenges == null || challenges.isEmpty()) {
            return new ArrayList<>();
        }
        List<Task> tasks = challenges.stream()
            .map(challenge -> {
                TaskDifficulty difficulty = parseDifficulty(challenge.getDifficulty());
                
                // Parse progress from challenge
                int currentProgress = 0;
                int targetProgress = 1;
                String unit = "";
                
                if (challenge.getProgress() != null) {
                    ChallengesResponse.Challenge.ChallengeProgress progress = challenge.getProgress();
                    if (progress.getCurrent() != null) {
                        currentProgress = progress.getCurrent();
                    }
                    if (progress.getTarget() != null) {
                        targetProgress = progress.getTarget();
                    }
                } else if (challenge.getProgressPercent() != null && challenge.getProgressPercent() > 0) {
                    // Fallback: if we have progressPercent but no progress object, estimate
                    // This is less accurate but better than nothing
                    if (challenge.getRequirement() != null && challenge.getRequirement() instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> requirement = (java.util.Map<String, Object>) challenge.getRequirement();
                        Object countObj = requirement.get("count");
                        if (countObj instanceof Number) {
                            targetProgress = ((Number) countObj).intValue();
                            currentProgress = Math.round((challenge.getProgressPercent() / 100.0f) * targetProgress);
                        }
                    }
                }
                
                // If no progress data, try to extract target from requirement
                if (targetProgress == 1 && challenge.getRequirement() != null && challenge.getRequirement() instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> requirement = (java.util.Map<String, Object>) challenge.getRequirement();
                    Object countObj = requirement.get("count");
                    if (countObj instanceof Number) {
                        targetProgress = ((Number) countObj).intValue();
                    }
                }
                
                // If challenge is completed, set current to target
                if (challenge.isCompleted() && targetProgress > 0) {
                    currentProgress = targetProgress;
                }
                
                return new Task(
                    challenge.getName(),
                    challenge.getDescription(),
                    currentProgress,
                    targetProgress,
                    challenge.getPoints() != null ? challenge.getPoints() : 0,
                    unit,
                    difficulty
                );
            })
            .collect(Collectors.toList());
        return tasks;
    }
    
    private List<Task> convertAchievementsToTasks(List<AchievementsResponse.Achievement> achievements) {
        return achievements.stream()
            .filter(a -> a.getProgress() != null && !a.getProgress().isCompleted()) // Only show incomplete achievements
            .map(achievement -> {
                TaskDifficulty difficulty = parseDifficulty(achievement.getDifficulty());
                Object progressData = achievement.getProgress() != null ? achievement.getProgress().getProgressData() : null;
                return new Task(
                    achievement.getName(),
                    achievement.getDescription(),
                    progressData != null ? 1 : 0, // Simplified progress
                    progressData != null ? 1 : 0,
                    0, // Achievements no longer have points
                    "", // Unit
                    difficulty
                );
            })
            .collect(Collectors.toList());
    }
    
    private TaskDifficulty parseDifficulty(String difficulty) {
        if (difficulty == null) return TaskDifficulty.MEDIUM;
        switch (difficulty.toLowerCase()) {
            case "easy": return TaskDifficulty.EASY;
            case "medium": return TaskDifficulty.MEDIUM;
            case "hard": return TaskDifficulty.HARD;
            case "elite": return TaskDifficulty.ELITE;
            default: return TaskDifficulty.MEDIUM;
        }
    }
    
    private void showNotLoggedIn() {
        contentPanel.removeAll();
        contentPanel.add(new LoginPrompt("Clan Tasks"));
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private void showLoading() {
        statusLabel.setText("Loading tasks...");
        statusLabel.setVisible(true);
    }
    
    private void showError(String message) {
        statusLabel.setText("Error: " + message);
        statusLabel.setVisible(true);
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BACKGROUND);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        
        JLabel title = new JLabel("CLAN TASKS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.ACCENT_GOLD);
        
        // Refresh button (white text with hover background, matching Events panel)
        RefreshButton refreshButton = new RefreshButton(Color.WHITE);
        refreshButton.setRefreshAction(this::refresh);
        
        header.add(title, BorderLayout.WEST);
        header.add(refreshButton, BorderLayout.EAST);
        
        return header;
    }
    
    private JPanel createTaskSection(String title, String subtitle, List<Task> tasks, Color accentColor, boolean isLoading) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        
        // Section header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        
        // Colored accent bar
        JPanel accentBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(accentColor);
                g2d.fillRoundRect(0, 0, 4, getHeight(), 2, 2);
                g2d.dispose();
            }
        };
        accentBar.setOpaque(false);
        accentBar.setPreferredSize(new Dimension(4, 20));
        
        JLabel titleLabel = new JLabel("  " + title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(accentColor);
        
        // Add tooltip with subtitle information
        if (subtitle != null && !subtitle.isEmpty()) {
            titleLabel.setToolTipText(subtitle);
        }
        
        titlePanel.add(accentBar);
        titlePanel.add(titleLabel);
        
        headerPanel.add(titlePanel, BorderLayout.WEST);
        
        section.add(headerPanel);
        section.add(Box.createRigidArea(new Dimension(0, 8)));
        
        // Show loading or empty message
        if (isLoading) {
            JLabel loadingLabel = new JLabel("Loading tasks...");
            loadingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
            loadingLabel.setForeground(UIConstants.TEXT_SECONDARY);
            loadingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(loadingLabel);
        } else if (tasks == null || tasks.isEmpty()) {
            JLabel emptyLabel = new JLabel("No Tasks available at this moment");
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
            emptyLabel.setForeground(UIConstants.TEXT_SECONDARY);
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(emptyLabel);
        } else {
            // Task cards
            for (Task task : tasks) {
                JPanel card = createTaskCard(task, accentColor);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                section.add(card);
                section.add(Box.createRigidArea(new Dimension(0, 6)));
                taskCards.put(task, card);
            }
        }
        
        return section;
    }
    
    private JPanel createTaskCard(Task task, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Background
                Color bg = task.isComplete() ? UIConstants.TASK_COMPLETE : UIConstants.CARD_BG;
                g2d.setColor(bg);
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                
                // Left accent bar
                g2d.setColor(task.isComplete() ? UIConstants.ACCENT_GREEN_ALT : accentColor);
                g2d.fillRoundRect(0, 4, 3, getHeight() - 8, 2, 2);
                
                // Green border for completed tasks (like diary tasks)
                if (task.isComplete()) {
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                    g2d.setColor(UIConstants.ACCENT_GREEN_ALT);
                    g2d.setStroke(new BasicStroke(2));
                    g2d.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                }
                
                g2d.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 12, 10, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        
        JLabel nameLabel = new JLabel(task.getName());
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        nameLabel.setForeground(task.isComplete() ? UIConstants.ACCENT_GREEN_ALT : UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        Badge diffBadge = new Badge(task.getDifficulty().getLabel(), task.getDifficulty().getColor());
        diffBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        String description = task.getDescription() != null ? task.getDescription() : "";
        JTextArea descArea = new JTextArea(description);
        descArea.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        descArea.setForeground(UIConstants.TEXT_SECONDARY);
        descArea.setBackground(UIConstants.CARD_BG);
        descArea.setEditable(false);
        descArea.setFocusable(false);
        descArea.setWrapStyleWord(true);
        descArea.setLineWrap(true);
        descArea.setOpaque(false);
        descArea.setBorder(null);
        descArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Set preferred width to match card width
        descArea.setPreferredSize(new Dimension(140, descArea.getPreferredSize().height));
        descArea.setMaximumSize(new Dimension(140, Integer.MAX_VALUE));
        
        // Progress bar
        JPanel progressPanel = createProgressBar(task, accentColor);
        progressPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        infoPanel.add(nameLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        infoPanel.add(diffBadge);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        infoPanel.add(descArea);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        infoPanel.add(progressPanel);
        
        // Right side: Reward
        JPanel rewardPanel = new JPanel();
        rewardPanel.setLayout(new BoxLayout(rewardPanel, BoxLayout.Y_AXIS));
        rewardPanel.setOpaque(false);
        rewardPanel.setBorder(new EmptyBorder(0, 8, 0, 0));
        
        JLabel rewardValue = new JLabel("+" + task.getRewardPoints());
        rewardValue.setFont(new Font("Segoe UI", Font.BOLD, 14));
        rewardValue.setForeground(task.isComplete() ? UIConstants.ACCENT_GREEN_ALT : UIConstants.ACCENT_GOLD);
        rewardValue.setAlignmentX(Component.RIGHT_ALIGNMENT);
        
        JLabel rewardLabel = new JLabel("pts");
        rewardLabel.setFont(new Font("Segoe UI", Font.PLAIN, 8));
        rewardLabel.setForeground(UIConstants.TEXT_SECONDARY);
        rewardLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        
        if (task.isComplete()) {
            ImageIcon icon = getCheckmarkIcon();
            JLabel checkmark;
            if (icon != null) {
                checkmark = new JLabel(icon);
            } else {
                // Fallback to text if image loading failed
                checkmark = new JLabel("✓");
                checkmark.setFont(new Font("Segoe UI", Font.BOLD, 16));
                checkmark.setForeground(UIConstants.ACCENT_GREEN_ALT);
            }
            checkmark.setAlignmentX(Component.RIGHT_ALIGNMENT);
            checkmark.setHorizontalAlignment(SwingConstants.RIGHT);
            rewardPanel.add(checkmark);
            // Also show points for completed weekly/monthly challenges
            rewardPanel.add(rewardValue);
            rewardPanel.add(rewardLabel);
        } else {
            rewardPanel.add(rewardValue);
            rewardPanel.add(rewardLabel);
        }
        
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(rewardPanel, BorderLayout.EAST);
        
        // Hover effect
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.repaint();
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                card.repaint();
            }
        });
        
        return card;
    }
    
    
    private JPanel createProgressBar(Task task, Color accentColor) {
        JPanel progressPanel = new JPanel(new BorderLayout(6, 0));
        progressPanel.setOpaque(false);
        progressPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        
        double progress = task.getProgressPercent();
        
        // Progress bar visual
        JPanel barPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int barHeight = 6;
                int y = (getHeight() - barHeight) / 2;
                
                // Background
                g2d.setColor(UIConstants.PROGRESS_BG);
                g2d.fillRoundRect(0, y, getWidth(), barHeight, barHeight, barHeight);
                
                // Progress
                if (progress > 0) {
                    Color barColor = task.isComplete() ? UIConstants.ACCENT_GREEN_ALT : accentColor;
                    int fillWidth = (int) (getWidth() * Math.min(1.0, progress));
                    g2d.setColor(barColor);
                    g2d.fillRoundRect(0, y, fillWidth, barHeight, barHeight, barHeight);
                }
                
                g2d.dispose();
            }
        };
        barPanel.setOpaque(false);
        barPanel.setPreferredSize(new Dimension(0, 12));
        
        // Progress text
        String progressText = formatProgress(task);
        JLabel progressLabel = new JLabel(progressText);
        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 8));
        progressLabel.setForeground(UIConstants.TEXT_SECONDARY);
        
        progressPanel.add(barPanel, BorderLayout.CENTER);
        progressPanel.add(progressLabel, BorderLayout.EAST);
        
        return progressPanel;
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
    
    private String formatProgress(Task task) {
        if (task.isComplete()) {
            return "Complete!";
        }
        
        if (task.getTarget() >= 1000000) {
            return String.format("%.1fM/%.1fM %s", 
                task.getCurrent() / 1000000.0, 
                task.getTarget() / 1000000.0, 
                task.getUnit());
        } else if (task.getTarget() >= 1000) {
            return String.format("%.1fK/%.1fK %s", 
                task.getCurrent() / 1000.0, 
                task.getTarget() / 1000.0, 
                task.getUnit());
        }
        return task.getCurrent() + "/" + task.getTarget() + " " + task.getUnit();
    }
    
    // === Task Data Classes ===
    
    private enum TaskDifficulty {
        EASY("Easy", UIConstants.TIER_EASY),
        MEDIUM("Medium", UIConstants.TIER_MEDIUM),
        HARD("Hard", UIConstants.TIER_HARD),
        ELITE("Elite", UIConstants.TIER_ELITE);
        
        private final String label;
        private final Color color;
        
        TaskDifficulty(String label, Color color) {
            this.label = label;
            this.color = color;
        }
        
        public String getLabel() { return label; }
        public Color getColor() { return color; }
    }
    
    private static class Task {
        private final String name;
        private final String description;
        private int current;
        private final int target;
        private final int rewardPoints;
        private final String unit;
        private final TaskDifficulty difficulty;
        
        Task(String name, String description, int current, int target, int rewardPoints, String unit, TaskDifficulty difficulty) {
            this.name = name;
            this.description = description;
            this.current = current;
            this.target = target;
            this.rewardPoints = rewardPoints;
            this.unit = unit;
            this.difficulty = difficulty;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getCurrent() { return current; }
        public int getTarget() { return target; }
        public int getRewardPoints() { return rewardPoints; }
        public String getUnit() { return unit; }
        public TaskDifficulty getDifficulty() { return difficulty; }
        
        public boolean isComplete() { return current >= target; }
        public double getProgressPercent() { return (double) current / target; }
    }
    
    // === Scrollable implementation to constrain width ===
    
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
        return true; // Force width to match viewport
    }
    
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

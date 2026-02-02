package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.achievements.AchievementsResponse;
import com.revalclan.ui.components.Badge;
import com.revalclan.ui.components.EmptyState;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanValidator;
import net.runelite.api.Client;
import net.runelite.api.GameState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Achievements tab panel - displays all achievements in a grid layout
 */
public class AchievementsPanel extends JPanel implements Scrollable {
    private RevalApiService apiService;
    private Client client;
    
    private final JPanel contentPanel;
    private JPanel achievementsGrid;
    private JLabel statusLabel;
    
    private List<AchievementsResponse.Achievement> allAchievements = new ArrayList<>();
    
    public AchievementsPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);
        
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIConstants.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        
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
        
        add(scrollPane, BorderLayout.CENTER);
        
        // Show not logged in initially (will be updated when client is set)
        showNotLoggedIn();
    }
    
    public void init(RevalApiService apiService, Client client) {
        this.apiService = apiService;
        this.client = client;
        // Check authorization state after client is set
        if (!isAuthorized()) {
            showNotLoggedIn();
        }
    }
    
    /**
     * Called when the player is logged in - refresh achievements
     */
    public void onLoggedIn() {
        checkAuthorizationAndLoad();
    }
    
    public void onLoggedOut() {
        SwingUtilities.invokeLater(() -> showNotLoggedIn());
    }
    
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
        
        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            showNotLoggedIn();
            return;
        }
        
        boolean isInClan = ClanValidator.validateClan(client);
        if (isInClan) {
            loadData();
        } else {
            scheduleRetryWithBackoff(1, 10);
        }
    }
    
    private void scheduleRetryWithBackoff(int attempt, int maxAttempts) {
        if (attempt > maxAttempts) {
            showNotLoggedIn();
            return;
        }
        
        int delayMs = attempt * 1000;
        javax.swing.Timer retryTimer = new javax.swing.Timer(delayMs, e -> {
            boolean retryIsInClan = ClanValidator.validateClan(client);
            if (retryIsInClan) {
                loadData();
            } else {
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
     * Force refresh achievements
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
        
        if (client == null) {
            showError("Client not initialized");
            return;
        }
        
        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            showError("Not logged in");
            return;
        }
        
        showLoading();
        
        // Fetch achievements with progress (accountHash included)
        apiService.fetchAchievementDefinitions(accountHash,
            response -> SwingUtilities.invokeLater(() -> {
                if (response != null && response.isSuccess() && response.getData() != null) {
                    List<AchievementsResponse.Achievement> definitions = response.getData().getAchievements();
                    allAchievements = definitions != null ? definitions : new ArrayList<>();
                } else {
                    allAchievements = new ArrayList<>();
                }
                
                buildUI();
            }),
            error -> {
                SwingUtilities.invokeLater(() -> {
                    allAchievements = new ArrayList<>();
                    showError("Failed to load achievements: " + error.getMessage());
                    buildUI();
                });
            }
        );
    }
    
    private void showLoading() {
        statusLabel.setText("Loading achievements...");
        statusLabel.setVisible(true);
    }
    
    private void showError(String message) {
        statusLabel.setText("Error: " + message);
        statusLabel.setVisible(true);
    }
    
    private void buildUI() {
        contentPanel.removeAll();
        
        if (!isAuthorized()) {
            showNotLoggedIn();
            return;
        }
        
        // Hide loading/error status when building UI
        statusLabel.setVisible(false);
        
        // Header (exactly like DiaryPanel)
        JPanel header = createHeader();
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(header);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        
        // Achievements grid
        if (allAchievements.isEmpty()) {
            showEmptyState();
        } else {
            achievementsGrid = new JPanel();
            achievementsGrid.setLayout(new GridLayout(0, 2, 8, 8)); // 2 columns, 8px gaps
            achievementsGrid.setOpaque(false);
            achievementsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            for (AchievementsResponse.Achievement achievement : allAchievements) {
                JPanel achievementCard = createAchievementCard(achievement);
                achievementsGrid.add(achievementCard);
            }
            
            contentPanel.add(achievementsGrid);
        }
        
        contentPanel.add(Box.createVerticalGlue());
        revalidate();
        repaint();
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
        
        JLabel title = new JLabel("ACHIEVEMENTS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIConstants.ACCENT_GOLD);
        
        // Refresh button (white text with hover background, matching Events and Tasks panels)
        RefreshButton refreshButton = new RefreshButton(Color.WHITE);
        refreshButton.setRefreshAction(this::refresh);
        
        header.add(title, BorderLayout.WEST);
        header.add(refreshButton, BorderLayout.EAST);
        
        return header;
    }
    
    private JPanel createAchievementCard(AchievementsResponse.Achievement achievement) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw rounded rectangle background
                g2d.setColor(UIConstants.CARD_BG);
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                
                // Draw border if completed
                if (achievement.getProgress() != null && achievement.getProgress().isCompleted()) {
                    g2d.setColor(UIConstants.ACCENT_GREEN_ALT);
                    g2d.setStroke(new BasicStroke(2.0f));
                    g2d.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
                }
                
                g2d.dispose();
            }
        };
        
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        card.setPreferredSize(new Dimension(0, 90));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // Rarity badge
        Badge rarityBadge = Badge.rarity(achievement.getRarity());
        rarityBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        int cardWidth = 200;
        String name = achievement.getName() != null ? achievement.getName() : "Unknown";
        name = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel nameLabel = new JLabel("<html><body style='width: " + (cardWidth - 16) + "px'>" + name + "</body></html>");
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Description - use JTextArea for proper multiline wrapping
        String description = achievement.getDescription() != null ? achievement.getDescription() : "";
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
        descArea.setPreferredSize(new Dimension(cardWidth - 16, descArea.getPreferredSize().height));
        descArea.setMaximumSize(new Dimension(cardWidth - 16, Integer.MAX_VALUE));
        
        card.add(rarityBadge);
        card.add(Box.createRigidArea(new Dimension(0, 3)));
        card.add(nameLabel);
        card.add(Box.createRigidArea(new Dimension(0, 3)));
        card.add(descArea);
        
        // Hover effect
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                card.setBackground(UIConstants.CARD_HOVER);
                card.repaint();
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                card.setBackground(UIConstants.CARD_BG);
                card.repaint();
            }
        });
        
        return card;
    }
    
    private void showNotLoggedIn() {
        contentPanel.removeAll();
        contentPanel.add(new LoginPrompt("Achievements"));
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private void showEmptyState() {
        contentPanel.add(new EmptyState("🏆", "No achievements available"));
    }
    
    // Scrollable interface implementation
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
        return visibleRect.height;
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

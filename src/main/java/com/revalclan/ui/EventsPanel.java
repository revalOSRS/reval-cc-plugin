package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.ui.components.EmptyState;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.ui.components.EventCard;
import com.revalclan.util.ClanValidator;
import net.runelite.api.GameState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Events tab panel - displays clan events and competitions
 */
public class EventsPanel extends JPanel implements Scrollable {
    
    
    private RevalApiService apiService;
    private net.runelite.api.Client client;
    
    private JPanel contentPanel;
    private JPanel eventsListPanel;
    private JLabel statusLabel;
    
    private String currentTab = "active";
    private List<EventsResponse.EventSummary> allEvents = new ArrayList<>();
    private long currentAccountHash = -1;
    private String currentPlayerName = null;
    
    // Tab buttons
    private JButton upcomingTab;
    private JButton activeTab;
    private JButton pastTab;
    
    public EventsPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);
        setBorder(new EmptyBorder(0, 0, 0, 0));
        
        buildUI();
    }
    
    public void init(RevalApiService apiService, net.runelite.api.Client client) {
        this.apiService = apiService;
        this.client = client;
    }
    
    /**
     * Called when the player is logged in - refresh events
     */
    public void onLoggedIn() {
        // Only reload if we don't have data yet or if showing login prompt
        if (allEvents.isEmpty() || isShowingLoginPrompt()) {
            checkAuthorizationAndLoad();
        }
    }
    
    /**
     * Check if the panel is currently showing the login prompt
     */
    private boolean isShowingLoginPrompt() {
        if (contentPanel == null || contentPanel.getComponentCount() == 0) {
            return true;
        }
        // Check if the first component is a LoginPrompt
        return contentPanel.getComponent(0) instanceof LoginPrompt;
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
            // Authorized - build UI and load data
            if (eventsListPanel == null) {
                buildFullUI();
            }
            
            currentAccountHash = accountHash;
            currentPlayerName = client.getLocalPlayer() != null ? 
                client.getLocalPlayer().getName() : null;
            loadEvents();
        } else {
            // Clan channel might not be ready yet - retry multiple times with increasing delays
            scheduleRetryWithBackoff(1, 10, accountHash);
        }
    }
    
    /**
     * Schedule retry with exponential backoff, up to maxAttempts attempts
     */
    private void scheduleRetryWithBackoff(int attempt, int maxAttempts, long accountHash) {
        if (attempt > maxAttempts) {
            showNotLoggedIn();
            return;
        }
        
        int delayMs = attempt * 1000; // 1s, 2s, 3s, etc.
        
        javax.swing.Timer retryTimer = new javax.swing.Timer(delayMs, e -> {
            boolean retryIsInClan = ClanValidator.validateClan(client);
            
            if (retryIsInClan) {
                // Now authorized - build UI and load data
                if (eventsListPanel == null) {
                    buildFullUI();
                }
                
                currentAccountHash = accountHash;
                currentPlayerName = client.getLocalPlayer() != null ? 
                    client.getLocalPlayer().getName() : null;
                loadEvents();
            } else {
                // Schedule next retry
                scheduleRetryWithBackoff(attempt + 1, maxAttempts, accountHash);
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
     * Force refresh events
     */
    public void refresh() {
        if (apiService != null) {
            apiService.refreshEvents(this::onEventsLoaded, this::onError);
        }
    }
    
    private void buildUI() {
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIConstants.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(12, 10, 10, 10));
        
        // Show not logged in state initially
        showNotLoggedIn();
        
        add(contentPanel, BorderLayout.NORTH);
    }
    
    private void buildFullUI() {
        contentPanel.removeAll();
        
        // Header with refresh button
        JPanel headerPanel = buildHeader();
        contentPanel.add(headerPanel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        
        // Tab bar
        JPanel tabBar = buildTabBar();
        contentPanel.add(tabBar);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        
        // Status label (for loading/errors)
        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusLabel.setForeground(UIConstants.TEXT_MUTED);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setVisible(false);
        contentPanel.add(statusLabel);
        
        // Events list
        eventsListPanel = new JPanel();
        eventsListPanel.setLayout(new BoxLayout(eventsListPanel, BoxLayout.Y_AXIS));
        eventsListPanel.setBackground(UIConstants.BACKGROUND);
        eventsListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(eventsListPanel);
        
        // Initial loading state
        showLoading();
        
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        
        // Title
        JLabel titleLabel = new JLabel("EVENTS");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(UIConstants.ACCENT_GOLD);
        
        // Refresh button
        RefreshButton refreshBtn = new RefreshButton(Color.WHITE, UIConstants.TAB_HOVER);
        refreshBtn.setToolTipText("Refresh events");
        refreshBtn.setRefreshAction(this::refresh);
        
        header.add(titleLabel, BorderLayout.WEST);
        header.add(refreshBtn, BorderLayout.EAST);
        
        return header;
    }
    
    private JPanel buildTabBar() {
        JPanel tabBar = new JPanel(new GridLayout(1, 3, 4, 0));
        tabBar.setOpaque(false);
        tabBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        
        upcomingTab = createTabButton("Future", "upcoming", UIConstants.ACCENT_BLUE);
        activeTab = createTabButton("Active", "active", UIConstants.ACCENT_GREEN);
        pastTab = createTabButton("Past", "past", UIConstants.TEXT_MUTED);
        
        tabBar.add(upcomingTab);
        tabBar.add(activeTab);
        tabBar.add(pastTab);
        
        updateTabSelection();
        
        return tabBar;
    }
    
    private JButton createTabButton(String text, String tabId, Color accentColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                boolean isActive = currentTab.equals(tabId);
                
                // Background
                if (isActive) {
                    g2d.setColor(UIConstants.TAB_ACTIVE);
                } else if (getModel().isRollover()) {
                    g2d.setColor(UIConstants.TAB_HOVER);
                } else {
                    g2d.setColor(UIConstants.CARD_BG);
                }
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                
                // Border for active tab
                if (isActive) {
                    g2d.setColor(accentColor);
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2);
                }
                
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setForeground(currentTab.equals(tabId) ? accentColor : UIConstants.TEXT_SECONDARY);
        btn.setBackground(null);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        btn.addActionListener(e -> {
            currentTab = tabId;
            updateTabSelection();
            displayEvents();
        });
        
        return btn;
    }
    
    private void updateTabSelection() {
        upcomingTab.setForeground(currentTab.equals("upcoming") ? UIConstants.ACCENT_BLUE : UIConstants.TEXT_SECONDARY);
        activeTab.setForeground(currentTab.equals("active") ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_SECONDARY);
        pastTab.setForeground(currentTab.equals("past") ? UIConstants.TEXT_MUTED : UIConstants.TEXT_SECONDARY);
        
        upcomingTab.repaint();
        activeTab.repaint();
        pastTab.repaint();
    }
    
    public void loadEvents() {
        if (!isAuthorized()) {
            showNotLoggedIn();
            return;
        }
        
        if (apiService == null) {
            showError("API service not initialized");
            return;
        }
        
        showLoading();
        apiService.fetchEvents(this::onEventsLoaded, this::onError);
    }
    
    private void onEventsLoaded(EventsResponse response) {
        SwingUtilities.invokeLater(() -> {
            if (response != null && response.getData() != null && response.getData().getEvents() != null) {
                allEvents = response.getData().getEvents();
            } else {
                allEvents = new ArrayList<>();
            }
            displayEvents();
        });
    }
    
    private void onError(Exception e) {
        SwingUtilities.invokeLater(() -> {
            showError("Failed to load events: " + e.getMessage());
        });
    }
    
    private void displayEvents() {
        eventsListPanel.removeAll();
        statusLabel.setVisible(false);
        
        List<EventsResponse.EventSummary> filteredEvents = filterEvents();
        
        if (filteredEvents.isEmpty()) {
            showEmptyState();
        } else {
            for (EventsResponse.EventSummary event : filteredEvents) {
                EventCard.EventType type = getEventType(event);
                EventCard card = new EventCard(event, type, currentPlayerName, this::handleRegistration);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
                eventsListPanel.add(card);
            }
        }
        
        eventsListPanel.revalidate();
        eventsListPanel.repaint();
    }
    
    private List<EventsResponse.EventSummary> filterEvents() {
        List<EventsResponse.EventSummary> filtered = allEvents.stream()
            .filter(e -> {
                switch (currentTab) {
                    case "upcoming": return e.isUpcoming();
                    case "active": return e.isCurrentlyActive();
                    case "past": return e.isCompleted();
                    default: return true;
                }
            })
            .sorted(getComparator())
            .collect(Collectors.toList());
        
        return filtered;
    }
    
    private Comparator<EventsResponse.EventSummary> getComparator() {
        switch (currentTab) {
            case "upcoming":
                // Soonest first
                return Comparator.comparing(EventsResponse.EventSummary::getStartDate);
            case "active":
                // Ending soonest first
                return Comparator.comparing(EventsResponse.EventSummary::getEndDate);
            case "past":
                // Most recent first
                return Comparator.comparing(EventsResponse.EventSummary::getEndDate).reversed();
            default:
                return Comparator.comparing(EventsResponse.EventSummary::getStartDate);
        }
    }
    
    private EventCard.EventType getEventType(EventsResponse.EventSummary event) {
        if (event.isCompleted()) return EventCard.EventType.PAST;
        if (event.isCurrentlyActive()) return EventCard.EventType.ACTIVE;
        return EventCard.EventType.UPCOMING;
    }
    
    private void handleRegistration(String eventId, boolean isRegister) {
        if (apiService == null || currentAccountHash == -1) {
            JOptionPane.showMessageDialog(this, 
                "Please log in to register for events", 
                "Not Logged In", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (isRegister) {
            // Register for event
            int confirm = JOptionPane.showConfirmDialog(this,
                "Do you want to register for this event?",
                "Confirm Registration",
                JOptionPane.YES_NO_OPTION);
            
            if (confirm == JOptionPane.YES_OPTION) {
                apiService.registerForEvent(eventId, currentAccountHash,
                    response -> SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            response.getMessage(),
                            "Registration",
                            JOptionPane.INFORMATION_MESSAGE);
                        refresh();
                    }),
                    error -> SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "Registration failed: " + error.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    })
                );
            }
        } else {
            // Cancel registration
            int confirm = JOptionPane.showConfirmDialog(this,
                "Do you want to cancel your registration?",
                "Confirm Cancellation",
                JOptionPane.YES_NO_OPTION);
            
            if (confirm == JOptionPane.YES_OPTION) {
                apiService.cancelEventRegistration(eventId, currentAccountHash,
                    response -> SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            response.getMessage(),
                            "Cancellation",
                            JOptionPane.INFORMATION_MESSAGE);
                        refresh();
                    }),
                    error -> SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "Cancellation failed: " + error.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    })
                );
            }
        }
    }
    
    private void showLoading() {
        eventsListPanel.removeAll();
        
        // Wrapper to center the content horizontally
        JPanel wrapperPanel = new JPanel(new GridBagLayout());
        wrapperPanel.setOpaque(false);
        wrapperPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        
        JPanel loadingPanel = new JPanel();
        loadingPanel.setLayout(new BoxLayout(loadingPanel, BoxLayout.Y_AXIS));
        loadingPanel.setOpaque(false);
        loadingPanel.setBorder(new EmptyBorder(40, 0, 40, 0));
        
        JLabel spinnerLabel = new JLabel("⏳");
        spinnerLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        spinnerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        spinnerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel textLabel = new JLabel("Loading events...");
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        textLabel.setForeground(UIConstants.TEXT_MUTED);
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        loadingPanel.add(spinnerLabel);
        loadingPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        loadingPanel.add(textLabel);
        
        wrapperPanel.add(loadingPanel);
        eventsListPanel.add(wrapperPanel);
        eventsListPanel.revalidate();
        eventsListPanel.repaint();
    }
    
    private void showNotLoggedIn() {
        contentPanel.removeAll();
        contentPanel.add(new LoginPrompt("Events"));
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private void showEmptyState() {
        String emoji = currentTab.equals("upcoming") ? "📅" : 
                       currentTab.equals("active") ? "🎮" : "📋";
        String message = currentTab.equals("upcoming") ? "No future events" :
                        currentTab.equals("active") ? "No active events right now" :
                        "No past events";
        
        // Wrapper to center the empty state horizontally
        JPanel wrapperPanel = new JPanel(new GridBagLayout());
        wrapperPanel.setOpaque(false);
        wrapperPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        wrapperPanel.add(new EmptyState(emoji, message));
        eventsListPanel.add(wrapperPanel);
    }
    
    private void showError(String message) {
        eventsListPanel.removeAll();
        
        // Wrapper to center the content horizontally
        JPanel wrapperPanel = new JPanel(new GridBagLayout());
        wrapperPanel.setOpaque(false);
        wrapperPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        
        JPanel errorPanel = new JPanel();
        errorPanel.setLayout(new BoxLayout(errorPanel, BoxLayout.Y_AXIS));
        errorPanel.setOpaque(false);
        errorPanel.setBorder(new EmptyBorder(40, 0, 40, 0));
        
        JLabel emojiLabel = new JLabel("⚠️");
        emojiLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        emojiLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        emojiLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel textLabel = new JLabel("<html><center>" + message + "</center></html>");
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        textLabel.setForeground(UIConstants.TEXT_MUTED);
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JButton retryBtn = new JButton("Retry");
        retryBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        retryBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        retryBtn.addActionListener(e -> loadEvents());
        
        errorPanel.add(emojiLabel);
        errorPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        errorPanel.add(textLabel);
        errorPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        errorPanel.add(retryBtn);
        
        wrapperPanel.add(errorPanel);
        eventsListPanel.add(wrapperPanel);
        eventsListPanel.revalidate();
        eventsListPanel.repaint();
    }
    
    // Scrollable interface implementation
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }
    
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 20;
    }
    
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 60;
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

package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.leaderboard.LeaderboardResponse;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import com.revalclan.util.WikiIconLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Leaderboard panel - displays all players ranked by points with search functionality.
 * When clicking a player, shows their profile within this panel with a back button.
 */
public class LeaderboardPanel extends JPanel {

    private RevalApiService apiService;
    private WikiIconLoader wikiIconLoader;
    private UIAssetLoader assetLoader;

    // Card layout for switching between list and profile view
    private final CardLayout cardLayout;
    private final JPanel cardContainer;
    
    // List view components
    private final JPanel listViewPanel;
    private final JPanel contentPanel;
    private JTextField searchField;
    private final JLabel loadingLabel;
    private final JLabel errorLabel;
    private final JLabel resultCountLabel;

    // Profile view components
    private final JPanel profileViewPanel;
    private ProfilePanel embeddedProfilePanel;
    private String currentViewingPlayerName;

    private List<LeaderboardResponse.LeaderboardEntry> allEntries = new ArrayList<>();
    private List<LeaderboardResponse.LeaderboardEntry> filteredEntries = new ArrayList<>();

    private static final String LIST_VIEW = "LIST";
    private static final String PROFILE_VIEW = "PROFILE";

    public LeaderboardPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);

        // Card layout for view switching
        cardLayout = new CardLayout();
        cardContainer = new JPanel(cardLayout);
        cardContainer.setBackground(UIConstants.BACKGROUND);

        // === LIST VIEW ===
        listViewPanel = new JPanel(new BorderLayout());
        listViewPanel.setBackground(UIConstants.BACKGROUND);

        // Search panel at top
        JPanel searchPanel = createSearchPanel();
        listViewPanel.add(searchPanel, BorderLayout.NORTH);

        // Main scrollable content - use wrapper to keep content at top
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIConstants.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(4, 6, 6, 6));

        // Wrapper panel to keep content at top (not centered)
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setBackground(UIConstants.BACKGROUND);
        contentWrapper.add(contentPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(contentWrapper);
        scrollPane.setBackground(UIConstants.BACKGROUND);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);

        listViewPanel.add(scrollPane, BorderLayout.CENTER);

        // Loading state
        loadingLabel = new JLabel("Loading leaderboard...");
        loadingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        loadingLabel.setForeground(UIConstants.TEXT_SECONDARY);
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Error state
        errorLabel = new JLabel();
        errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        errorLabel.setForeground(UIConstants.ERROR_COLOR);
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Result count label
        resultCountLabel = new JLabel();
        resultCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        resultCountLabel.setForeground(UIConstants.TEXT_MUTED);

        // === PROFILE VIEW ===
        profileViewPanel = new JPanel(new BorderLayout());
        profileViewPanel.setBackground(UIConstants.BACKGROUND);

        // Add views to card container
        cardContainer.add(listViewPanel, LIST_VIEW);
        cardContainer.add(profileViewPanel, PROFILE_VIEW);

        add(cardContainer, BorderLayout.CENTER);
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.CARD_BG);
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Search text field - full width
        searchField = new JTextField();
        searchField.setBackground(UIConstants.BACKGROUND);
        searchField.setForeground(UIConstants.TEXT_PRIMARY);
        searchField.setCaretColor(UIConstants.TEXT_PRIMARY);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIConstants.CARD_HOVER, 1),
            new EmptyBorder(6, 10, 6, 10)
        ));
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Placeholder text
        searchField.putClientProperty("JTextField.placeholderText", "Search player...");

        // Search listener
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterLeaderboard(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filterLeaderboard(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filterLeaderboard(); }
        });

        panel.add(searchField, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBackHeader(String playerName) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.CARD_BG);
        header.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Back button
        BackButton backButton = new BackButton("← Back to Leaderboard", this::showListView, true);
        backButton.setPreferredSize(new Dimension(140, 28));

        // Player name label
        JLabel nameLabel = new JLabel(playerName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        nameLabel.setForeground(UIConstants.ACCENT_GOLD);
        nameLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        header.add(backButton, BorderLayout.WEST);
        header.add(nameLabel, BorderLayout.EAST);

        return header;
    }

    /**
     * Initialize the panel with required dependencies
     */
    public void init(RevalApiService apiService, WikiIconLoader wikiIconLoader, UIAssetLoader assetLoader) {
        this.apiService = apiService;
        this.wikiIconLoader = wikiIconLoader;
        this.assetLoader = assetLoader;
        loadLeaderboard();
    }

    /**
     * Refresh the leaderboard data
     */
    public void refresh() {
        loadLeaderboard();
    }

    /**
     * Show the list view
     */
    public void showListView() {
        cardLayout.show(cardContainer, LIST_VIEW);
    }

    /**
     * Show a player's profile
     */
    private void showPlayerProfile(int osrsAccountId, String playerName) {
        currentViewingPlayerName = playerName;
        
        // Rebuild profile view with back header
        profileViewPanel.removeAll();
        profileViewPanel.add(createBackHeader(playerName), BorderLayout.NORTH);

        // Create embedded profile panel
        embeddedProfilePanel = new ProfilePanel();
        embeddedProfilePanel.init(apiService, null, assetLoader);
        embeddedProfilePanel.loadAccountById(osrsAccountId);
        
        profileViewPanel.add(embeddedProfilePanel, BorderLayout.CENTER);
        profileViewPanel.revalidate();
        profileViewPanel.repaint();

        cardLayout.show(cardContainer, PROFILE_VIEW);
    }

    private void loadLeaderboard() {
        if (apiService == null) return;

        showLoading();

        apiService.fetchLeaderboard(
            response -> {
                if (response.getData() != null && response.getData().getLeaderboard() != null) {
                    allEntries = response.getData().getLeaderboard();
                    filteredEntries = new ArrayList<>(allEntries);
                    SwingUtilities.invokeLater(this::buildLeaderboard);
                } else {
                    SwingUtilities.invokeLater(() -> showError("No leaderboard data"));
                }
            },
            error -> {
                SwingUtilities.invokeLater(() -> showError("Failed to load leaderboard"));
            }
        );
    }

    private void filterLeaderboard() {
        String query = searchField.getText().toLowerCase().trim();

        if (query.isEmpty()) {
            filteredEntries = new ArrayList<>(allEntries);
        } else {
            filteredEntries = allEntries.stream()
                .filter(entry -> entry.getOsrsNickname().toLowerCase().contains(query))
                .collect(Collectors.toList());
        }

        buildLeaderboard();
    }

    private void showLoading() {
        contentPanel.removeAll();
        contentPanel.add(Box.createVerticalStrut(20));
        contentPanel.add(loadingLabel);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void showError(String message) {
        contentPanel.removeAll();
        contentPanel.add(Box.createVerticalStrut(20));
        errorLabel.setText(message);
        contentPanel.add(errorLabel);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void buildLeaderboard() {
        contentPanel.removeAll();

        if (filteredEntries.isEmpty()) {
            contentPanel.add(Box.createVerticalStrut(20));
            JLabel noResults = new JLabel(allEntries.isEmpty() ? "No players found" : "No matching players");
            noResults.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            noResults.setForeground(UIConstants.TEXT_SECONDARY);
            noResults.setHorizontalAlignment(SwingConstants.CENTER);
            noResults.setAlignmentX(Component.CENTER_ALIGNMENT);
            contentPanel.add(noResults);
        } else {
            // Result count
            String countText = filteredEntries.size() == allEntries.size()
                ? allEntries.size() + " players"
                : filteredEntries.size() + " of " + allEntries.size() + " players";
            resultCountLabel.setText(countText);
            
            JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            countPanel.setOpaque(false);
            countPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            countPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            countPanel.add(resultCountLabel);
            contentPanel.add(countPanel);
            contentPanel.add(Box.createVerticalStrut(6));

            // Add player entries
            for (LeaderboardResponse.LeaderboardEntry entry : filteredEntries) {
                JPanel row = createPlayerRow(entry);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                contentPanel.add(row);
                contentPanel.add(Box.createVerticalStrut(2));
            }
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPanel createPlayerRow(LeaderboardResponse.LeaderboardEntry entry) {
        JPanel row = new JPanel(new BorderLayout(8, 0)) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        setBackground(UIConstants.CARD_HOVER);
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        setBackground(UIConstants.CARD_BG);
                        setCursor(Cursor.getDefaultCursor());
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            showPlayerProfile(entry.getOsrsAccountId(), entry.getOsrsNickname());
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2d.dispose();
            }
        };

        row.setOpaque(false);
        row.setBackground(UIConstants.CARD_BG);
        row.setBorder(new EmptyBorder(8, 10, 8, 12));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        row.setPreferredSize(new Dimension(0, 44));

        // Left: Rank + Name
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.X_AXIS));
        leftPanel.setOpaque(false);

        // Rank badge
        JLabel rankLabel = new JLabel("#" + entry.getRank());
        rankLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        rankLabel.setForeground(getRankColor(entry.getRank()));
        rankLabel.setPreferredSize(new Dimension(32, 20));
        rankLabel.setMinimumSize(new Dimension(32, 20));

        // Player name
        JLabel nameLabel = new JLabel(entry.getOsrsNickname());
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);

        leftPanel.add(rankLabel);
        leftPanel.add(Box.createRigidArea(new Dimension(6, 0)));
        leftPanel.add(nameLabel);

        // Prestige indicator
        if (entry.getPrestigeLevel() > 0) {
            JLabel prestigeLabel = new JLabel(" ★" + entry.getPrestigeLevel());
            prestigeLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
            prestigeLabel.setForeground(UIConstants.ACCENT_PURPLE);
            leftPanel.add(prestigeLabel);
        }

        // Right: Points + Clan Rank
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);
        rightPanel.setMinimumSize(new Dimension(70, 0));

        JLabel pointsLabel = new JLabel(formatPoints(entry.getActivityPoints()) + " pts");
        pointsLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        pointsLabel.setForeground(UIConstants.ACCENT_GOLD);
        pointsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel clanRankLabel = new JLabel(capitalize(entry.getClanRank()));
        clanRankLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        clanRankLabel.setForeground(UIConstants.TEXT_MUTED);
        clanRankLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        rightPanel.add(pointsLabel);
        rightPanel.add(clanRankLabel);

        row.add(leftPanel, BorderLayout.WEST);
        row.add(rightPanel, BorderLayout.EAST);

        return row;
    }

    private Color getRankColor(int rank) {
        if (rank == 1) return new Color(255, 215, 0); // Gold
        if (rank == 2) return new Color(192, 192, 192); // Silver
        if (rank == 3) return new Color(205, 127, 50); // Bronze
        if (rank <= 10) return UIConstants.ACCENT_BLUE;
        return UIConstants.TEXT_SECONDARY;
    }

    private String formatPoints(int points) {
        if (points >= 1000000) {
            return String.format("%.1fM", points / 1000000.0);
        } else if (points >= 1000) {
            return String.format("%.1fK", points / 1000.0);
        }
        return String.valueOf(points);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}

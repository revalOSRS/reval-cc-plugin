package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.components.*;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.WikiIconLoader;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ranking tab panel - displays clan ranks and point sources
 */
@Slf4j
public class RankingPanel extends JPanel {
    private static final int ITEM_OATHPLATE_HELM = 29006; // Oathplate helm for PvM
    private static final int ITEM_CHAOS_ELEMENTAL_PET = 11995; // Pets
    private static final int ITEM_FIRE_CAPE = 6570; // Milestones
    private static final int ITEM_COLLECTION_LOG = 22711; // Collection log
    private static final int ITEM_COMBAT_ACHIEVEMENTS = 25956; // Combat achievements

    private final JPanel contentPanel;
    private final JLabel loadingLabel;
    private final JLabel errorLabel;
    private final GridBagConstraints gbc;
    private int gridY = 0;

    private RevalApiService apiService;
    private ItemManager itemManager;
    private WikiIconLoader wikiIconLoader;

    public RankingPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);

        contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(UIConstants.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(6, 2, 6, 2));

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(1, 0, 1, 0);

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

        loadingLabel = new JLabel("Loading...");
        loadingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        loadingLabel.setForeground(UIConstants.TEXT_SECONDARY);
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);

        errorLabel = new JLabel();
        errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        errorLabel.setForeground(UIConstants.ERROR_COLOR);
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        errorLabel.setVisible(false);

        showPlaceholder();

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Add component to content panel with proper width constraints
     */
    private void addComponent(JComponent comp) {
        gbc.gridy = gridY++;
        contentPanel.add(comp, gbc);
    }

    /**
     * Add vertical spacing
     */
    private void addSpacing(int height) {
        gbc.gridy = gridY++;
        gbc.insets = new Insets(height / 2, 0, height / 2, 0);
        contentPanel.add(Box.createVerticalStrut(height), gbc);
        gbc.insets = new Insets(1, 0, 1, 0);
    }

    /**
     * Initialize with dependencies
     */
    public void init(RevalApiService apiService, ItemManager itemManager, WikiIconLoader wikiIconLoader) {
        this.apiService = apiService;
        this.itemManager = itemManager;
        this.wikiIconLoader = wikiIconLoader;
        loadData();
    }

    /**
     * Refresh data from API
     */
    public void refresh() {
        if (apiService != null) {
            apiService.clearCache();
            loadData();
        }
    }

    private void showPlaceholder() {
        contentPanel.removeAll();
        gridY = 0;

        JPanel placeholderPanel = new JPanel();
        placeholderPanel.setLayout(new BoxLayout(placeholderPanel, BoxLayout.Y_AXIS));
        placeholderPanel.setBackground(UIConstants.BACKGROUND);
        placeholderPanel.setBorder(new EmptyBorder(30, 10, 10, 10));

        JLabel titleLabel = new JLabel("RANKING");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(UIConstants.ACCENT_GOLD);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Ranks & Points");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitleLabel.setForeground(UIConstants.TEXT_SECONDARY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hintLabel = new JLabel("Loading data...");
        hintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 9));
        hintLabel.setForeground(UIConstants.TEXT_SECONDARY);
        hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        placeholderPanel.add(titleLabel);
        placeholderPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        placeholderPanel.add(subtitleLabel);
        placeholderPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        placeholderPanel.add(hintLabel);

        addComponent(placeholderPanel);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void showLoading() {
        contentPanel.removeAll();
        gridY = 0;

        JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loadingPanel.setBackground(UIConstants.BACKGROUND);
        loadingPanel.setBorder(new EmptyBorder(30, 0, 0, 0));
        loadingPanel.add(loadingLabel);

        addComponent(loadingPanel);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void showError(String message) {
        errorLabel.setText("<html><center>" + message + "</center></html>");
        errorLabel.setVisible(true);

        JPanel errorPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        errorPanel.setBackground(UIConstants.BACKGROUND);
        errorPanel.add(errorLabel);
        addComponent(errorPanel);

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void loadData() {
        showLoading();

        apiService.fetchPoints(
            pointsResponse -> {
                SwingUtilities.invokeLater(() -> buildContent(pointsResponse));
            },
            error -> {
                SwingUtilities.invokeLater(() -> showError("Failed to load data: " + error.getMessage()));
            }
        );
    }

    private void buildContent(PointsResponse pointsResponse) {
        contentPanel.removeAll();
        gridY = 0;

        if (pointsResponse == null || pointsResponse.getData() == null) {
            showError("No data received");
            return;
        }

        PointsResponse.PointsData data = pointsResponse.getData();

        // === RANKS SECTION ===
        if (data.getRanks() != null && !data.getRanks().isEmpty()) {
            addComponent(createSectionHeader("RANKS", "Clan rank progression"));

            List<PointsResponse.Rank> regularRanks = data.getRanks().stream()
                .filter(r -> r.getPrestigeRequired() == 0)
                .collect(Collectors.toList());
            
            List<PointsResponse.Rank> prestigeRanks = data.getRanks().stream()
                .filter(r -> r.getPrestigeRequired() > 0)
                .collect(Collectors.toList());

            for (PointsResponse.Rank rank : regularRanks) {
                addComponent(new RankCard(rank, wikiIconLoader));
            }

            if (!prestigeRanks.isEmpty()) {
                addSpacing(6);

                JPanel prestigeRanksPanel = createVerticalPanel();
                for (PointsResponse.Rank rank : prestigeRanks) {
                    prestigeRanksPanel.add(new RankCard(rank, wikiIconLoader));
                    prestigeRanksPanel.add(Box.createRigidArea(new Dimension(0, 2)));
                }

                CollapsibleSection prestigeSection = new CollapsibleSection(
                    "Prestige Ranks",
                    "Requires prestige level 1+",
                    prestigeRanksPanel,
                    false
                );
                // Load paragon (Brigadier) clan icon for prestige section
                if (wikiIconLoader != null) {
                    wikiIconLoader.loadClanIconForSection("Brigadier", 18, prestigeSection);
                }
                addComponent(prestigeSection);
            }
        }

        // === POINT SOURCES SECTION ===
        addSpacing(12);
        addComponent(createSectionHeader("POINT SOURCES", "Ways to earn points"));

        // Point sources by category - display everything from pointSources
        // Skip UNTRADEABLE_DROPS here as it's handled separately below with proper grouping
        if (data.getPointSources() != null) {
            for (Map.Entry<String, List<PointsResponse.PointSource>> entry : data.getPointSources().entrySet()) {
                String categoryName = entry.getKey();
                List<PointsResponse.PointSource> sources = entry.getValue();

                if (sources == null || sources.isEmpty()) continue;
                
                // Skip untradeable drops - handled separately below
                if (categoryName.equals("UNTRADEABLE_DROPS")) {
                    continue;
                }

                JPanel categoryContent = createPointSourcesPanel(sources, categoryName);

                // Fix category names
                String displayName = formatCategoryName(categoryName);
                
                // Calculate subtitle
                String subtitle = null;
                String lowerName = categoryName.toLowerCase();
                String upperName = categoryName.toUpperCase();
                
                // Collection Log and Combat Achievements show final tier's points (not sum)
                if (upperName.equals("COLLECTION_LOG") || lowerName.contains("collection log")) {
                    // Find the highest points value (final tier)
                    int maxPoints = sources.stream()
                        .mapToInt(PointsResponse.PointSource::getPointsValue)
                        .max()
                        .orElse(0);
                    if (maxPoints > 0) {
                        subtitle = maxPoints + " pts max";
                    }
                } else if (upperName.equals("COMBAT_ACHIEVEMENTS") || lowerName.contains("combat achievement")) {
                    // Find the highest points value (final tier)
                    int maxPoints = sources.stream()
                        .mapToInt(PointsResponse.PointSource::getPointsValue)
                        .max()
                        .orElse(0);
                    if (maxPoints > 0) {
                        subtitle = maxPoints + " pts max";
                    }
                } else if (!lowerName.contains("pet") && !lowerName.contains("pvm") && !lowerName.contains("drop")) {
                    // Other categories show sum of all points
                    int totalPoints = sources.stream().mapToInt(PointsResponse.PointSource::getPointsValue).sum();
                    if (totalPoints > 0) {
                        subtitle = totalPoints + " pts";
                    }
                }

                CollapsibleSection section = new CollapsibleSection(displayName, subtitle, categoryContent, false);
                // Use category-specific icons for Collection Log and Combat Achievements
                if (upperName.equals("COLLECTION_LOG") || lowerName.contains("collection log")) {
                    wikiIconLoader.loadIconForSection("Collection_log_detail", 24, section);
                } else if (upperName.equals("COMBAT_ACHIEVEMENTS") || lowerName.contains("combat achievement")) {
                    wikiIconLoader.loadIconForSection("Combat_achievements_detail", 24, section);
                } else if (!sources.isEmpty() && sources.get(0).getIcon() != null && !sources.get(0).getIcon().isEmpty()) {
                    // Try to use icon from first point source for other categories
                    wikiIconLoader.loadIconForSection(sources.get(0).getIcon(), 24, section);
                } else {
                    // Fallback to category-based icon using ItemManager
                    loadCategoryIcon(section, categoryName);
                }
                addComponent(section);
            }
        }

        // Untradeable Drops (now in pointSources) - handled separately with proper grouping
        List<PointsResponse.PointSource> untradeableDrops = data.getPointSources() != null 
            ? data.getPointSources().get("UNTRADEABLE_DROPS") 
            : null;
        if (untradeableDrops != null && !untradeableDrops.isEmpty()) {
            JPanel untradeablePanel = createUntradeableDropsPanel(untradeableDrops);
            CollapsibleSection section = new CollapsibleSection("Untradeable Drops", null, untradeablePanel, false);
            // Try to use icon from first point source, fallback to PvM icon
            if (!untradeableDrops.isEmpty() && untradeableDrops.get(0).getIcon() != null) {
                wikiIconLoader.loadIconForSection(untradeableDrops.get(0).getIcon(), 24, section);
            } else {
                loadItemIcon(section, ITEM_OATHPLATE_HELM); // Use PvM icon for untradeable drops
            }
            addComponent(section);
        }

        addSpacing(16);

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPanel createVerticalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UIConstants.BACKGROUND);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private void loadCategoryIcon(CollapsibleSection section, String categoryName) {
        int itemId;
        String upper = categoryName.toUpperCase();
        String lower = categoryName.toLowerCase();
        
        // Match exact category names from API response
        if (upper.equals("VALUABLE_PVM_DROPS") || lower.contains("valuable") || lower.contains("pvm") || lower.contains("drop")) {
            itemId = ITEM_OATHPLATE_HELM;
        } else if (upper.equals("PETS") || lower.equals("pets")) {
            itemId = ITEM_CHAOS_ELEMENTAL_PET;
        } else if (upper.equals("MILESTONES") || lower.equals("milestones")) {
            itemId = ITEM_FIRE_CAPE;
        } else if (upper.equals("COLLECTION_LOG") || lower.equals("collection_log") || lower.contains("collection log")) {
            itemId = ITEM_COLLECTION_LOG;
        } else if (upper.equals("COMBAT_ACHIEVEMENTS") || lower.equals("combat_achievements") || lower.contains("combat achievement")) {
            itemId = ITEM_COMBAT_ACHIEVEMENTS;
        } else {
            return;
        }
        loadItemIcon(section, itemId);
    }

    private void loadItemIcon(CollapsibleSection section, int itemId) {
        if (itemManager == null || itemId <= 0) return;

        AsyncBufferedImage itemImage = itemManager.getImage(itemId);
        itemImage.onLoaded(() -> {
            SwingUtilities.invokeLater(() -> {
                BufferedImage scaled = ImageUtil.resizeImage(itemImage, 24, 24);
                section.setIcon(new ImageIcon(scaled));
            });
        });
    }

    private JPanel createSectionHeader(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BACKGROUND);
        header.setBorder(new EmptyBorder(6, 2, 4, 2));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(UIConstants.ACCENT_GOLD);

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        subtitleLabel.setForeground(UIConstants.TEXT_SECONDARY);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.add(titleLabel);
        textPanel.add(subtitleLabel);

        header.add(textPanel, BorderLayout.WEST);
        return header;
    }

    private JPanel createPointSourcesPanel(List<PointsResponse.PointSource> sources, String categoryName) {
        JPanel panel = createVerticalPanel();
        panel.setBorder(new EmptyBorder(2, 0, 2, 0));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Determine if icons should be shown for this category
        boolean showIcons = shouldShowIconsForCategory(categoryName);

        for (PointsResponse.PointSource source : sources) {
            PointSourceRow row;
            if (showIcons) {
                row = new PointSourceRow(source);
            } else {
                // Create row without icon
                row = new PointSourceRow(source.getName(), source.getDescription(), source.getPointsDisplay(), null);
            }
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(row);
            panel.add(Box.createRigidArea(new Dimension(0, 1)));
        }
        return panel;
    }

    /**
     * Determine if icons should be displayed for a given category
     */
    private boolean shouldShowIconsForCategory(String categoryName) {
        if (categoryName == null) return false;
        String upper = categoryName.toUpperCase();
        String lower = categoryName.toLowerCase();
        
        // Don't show icons for valuable drops and pets
        if (upper.equals("VALUABLE_PVM_DROPS") || lower.contains("valuable") || lower.contains("pvm") || lower.contains("drop")) {
            return false;
        }
        if (upper.equals("PETS") || lower.equals("pets")) {
            return false;
        }
        
        // Show icons for other categories (milestones, collection log, combat achievements, untradeable drops)
        return true;
    }


    private JPanel createUntradeableDropsPanel(List<PointsResponse.PointSource> sources) {
        JPanel panel = createVerticalPanel();
        panel.setBorder(new EmptyBorder(2, 0, 2, 0));

        // Group sources by category from metadata
        Map<String, List<PointsResponse.PointSource>> groupedByCategory = sources.stream()
            .filter(source -> source.getMetadata() != null && source.getMetadata().getCategory() != null)
            .collect(Collectors.groupingBy(
                source -> source.getMetadata().getCategory(),
                Collectors.toList()
            ));

        for (Map.Entry<String, List<PointsResponse.PointSource>> entry : groupedByCategory.entrySet()) {
            String categoryName = entry.getKey();
            List<PointsResponse.PointSource> categorySources = entry.getValue();
            
            if (categorySources.isEmpty()) continue;
            
            // Add category header - left aligned
            JLabel categoryLabel = new JLabel(categoryName);
            categoryLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
            categoryLabel.setForeground(UIConstants.ACCENT_GOLD);
            categoryLabel.setBorder(new EmptyBorder(4, 0, 2, 0));
            categoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            categoryLabel.setHorizontalAlignment(SwingConstants.LEFT);
            
            // Wrap in a panel to ensure left alignment
            JPanel headerWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            headerWrapper.setOpaque(false);
            headerWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            headerWrapper.add(categoryLabel);
            panel.add(headerWrapper);
            
            // Add items in this category with icons
            for (PointsResponse.PointSource source : categorySources) {
                String sourceName = source.getMetadata() != null && source.getMetadata().getSource() != null 
                    ? source.getMetadata().getSource() 
                    : "";
                String pointsText = source.getPointsDescription() != null 
                    ? "+" + source.getPointsDescription() 
                    : (source.getPoints() != null && source.getPoints() > 0 
                        ? "+" + source.getPoints() + " pts" 
                        : "");
                // Use icon identifier from PointSource
                String iconIdentifier = source.getIcon();
                JPanel itemRow = createUntradeableDropRow(source.getName(), sourceName, pointsText, iconIdentifier);
                itemRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                panel.add(itemRow);
                panel.add(Box.createRigidArea(new Dimension(0, 1)));
            }
            
            // Add spacing between categories
            panel.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        return panel;
    }

    private JPanel createUntradeableDropRow(String name, String description, String points, String iconIdentifier) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(UIConstants.ROW_BG);
        row.setBorder(new EmptyBorder(5, 8, 5, 8));
        row.setOpaque(false);

        JPanel leftPanel = new JPanel(new BorderLayout(6, 0));
        leftPanel.setOpaque(false);

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(20, 20));
        iconLabel.setMinimumSize(new Dimension(20, 20));
        iconLabel.setMaximumSize(new Dimension(20, 20));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);

        if (iconIdentifier != null && !iconIdentifier.isEmpty()) {
            wikiIconLoader.loadIconForLabel(iconIdentifier, 20, iconLabel);
        }

        // Text panel
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(nameLabel);

        if (description != null && !description.isEmpty()) {
            // Use JTextArea for proper multiline wrapping
            JTextArea descArea = new JTextArea(description);
            descArea.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            descArea.setForeground(UIConstants.TEXT_SECONDARY);
            descArea.setBackground(UIConstants.ROW_BG);
            descArea.setEditable(false);
            descArea.setFocusable(false);
            descArea.setWrapStyleWord(true);
            descArea.setLineWrap(true);
            descArea.setOpaque(false);
            descArea.setBorder(null);
            descArea.setAlignmentX(Component.LEFT_ALIGNMENT);
            // Let it expand to fill available space
            descArea.setPreferredSize(new Dimension(0, descArea.getPreferredSize().height));
            descArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            textPanel.add(descArea);
        }

        leftPanel.add(iconLabel, BorderLayout.WEST);
        leftPanel.add(textPanel, BorderLayout.CENTER);

        // Right: Points
        JLabel pointsLabel = new JLabel(points);
        pointsLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        pointsLabel.setForeground(UIConstants.ACCENT_GREEN);

        row.add(leftPanel, BorderLayout.CENTER);
        row.add(pointsLabel, BorderLayout.EAST);

        // Wrap in panel with rounded background
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(UIConstants.ROW_BG);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2d.dispose();
            }
        };
        wrapper.setOpaque(false);
        wrapper.add(row, BorderLayout.CENTER);
        
        return wrapper;
    }

    private String formatCategoryName(String categoryName) {
        if (categoryName == null) return categoryName;
        String lower = categoryName.toLowerCase();
        if (lower.equals("valuable_pvm_drops") || lower.equals("valuable pvm drops")) {
            return "Valuable Drops";
        } else if (lower.equals("pets")) {
            return "Pets";
        } else if (lower.equals("milestones")) {
            return "Milestones";
        } else if (lower.contains("collection log")) {
            return "Collection Log";
        } else if (lower.contains("combat achievement")) {
            return "Combat Achievements";
        }
        // Capitalize first letter of each word
        String[] words = categoryName.split("[_\\s]+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (words[i].length() > 0) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }
}

package com.revalclan.ui.components;

import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.WikiIconLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class RankCard extends JPanel {
    
    private static final Map<String, String> RANK_ICON_FALLBACK = Map.ofEntries(
        Map.entry("member", "Mentor"),
        Map.entry("advanced", "Prefect"),
        Map.entry("elite", "Leader"),
        Map.entry("veteran", "Supervisor"),
        Map.entry("hero", "Superior"),
        Map.entry("champion", "Executive"),
        Map.entry("legend", "Senator"),
        Map.entry("mythic", "Monarch"),
        Map.entry("paragon", "Brigadier"),
        Map.entry("ascended", "Admiral"),
        Map.entry("eternal", "Marshal"),
        // Additional mappings for API name variations
        Map.entry("recruit", "Recruit"),
        Map.entry("corporal", "Corporal"),
        Map.entry("sergeant", "Sergeant")
    );
    
    private final WikiIconLoader wikiIconLoader;
    private final JLabel iconLabel;
    private final List<String> ingameRanks;
    private final String rankName;
    private final String internalName;
    private final Color rankColor;

    public RankCard(PointsResponse.Rank rank, WikiIconLoader wikiIconLoader) {
        this.wikiIconLoader = wikiIconLoader;
        setLayout(new BorderLayout(4, 0));
        setBackground(UIConstants.CARD_BG);
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setMinimumSize(new Dimension(0, 40));
        setPreferredSize(new Dimension(0, 40));

        String displayName = rank.getDisplayName() != null ? rank.getDisplayName() : rank.getName();
        this.ingameRanks = rank.getIngameRank();
        this.rankName = displayName.toLowerCase();
        this.internalName = rank.getName() != null ? rank.getName().toLowerCase() : null;
        this.rankColor = UIConstants.ACCENT_GOLD;

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);

        iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(18, 18));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Start loading the icon
        loadRankIcon();

        // Rank name
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        nameLabel.setForeground(rankColor);

        leftPanel.add(iconLabel);
        leftPanel.add(nameLabel);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));
        rightPanel.setOpaque(false);

        String pointsText = formatPoints(rank.getPointsRequired());
        if (rank.getMaintenancePerMonth() > 0) {
            pointsText += " (" + rank.getMaintenancePerMonth() + "/mo)";
        }

        JLabel pointsLabel = new JLabel(pointsText);
        pointsLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        pointsLabel.setForeground(UIConstants.POINTS_COLOR);
        pointsLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        rightPanel.add(pointsLabel);

        // Wrap panels in GridBagLayout containers for vertical centering
        JPanel leftWrapper = new JPanel(new GridBagLayout());
        leftWrapper.setOpaque(false);
        leftWrapper.add(leftPanel);

        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setOpaque(false);
        rightWrapper.add(rightPanel);

        add(leftWrapper, BorderLayout.WEST);
        add(rightWrapper, BorderLayout.EAST);

        // Tooltip for additional requirements
        if (rank.getAdditionalRequirements() != null && !rank.getAdditionalRequirements().isEmpty()) {
            StringBuilder tooltip = new StringBuilder();
            for (PointsResponse.Rank.AdditionalRequirement req : rank.getAdditionalRequirements()) {
                if (tooltip.length() > 0) tooltip.append(", ");
                tooltip.append(req.getDescription());
            }
            setToolTipText(tooltip.toString());
        }
    }

    private void loadRankIcon() {
        if (wikiIconLoader == null) {
            setFallbackIcon();
            return;
        }
        
        String iconName = null;
        
        // First try hardcoded fallback map using rankName (most reliable)
        iconName = RANK_ICON_FALLBACK.get(rankName);
        
        // Try hardcoded map using internal name if displayName didn't match
        if ((iconName == null || iconName.isEmpty()) && internalName != null) {
            iconName = RANK_ICON_FALLBACK.get(internalName);
        }
        
        // Try API ingameRanks as last resort
        if ((iconName == null || iconName.isEmpty()) && ingameRanks != null && !ingameRanks.isEmpty()) {
            iconName = ingameRanks.get(0);
            if ((iconName == null || iconName.isEmpty()) && ingameRanks.size() > 1) {
                iconName = ingameRanks.get(1);
            }
        }

        if (iconName == null || iconName.isEmpty()) {
            setFallbackIcon();
            return;
        }

        wikiIconLoader.loadClanIcon(iconName, 18, iconLabel);
    }

    private void setFallbackIcon() {
        iconLabel.setIcon(null);
        iconLabel.setText("★");
        iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        iconLabel.setForeground(rankColor);
    }

    private String formatPoints(int points) {
        if (points >= 1000) {
            return String.format("%.1fk", points / 1000.0);
        }
        return String.valueOf(points);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(getBackground());
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
        g2d.dispose();
    }
}

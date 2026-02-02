package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

/**
 * Reusable checklist item with status box, description, and optional points.
 * Used for milestones, diary tasks, achievement tiers, etc.
 */
public class ChecklistItem extends JPanel {
    
    private static final ImageIcon CHECKMARK_ICON;
    
    static {
        ImageIcon icon = null;
        try {
            URL imageUrl = ChecklistItem.class.getResource("/com/revalclan/ui/assets/checkmark.png");
            if (imageUrl != null) {
                BufferedImage image = ImageIO.read(imageUrl);
                if (image != null) {
                    Image scaled = image.getScaledInstance(10, 10, Image.SCALE_SMOOTH);
                    icon = new ImageIcon(scaled);
                }
            }
        } catch (IOException ignored) {}
        CHECKMARK_ICON = icon;
    }
    
    public ChecklistItem(String description, boolean completed, Integer points) {
        this(description, completed, points, 220);
    }
    
    public ChecklistItem(String description, boolean completed, Integer points, int descriptionWidth) {
        setLayout(new BorderLayout(8, 0));
        setOpaque(false);
        
        JPanel statusBox = createStatusBox(completed);
        
        String escapedDescription = description
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
        String htmlDescription = "<html><body style='width: " + descriptionWidth + "px; word-wrap: break-word;'>" + escapedDescription + "</body></html>";
        
        JLabel descriptionLabel = new JLabel(htmlDescription);
        descriptionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        descriptionLabel.setForeground(completed ? UIConstants.TEXT_PRIMARY : UIConstants.TEXT_SECONDARY);
        descriptionLabel.setVerticalAlignment(SwingConstants.TOP);
        
        JPanel leftPanel = new JPanel(new BorderLayout(8, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(statusBox, BorderLayout.WEST);
        leftPanel.add(descriptionLabel, BorderLayout.CENTER);
        
        add(leftPanel, BorderLayout.CENTER);
        
        // Points label
        if (points != null && points > 0) {
            JLabel pointsLabel = new JLabel("+" + points + " pts");
            pointsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            pointsLabel.setForeground(UIConstants.ACCENT_GOLD);
            pointsLabel.setVerticalAlignment(SwingConstants.TOP);
            
            JPanel pointsPanel = new JPanel(new BorderLayout());
            pointsPanel.setOpaque(false);
            pointsPanel.setBorder(new EmptyBorder(0, 8, 0, 0));
            pointsPanel.add(pointsLabel, BorderLayout.EAST);
            add(pointsPanel, BorderLayout.EAST);
        }
    }
    
    private JPanel createStatusBox(boolean completed) {
        JPanel statusBox = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int size = Math.min(getWidth(), getHeight());
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                Color completedBg = new Color(70, 75, 70);
                g2d.setColor(completed ? completedBg : UIConstants.TEXT_MUTED);
                g2d.fillRect(x, y, size, size);
                
                if (completed && CHECKMARK_ICON != null) {
                    int iconX = x + (size - CHECKMARK_ICON.getIconWidth()) / 2;
                    int iconY = y + (size - CHECKMARK_ICON.getIconHeight()) / 2;
                    CHECKMARK_ICON.paintIcon(this, g2d, iconX, iconY);
                }
                
                g2d.dispose();
            }
        };
        statusBox.setPreferredSize(new Dimension(14, 14));
        statusBox.setMinimumSize(new Dimension(14, 14));
        statusBox.setMaximumSize(new Dimension(14, 14));
        statusBox.setOpaque(false);
        return statusBox;
    }
}

package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Reusable button component for achievements navigation.
 * Similar styling to tab buttons with selection and hover states.
 */
public class AchievementsButton extends JPanel {
    private boolean selected = false;
    private boolean hovered = false;
    
    public AchievementsButton() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(0, 28));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }
    
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
        repaint();
    }
    
    public boolean isSelected() {
        return selected;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth();
        int height = getHeight();
        int arc = 8;
        
        Color bgColor;
        if (selected) {
            bgColor = UIConstants.ACCENT_GOLD; // Active tab color
        } else if (hovered) {
            bgColor = UIConstants.TAB_HOVER;
        } else {
            bgColor = UIConstants.TAB_ACTIVE;
        }
        
        g2d.setColor(bgColor);
        g2d.fill(new RoundRectangle2D.Float(0, 0, width, height, arc, arc));
        
        // Subtle inner glow for active tab
        if (selected) {
            g2d.setColor(new Color(255, 255, 255, 30));
            g2d.fill(new RoundRectangle2D.Float(1, 1, width - 2, height / 2, arc - 1, arc - 1));
        }
        
        // Text
        Color textColor;
        if (selected) {
            textColor = UIConstants.BACKGROUND; // Dark text on gold
        } else if (hovered) {
            textColor = UIConstants.TEXT_PRIMARY;
        } else {
            textColor = UIConstants.TEXT_SECONDARY;
        }
        g2d.setColor(textColor);
        g2d.setFont(new Font("Segoe UI", selected ? Font.BOLD : Font.PLAIN, 11));
        
        FontMetrics fm = g2d.getFontMetrics();
        String text = "Achievements";
        int textWidth = fm.stringWidth(text);
        int textX = (width - textWidth) / 2;
        int textY = (height + fm.getAscent() - fm.getDescent()) / 2;
        
        g2d.drawString(text, textX, textY);
        
        g2d.dispose();
    }
}

package com.revalclan.ui.components;

import com.revalclan.ui.RevalIcons;
import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import java.awt.*;

/**
 * Admin button component with lock icon and admin state management.
 * Changes appearance based on admin status and hover/press states.
 */
public class AdminButton extends JButton {
    private boolean isAdmin = false;
    
    public AdminButton() {
        setPreferredSize(new Dimension(22, 22));
        setMaximumSize(new Dimension(22, 22));
        setMinimumSize(new Dimension(22, 22));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setEnabled(false); // Disabled by default (not admin)
        setVisible(true); // Always visible
    }
    
    /**
     * Set the admin state of the button
     * @param admin true if user is admin, false otherwise
     */
    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
        setEnabled(admin);
        setCursor(admin ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        setToolTipText(admin ? "Admin" : null);
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth();
        int height = getHeight();
        int arc = 6;
        
        if (!isAdmin) {
            g2d.setColor(UIConstants.CARD_BG);
        } else if (getModel().isPressed()) {
            g2d.setColor(new Color(UIConstants.ACCENT_GOLD.getRed(), UIConstants.ACCENT_GOLD.getGreen(), UIConstants.ACCENT_GOLD.getBlue(), 200));
        } else if (getModel().isRollover()) {
            g2d.setColor(new Color(UIConstants.ACCENT_GOLD.getRed(), UIConstants.ACCENT_GOLD.getGreen(), UIConstants.ACCENT_GOLD.getBlue(), 230));
        } else {
            g2d.setColor(UIConstants.ACCENT_GOLD);
        }
        g2d.fillRoundRect(0, 0, width, height, arc, arc);
        
        // Draw lock icon (smaller for smaller button)
        // When disabled, use muted color against dark background; when enabled, use black against gold
        Color iconColor = isAdmin ? Color.BLACK : UIConstants.TEXT_MUTED;
        ImageIcon lockIcon = RevalIcons.createLockIcon(12, iconColor);
        int iconX = (width - lockIcon.getIconWidth()) / 2;
        int iconY = (height - lockIcon.getIconHeight()) / 2;
        lockIcon.paintIcon(this, g2d, iconX, iconY);
        
        g2d.dispose();
    }
}

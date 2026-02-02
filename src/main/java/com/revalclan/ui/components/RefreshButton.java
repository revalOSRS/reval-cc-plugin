package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Reusable refresh button component with hover effect
 */
public class RefreshButton extends JButton {
    private final Color hoverBackground;
    
    /**
     * Create a refresh button with default gold text color and card hover background
     */
    public RefreshButton() {
        this(UIConstants.ACCENT_GOLD, UIConstants.CARD_HOVER);
    }
    
    /**
     * Create a refresh button with custom text color and default hover background
     * 
     * @param textColor The color of the button text
     */
    public RefreshButton(Color textColor) {
        this(textColor, UIConstants.CARD_HOVER);
    }
    
    /**
     * Create a refresh button with custom text color and hover background
     * 
     * @param textColor The color of the button text
     * @param hoverBackground The background color when hovering (null for no hover effect)
     */
    public RefreshButton(Color textColor, Color hoverBackground) {
        super("Refresh");
        this.hoverBackground = hoverBackground;
        
        setFont(new Font("Segoe UI", Font.PLAIN, 11));
        setForeground(textColor);
        setBackground(null);
        setBorder(new EmptyBorder(4, 8, 4, 8));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // Custom paint for hover effect
        if (hoverBackground != null) {
            setOpaque(false);
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        if (hoverBackground != null && getModel().isRollover()) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(hoverBackground);
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2d.dispose();
        }
        super.paintComponent(g);
    }
    
    /**
     * Set the action to perform when clicked
     * 
     * @param action The action to perform
     */
    public void setRefreshAction(Runnable action) {
        // Remove existing action listeners
        for (ActionListener listener : getActionListeners()) {
            removeActionListener(listener);
        }
        // Add new action listener
        if (action != null) {
            addActionListener(e -> action.run());
        }
    }
}

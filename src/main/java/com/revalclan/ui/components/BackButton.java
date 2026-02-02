package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Reusable back button component with consistent styling across panels.
 */
public class BackButton extends JButton {
    private final boolean withHoverEffect;
    
    /**
     * Creates a back button with the standard "← Back" text (simple style).
     * @param onBack Runnable to execute when button is clicked
     */
    public BackButton(Runnable onBack) {
        this("← Back", onBack, false);
    }
    
    /**
     * Creates a back button with custom text (simple style).
     * @param text Button text (e.g., "← Back to Leaderboard")
     * @param onBack Runnable to execute when button is clicked
     */
    public BackButton(String text, Runnable onBack) {
        this(text, onBack, false);
    }
    
    /**
     * Creates a back button with custom text and optional hover effect.
     * @param text Button text
     * @param onBack Runnable to execute when button is clicked
     * @param withHoverEffect Whether to show hover background effect
     */
    public BackButton(String text, Runnable onBack, boolean withHoverEffect) {
        super(text);
        this.withHoverEffect = withHoverEffect;
        
        setFont(new Font("Segoe UI", Font.PLAIN, 11));
        setForeground(withHoverEffect ? UIConstants.TEXT_PRIMARY : UIConstants.ACCENT_GOLD);
        setBackground(UIConstants.BACKGROUND);
        setBorder(new EmptyBorder(0, 0, 0, 0));
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        
        addActionListener(e -> onBack.run());
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        if (withHoverEffect) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            if (getModel().isPressed()) {
                g2d.setColor(UIConstants.CARD_HOVER.darker());
            } else if (getModel().isRollover()) {
                g2d.setColor(UIConstants.CARD_HOVER);
            } else {
                g2d.setColor(UIConstants.BACKGROUND);
            }
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2d.dispose();
        }
        super.paintComponent(g);
    }
}

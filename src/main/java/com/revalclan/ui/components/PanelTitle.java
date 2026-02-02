package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import java.awt.*;

/**
 * Reusable panel title component with consistent styling.
 * Used for section headers like "PENDING RANKUPS", "REVAL CLAN", etc.
 */
public class PanelTitle extends JLabel {
    /**
     * Creates a gold accent title label.
     * @param text Title text
     */
    public PanelTitle(String text) {
        this(text, UIConstants.ACCENT_GOLD);
    }
    
    /**
     * Creates a title label with custom color.
     * @param text Title text
     * @param color Text color
     */
    public PanelTitle(String text, Color color) {
        super(text);
        
        setFont(new Font("Segoe UI", Font.BOLD, 14));
        setForeground(color);
    }
    
    /**
     * Creates a title label with alignment.
     * @param text Title text
     * @param horizontalAlignment SwingConstants alignment (e.g., SwingConstants.CENTER)
     */
    public PanelTitle(String text, int horizontalAlignment) {
        this(text, UIConstants.ACCENT_GOLD);
        setHorizontalAlignment(horizontalAlignment);
    }
}

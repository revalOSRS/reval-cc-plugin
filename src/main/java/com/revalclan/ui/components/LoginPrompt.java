package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Reusable "Log in to view" prompt component.
 */
public class LoginPrompt extends JPanel {
    public LoginPrompt(String featureName) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UIConstants.BACKGROUND);
        setBorder(new EmptyBorder(30, 20, 20, 20));
        
        JLabel titleLabel = new JLabel(featureName.toUpperCase());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(UIConstants.ACCENT_GOLD);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel hint = new JLabel("Log in to view " + featureName.toLowerCase());
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(UIConstants.TEXT_SECONDARY);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        add(titleLabel);
        add(Box.createRigidArea(new Dimension(0, 6)));
        add(hint);
    }
}

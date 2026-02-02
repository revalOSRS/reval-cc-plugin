package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Reusable empty state component with emoji and message.
 */
public class EmptyState extends JPanel {
    public EmptyState(String emoji, String message) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(new EmptyBorder(60, 20, 40, 20));
        
        JLabel emojiLabel = new JLabel(emoji);
        emojiLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        emojiLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        emojiLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel textLabel = new JLabel(message);
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        textLabel.setForeground(UIConstants.TEXT_SECONDARY);
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        add(Box.createVerticalGlue());
        add(emojiLabel);
        add(Box.createRigidArea(new Dimension(0, 8)));
        add(textLabel);
        add(Box.createVerticalGlue());
    }
    
    /**
     * Create a "not logged in" state
     */
    public static EmptyState notLoggedIn(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UIConstants.BACKGROUND);
        panel.setBorder(new EmptyBorder(30, 20, 20, 20));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(UIConstants.ACCENT_GOLD);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel hint = new JLabel("Log in to view " + title.toLowerCase());
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(UIConstants.TEXT_SECONDARY);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        panel.add(titleLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        panel.add(hint);
        
        EmptyState state = new EmptyState("", "");
        state.removeAll();
        state.add(panel);
        return state;
    }
}

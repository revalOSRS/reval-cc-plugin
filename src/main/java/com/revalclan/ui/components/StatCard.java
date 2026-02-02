package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * A compact card displaying a stat with icon, value, and label
 */
public class StatCard extends JPanel {
    public StatCard(String value, String label, Color accentColor) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UIConstants.CARD_BG);
        setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Vertical centering wrapper
        add(Box.createVerticalGlue());
        
        // Value
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        valueLabel.setForeground(accentColor != null ? accentColor : UIConstants.TEXT_PRIMARY);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(valueLabel);
        
        add(Box.createRigidArea(new Dimension(0, 2)));
        
        // Label
        JLabel labelLabel = new JLabel(label);
        labelLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        labelLabel.setForeground(UIConstants.TEXT_SECONDARY);
        labelLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(labelLabel);
        
        add(Box.createVerticalGlue());
    }
    
    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = Math.max(size.height, 50); // Minimum height
        return size;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(getBackground());
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        g2d.dispose();
    }
}


package com.revalclan.ui;

import com.revalclan.api.account.AccountResponse;
import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Panel showing detailed points breakdown for a specific source type
 */
public class PointsBreakdownPanel extends JDialog {
    
    public PointsBreakdownPanel(String title, List<AccountResponse.PointsLogEntry> entries) {
        super((Frame) null, title + " Points Breakdown", true);
        
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);
        setSize(420, 450);
        setLocationRelativeTo(null);
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.BACKGROUND);
        header.setBorder(new EmptyBorder(12, 16, 12, 16));
        
        JLabel titleLabel = new JLabel(title + " Points Breakdown");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(UIConstants.ACCENT_GOLD);
        
        JButton closeButton = new JButton("Close");
        closeButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        closeButton.setForeground(UIConstants.TEXT_PRIMARY);
        closeButton.setBackground(UIConstants.CARD_BG);
        closeButton.setBorder(new EmptyBorder(6, 12, 6, 12));
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dispose());
        
        header.add(titleLabel, BorderLayout.WEST);
        header.add(closeButton, BorderLayout.EAST);
        
        // Content panel with scroll
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIConstants.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(0, 16, 16, 16));
        
        if (entries.isEmpty()) {
            JLabel emptyLabel = new JLabel("No points entries found for this category.");
            emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            emptyLabel.setForeground(UIConstants.TEXT_SECONDARY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            emptyLabel.setBorder(new EmptyBorder(40, 0, 0, 0));
            contentPanel.add(emptyLabel);
        } else {
            // Calculate total points
            int totalPoints = entries.stream()
                .filter(e -> e.getPointsChange() != null && e.getPointsChange() > 0)
                .mapToInt(AccountResponse.PointsLogEntry::getPointsChange)
                .sum();
            
            // Total summary
            JPanel summaryPanel = createSummaryCard("Total Points", String.valueOf(totalPoints));
            contentPanel.add(summaryPanel);
            contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            
            // Entries list
            for (AccountResponse.PointsLogEntry entry : entries) {
                if (entry.getPointsChange() != null && entry.getPointsChange() > 0) {
                    contentPanel.add(createEntryCard(entry));
                    contentPanel.add(Box.createRigidArea(new Dimension(0, 2)));
                }
            }
        }
        
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBackground(UIConstants.BACKGROUND);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }
    
    private JPanel createSummaryCard(String label, String value) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UIConstants.CARD_BG);
        card.setBorder(new EmptyBorder(8, 10, 8, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        
        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        labelComponent.setForeground(UIConstants.TEXT_SECONDARY);
        
        JLabel valueComponent = new JLabel(value);
        valueComponent.setFont(new Font("Segoe UI", Font.BOLD, 14));
        valueComponent.setForeground(UIConstants.ACCENT_GOLD);
        
        card.add(labelComponent, BorderLayout.WEST);
        card.add(valueComponent, BorderLayout.EAST);
        
        return card;
    }
    
    private JPanel createEntryCard(AccountResponse.PointsLogEntry entry) {
        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setBackground(UIConstants.CARD_BG);
        card.setBorder(new EmptyBorder(4, 8, 4, 8));
        // Constrain the card height to prevent expansion
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        card.setPreferredSize(new Dimension(0, 38));
        
        // Left side: Description with date below
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        
        String description = entry.getSourceDescription() != null ? 
            entry.getSourceDescription() : "Unknown source";
        // Truncate long descriptions
        if (description.length() > 55) {
            description = description.substring(0, 52) + "...";
        }
        
        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        descLabel.setForeground(UIConstants.TEXT_PRIMARY);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Date below description
        String dateStr = formatDate(entry.getCreatedAt());
        JLabel dateLabel = new JLabel(dateStr);
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 8));
        dateLabel.setForeground(UIConstants.TEXT_MUTED);
        dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        leftPanel.add(descLabel);
        leftPanel.add(dateLabel);
        
        // Right side: Points
        JLabel pointsLabel = new JLabel("+" + entry.getPointsChange());
        pointsLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        pointsLabel.setForeground(UIConstants.ACCENT_GREEN);
        
        card.add(leftPanel, BorderLayout.CENTER);
        card.add(pointsLabel, BorderLayout.EAST);
        
        return card;
    }
    
    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "Unknown date";
        }
        try {
            // Parse ISO 8601 date
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            Date date = inputFormat.parse(dateStr);
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy");
            return outputFormat.format(date);
        } catch (Exception e) {
            return dateStr;
        }
    }
}

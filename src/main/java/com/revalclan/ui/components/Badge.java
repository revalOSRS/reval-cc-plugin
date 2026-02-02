package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Reusable badge component for difficulty, rarity, category labels.
 */
public class Badge extends JLabel {
    private final Color badgeColor;
    
    public Badge(String text, Color color) {
        super(text);
        this.badgeColor = color;
        
        setFont(new Font("Segoe UI", Font.BOLD, 7));
        setForeground(color);
        setBorder(new EmptyBorder(2, 5, 2, 5));
    }
    
    /**
     * Create a difficulty badge (Easy, Medium, Hard, Elite, Master)
     */
    public static Badge difficulty(String difficulty) {
        Color color = getDifficultyColor(difficulty);
        String label = difficulty != null ? difficulty.substring(0, 1).toUpperCase() + difficulty.substring(1).toLowerCase() : "Unknown";
        return new Badge(label, color);
    }
    
    /**
     * Create a rarity badge (Common, Uncommon, Rare, Epic, Legendary, Mythic)
     */
    public static Badge rarity(String rarity) {
        Color color = getRarityColor(rarity);
        String label = rarity != null ? rarity.substring(0, 1).toUpperCase() + rarity.substring(1).toLowerCase() : "Common";
        return new Badge(label, color);
    }
    
    /**
     * Create a tier badge (Easy, Medium, Hard, Elite)
     */
    public static Badge tier(String tier) {
        Color color = getTierColor(tier);
        String label = tier != null ? tier.substring(0, 1).toUpperCase() + tier.substring(1).toLowerCase() : "Unknown";
        return new Badge(label, color);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(badgeColor.getRed(), badgeColor.getGreen(), badgeColor.getBlue(), 40));
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
        g2d.dispose();
        super.paintComponent(g);
    }
    
    private static Color getDifficultyColor(String difficulty) {
        if (difficulty == null) return UIConstants.TEXT_SECONDARY;
        switch (difficulty.toLowerCase()) {
            case "easy": return UIConstants.TIER_EASY;
            case "medium": return UIConstants.TIER_MEDIUM;
            case "hard": return UIConstants.TIER_HARD;
            case "elite": return UIConstants.TIER_ELITE;
            case "master": return UIConstants.TIER_MASTER;
            case "grandmaster": return UIConstants.TIER_GRANDMASTER;
            default: return UIConstants.TEXT_SECONDARY;
        }
    }
    
    private static Color getRarityColor(String rarity) {
        if (rarity == null) return UIConstants.RARITY_COMMON;
        switch (rarity.toLowerCase()) {
            case "common": return UIConstants.RARITY_COMMON;
            case "uncommon": return UIConstants.RARITY_UNCOMMON;
            case "rare": return UIConstants.RARITY_RARE;
            case "epic": return UIConstants.RARITY_EPIC;
            case "legendary": return UIConstants.RARITY_LEGENDARY;
            case "mythic": return UIConstants.RARITY_MYTHIC;
            default: return UIConstants.RARITY_COMMON;
        }
    }
    
    private static Color getTierColor(String tier) {
        if (tier == null) return UIConstants.TEXT_SECONDARY;
        switch (tier.toLowerCase()) {
            case "easy": return UIConstants.TIER_EASY;
            case "medium": return UIConstants.TIER_MEDIUM;
            case "hard": return UIConstants.TIER_HARD;
            case "elite": return UIConstants.TIER_ELITE;
            default: return UIConstants.TEXT_SECONDARY;
        }
    }
}

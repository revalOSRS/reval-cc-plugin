package com.revalclan.ui.constants;

import java.awt.Color;

/**
 * Centralized UI constants for consistent styling across all UI components.
 * All colors, fonts, and other UI constants should be defined here.
 */
public class UIConstants {
    
    // ========== Background Colors ==========
    
    /** Main background color used for panels and containers */
    public static final Color BACKGROUND = new Color(15, 17, 17);
    
    /** Card background color for elevated content */
    public static final Color CARD_BG = new Color(28, 31, 33);
    
    /** Card hover state background color */
    public static final Color CARD_HOVER = new Color(35, 38, 42);
    
    /** Border color for cards and containers */
    public static final Color BORDER_COLOR = new Color(50, 54, 58);
    
    /** Progress bar background color */
    public static final Color PROGRESS_BG = new Color(40, 43, 47);
    
    /** Row/item background color (darker than card) */
    public static final Color ROW_BG = new Color(22, 24, 26);
    
    /** Header background color (same as card) */
    public static final Color HEADER_BG = CARD_BG;
    
    /** Header hover state background color (same as card hover) */
    public static final Color HEADER_HOVER = CARD_HOVER;
    
    /** Tab active state background */
    public static final Color TAB_ACTIVE = new Color(45, 48, 52);
    
    /** Tab hover state background */
    public static final Color TAB_HOVER = new Color(38, 42, 46);
    
    // ========== Text Colors ==========
    
    /** Primary text color for main content */
    public static final Color TEXT_PRIMARY = new Color(230, 230, 220);
    
    /** Secondary text color for less important content */
    public static final Color TEXT_SECONDARY = new Color(150, 150, 140);
    
    /** Muted text color for disabled or very subtle content */
    public static final Color TEXT_MUTED = new Color(100, 100, 95);
    
    // ========== Accent Colors ==========
    
    /** Gold accent color - used for points, highlights, and important elements */
    public static final Color ACCENT_GOLD = new Color(218, 165, 32);
    
    /** Green accent color - used for success states, positive values, and completion */
    public static final Color ACCENT_GREEN = new Color(139, 195, 74);
    
    /** Alternative green (slightly different shade) - used in some components */
    public static final Color ACCENT_GREEN_ALT = new Color(76, 175, 80);
    
    /** Blue accent color - used for informational elements */
    public static final Color ACCENT_BLUE = new Color(66, 165, 245);
    
    /** Alternative blue (slightly different shade) - used in some components */
    public static final Color ACCENT_BLUE_ALT = new Color(33, 150, 243);
    
    /** Purple accent color - used for special elements */
    public static final Color ACCENT_PURPLE = new Color(171, 71, 188);
    
    /** Orange accent color - used for warnings and medium-priority elements */
    public static final Color ACCENT_ORANGE = new Color(255, 152, 0);
    
    /** Points color (green) - used specifically for displaying points */
    public static final Color POINTS_COLOR = ACCENT_GREEN;
    
    // ========== State Colors ==========
    
    /** Error/red color for errors and negative states */
    public static final Color ERROR_COLOR = new Color(231, 76, 60);
    
    /** Success/green color for completed states */
    public static final Color SUCCESS_COLOR = ACCENT_GREEN_ALT;
    
    /** Task complete background (green with transparency) */
    public static final Color TASK_COMPLETE = new Color(76, 175, 80, 40);
    
    /** Complete background (green with transparency) */
    public static final Color COMPLETE_BG = new Color(76, 175, 80, 25);
    
    // ========== Tier Colors (for diaries, achievements, etc.) ==========
    
    /** Easy tier color */
    public static final Color TIER_EASY = ACCENT_GREEN;
    
    /** Medium tier color */
    public static final Color TIER_MEDIUM = new Color(255, 193, 7);
    
    /** Hard tier color */
    public static final Color TIER_HARD = ACCENT_ORANGE;
    
    /** Elite tier color */
    public static final Color TIER_ELITE = new Color(239, 83, 80);
    
    /** Master tier color */
    public static final Color TIER_MASTER = ACCENT_PURPLE;
    
    /** Grandmaster tier color */
    public static final Color TIER_GRANDMASTER = ACCENT_BLUE;
    
    // ========== Type Colors (for categorization) ==========
    
    /** Combat type color */
    public static final Color TYPE_COMBAT = new Color(239, 83, 80);
    
    /** Skilling type color */
    public static final Color TYPE_SKILLING = ACCENT_GREEN;
    
    /** Collection type color */
    public static final Color TYPE_COLLECTION = ACCENT_PURPLE;
    
    /** Social type color */
    public static final Color TYPE_SOCIAL = ACCENT_BLUE;
    
    /** Exploration type color */
    public static final Color TYPE_EXPLORATION = new Color(255, 193, 7);
    
    /** Boss type color */
    public static final Color TYPE_BOSS = new Color(255, 87, 34);
    
    // ========== Rarity Colors (for achievements, items, etc.) ==========
    
    /** Common rarity color */
    public static final Color RARITY_COMMON = new Color(200, 200, 200);
    
    /** Uncommon rarity color */
    public static final Color RARITY_UNCOMMON = ACCENT_GREEN;
    
    /** Rare rarity color */
    public static final Color RARITY_RARE = ACCENT_BLUE;
    
    /** Epic rarity color */
    public static final Color RARITY_EPIC = ACCENT_PURPLE;
    
    /** Legendary rarity color */
    public static final Color RARITY_LEGENDARY = new Color(255, 193, 7);
    
    /** Mythic rarity color */
    public static final Color RARITY_MYTHIC = new Color(239, 83, 80);
    
    // ========== Private Constructor ==========
    
    private UIConstants() {}
}

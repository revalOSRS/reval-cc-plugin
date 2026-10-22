package com.revalclan.ui.constants;

import java.awt.Color;

/**
 * Centralized UI constants for consistent styling across all UI components.
 * All colors, fonts, and other UI constants should be defined here.
 */
public class UIConstants {
    
    // ========== Background Colors ==========
    // The themed colours (non-final) get their values from setHalloweenTheme, run with
    // false when the class loads; the default and Halloween values are listed there.
    
    /** Main background color used for panels and containers */
    public static Color BACKGROUND;
    
    /** Card background color for elevated content */
    public static Color CARD_BG;
    
    /** Card hover state background color */
    public static Color CARD_HOVER;
    
    /** Border color for cards and containers */
    public static Color BORDER_COLOR;
    
    /** Progress bar background color */
    public static Color PROGRESS_BG;
    
    /** Row/item background color (darker than card) */
    public static Color ROW_BG;
    
    /** Tab active state background */
    public static Color TAB_ACTIVE;
    
    /** Tab hover state background */
    public static Color TAB_HOVER;
    
    // ========== Text Colors ==========
    
    /** Primary text color for main content */
    public static Color TEXT_PRIMARY;
    
    /** Secondary text color for less important content */
    public static Color TEXT_SECONDARY;
    
    /** Muted text color for disabled or very subtle content */
    public static Color TEXT_MUTED;
    
    // ========== Accent Colors ==========
    
    /** Gold accent color - used for points, highlights, and important elements */
    public static Color ACCENT_GOLD;
    
    /** Green accent color - used for success states, positive values, and completion */
    public static final Color ACCENT_GREEN = new Color(139, 195, 74);
    
    /** Blue accent color - used for informational elements */
    public static final Color ACCENT_BLUE = new Color(66, 165, 245);
    
    /** Purple accent color - used for special elements */
    public static final Color ACCENT_PURPLE = new Color(171, 71, 188);
    
    // ========== State Colors ==========
    
    /** Error/red color for errors and negative states */
    public static final Color ERROR_COLOR = new Color(231, 76, 60);
    
    /** Success/green color for completed states */
    public static final Color SUCCESS_COLOR = new Color(76, 175, 80);
    
    /** Task complete background (green with transparency) */
    public static final Color TASK_COMPLETE = new Color(76, 175, 80, 40);
    
    // ========== Tier Colors (for diaries, achievements, etc.) ==========
    
    /** Easy tier color */
    public static final Color TIER_EASY = ACCENT_GREEN;
    
    /** Medium tier color */
    public static final Color TIER_MEDIUM = new Color(255, 193, 7);
    
    /** Hard tier color */
    public static final Color TIER_HARD = new Color(255, 152, 0);
    
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

    // ========== Jack-o'-Lantern seasonal appearance ==========

    public static final Color HALLOWEEN_ACCENT = new Color(245, 163, 75);
    public static final Color HALLOWEEN_TAB = new Color(163, 87, 33);
    public static final Color HALLOWEEN_TAB_BORDER = new Color(209, 138, 69);
    public static final Color HALLOWEEN_TAB_TEXT = new Color(255, 241, 211);
    public static final Color HALLOWEEN_STAT_GREEN = new Color(177, 201, 126);
    public static final Color COBWEB = new Color(224, 203, 164, 50);
    public static final Color PUMPKIN_BODY = new Color(217, 119, 41);
    public static final Color PUMPKIN_LIGHT = new Color(255, 184, 88);
    public static final Color PUMPKIN_OUTLINE = new Color(81, 42, 22);
    public static final Color PUMPKIN_FACE = new Color(65, 36, 25);
    public static final Color PUMPKIN_GLOW = new Color(255, 208, 114);
    public static final Color PUMPKIN_STEM = new Color(126, 153, 97);

    private static boolean halloweenTheme;

    static {
        setHalloweenTheme(false);
    }

    public static boolean isHalloweenTheme() {
        return halloweenTheme;
    }

    /** Apply before constructing the panel tree. Theme changes rebuild that tree on the EDT. */
    public static void setHalloweenTheme(boolean enabled) {
        halloweenTheme = enabled;
        BACKGROUND = enabled ? new Color(18, 18, 15) : new Color(15, 17, 17);
        CARD_BG = enabled ? new Color(33, 27, 22) : new Color(28, 31, 33);
        CARD_HOVER = enabled ? new Color(45, 35, 26) : new Color(35, 38, 42);
        BORDER_COLOR = enabled ? new Color(71, 50, 34) : new Color(50, 54, 58);
        PROGRESS_BG = enabled ? new Color(57, 42, 29) : new Color(40, 43, 47);
        ROW_BG = enabled ? new Color(24, 22, 18) : new Color(22, 24, 26);
        TAB_ACTIVE = enabled ? HALLOWEEN_TAB : new Color(45, 48, 52);
        TAB_HOVER = enabled ? CARD_HOVER : new Color(38, 42, 46);
        TEXT_PRIMARY = enabled ? new Color(231, 225, 210) : new Color(230, 230, 220);
        TEXT_SECONDARY = enabled ? new Color(175, 162, 142) : new Color(150, 150, 140);
        TEXT_MUTED = enabled ? new Color(119, 105, 87) : new Color(100, 100, 95);
        ACCENT_GOLD = enabled ? new Color(227, 182, 104) : new Color(218, 165, 32);
    }

    public static Color panelAccent() {
        return halloweenTheme ? HALLOWEEN_ACCENT : ACCENT_GOLD;
    }
    
    // ========== Private Constructor ==========
    
    private UIConstants() {}
}

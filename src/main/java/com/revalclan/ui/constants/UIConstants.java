package com.revalclan.ui.constants;

import java.awt.Color;

/**
 * Centralized UI constants for consistent styling across all UI components.
 * All colors, fonts, and other UI constants should be defined here.
 */
public class UIConstants {
    
    // ========== Background Colors ==========
    // The themed colours (non-final) get their values from setSeason, run with
    // Season.NONE when the class loads; every season's values are listed there.
    
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

    // ========== Seasonal appearance ==========

    /** Panel title, Refresh button and rank progress bar */
    public static Color SEASON_ACCENT;

    /** Selected navigation tab: fill, seasonal outline and text */
    public static Color NAV_TAB;
    public static Color NAV_TAB_BORDER;
    public static Color NAV_TAB_TEXT;

    /** Profile stat values: clickable point sources, then the other coloured stats */
    public static Color SEASON_STAT;
    public static Color SEASON_STAT_GREEN;

    private static Season season;

    static {
        setSeason(Season.NONE);
    }

    public static Season season() {
        return season;
    }

    public static boolean isSeasonal() {
        return season != Season.NONE;
    }

    /** Apply before constructing the panel tree; the plugin sets it once when it starts. */
    public static void setSeason(Season value) {
        season = value;
        BACKGROUND = pick(new Color(15, 17, 17), new Color(0x1d1411));
        CARD_BG = pick(new Color(28, 31, 33), new Color(0x2d211b));
        CARD_HOVER = pick(new Color(35, 38, 42), new Color(0x3b2c23));
        BORDER_COLOR = pick(new Color(50, 54, 58), new Color(0x5a4334));
        PROGRESS_BG = pick(new Color(40, 43, 47), new Color(0x5a4334));
        ROW_BG = pick(new Color(22, 24, 26), new Color(0x221814));
        TEXT_PRIMARY = pick(new Color(230, 230, 220), new Color(0xf8eee2));
        TEXT_SECONDARY = pick(new Color(150, 150, 140), new Color(0xd3bca6));
        TEXT_MUTED = pick(new Color(100, 100, 95), new Color(0x977f6b));
        ACCENT_GOLD = pick(new Color(218, 165, 32), new Color(0xf1c47c));
        SEASON_ACCENT = pick(ACCENT_GOLD, new Color(0xfff1e0));
        NAV_TAB = pick(ACCENT_BLUE, new Color(0xc23a4c));
        NAV_TAB_BORDER = pick(ACCENT_BLUE, new Color(0xfbe3e5));
        NAV_TAB_TEXT = pick(Color.WHITE, new Color(0xfff6f2));
        SEASON_STAT = pick(ACCENT_GOLD, new Color(0xf5a9ae));
        SEASON_STAT_GREEN = pick(ACCENT_GREEN, new Color(0xa9dfb9));
        TAB_ACTIVE = isSeasonal() ? NAV_TAB : new Color(45, 48, 52);
        TAB_HOVER = isSeasonal() ? CARD_HOVER : new Color(38, 42, 46);
    }

    private static Color pick(Color plain, Color christmas) {
        return season == Season.CHRISTMAS ? christmas : plain;
    }

    public static Color panelAccent() {
        return SEASON_ACCENT;
    }
    
    // ========== Private Constructor ==========
    
    private UIConstants() {}
}

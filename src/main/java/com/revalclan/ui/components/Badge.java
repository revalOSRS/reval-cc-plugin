package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class Badge extends JLabel {
	private final Color badgeColor;

	public Badge(String text, Color color) {
		super(text);
		this.badgeColor = color;
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(color);
		setBorder(new EmptyBorder(2, 5, 2, 5));
	}

	public static Badge difficulty(String difficulty) {
		return new Badge(capitalize(difficulty), getDifficultyColor(difficulty));
	}

	public static Badge rarity(String rarity) {
		return new Badge(capitalize(rarity), getRarityColor(rarity));
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

	private static String capitalize(String s) {
		if (s == null || s.isEmpty()) return "Unknown";
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
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
}

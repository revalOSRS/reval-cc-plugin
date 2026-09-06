package com.revalclan.util;

import java.awt.Color;

/** Event team colours: parsing the backend's hex, and keeping them readable on the dark panel. */
public final class TeamColor {
	/** Teams whose colour is unset get the backend's default gray. */
	public static final Color FALLBACK = new Color(0x888888);

	private TeamColor() {
	}

	public static Color parse(String hex) {
		if (hex == null || hex.isEmpty()) return FALLBACK;
		try {
			return Color.decode(hex);
		} catch (NumberFormatException e) {
			return FALLBACK;
		}
	}

	/** Near-white and near-black team colours vanish on the panel background; nudge them. */
	public static Color onDarkPanel(Color c) {
		int sum = c.getRed() + c.getGreen() + c.getBlue();
		if (sum > 720) return new Color(220, 220, 220);
		if (sum < 60) return new Color(140, 140, 140);
		return c;
	}
}

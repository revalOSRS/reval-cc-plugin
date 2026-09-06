package com.revalclan.util;

import java.awt.Color;

/** Colour helpers shared by painted components. */
public final class Colors {
	private Colors() {
	}

	public static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}
}

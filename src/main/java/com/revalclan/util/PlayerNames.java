package com.revalclan.util;

import java.util.Locale;

/** Normalizes OSRS display names for map lookups (non-breaking spaces, case). */
public final class PlayerNames {
	private PlayerNames() {
	}

	public static String normalize(String name) {
		if (name == null) {
			return "";
		}
		return name.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
	}
}

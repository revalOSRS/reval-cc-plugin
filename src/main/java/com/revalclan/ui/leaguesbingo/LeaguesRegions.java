package com.revalclan.ui.leaguesbingo;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

/**
 * The 13 fixed Leagues Bingo regions, mirroring the app's regions.ts so the
 * side panel uses the same names, order and accent colors as the homepage.
 */
public final class LeaguesRegions {
	public static final class Region {
		public final String id;
		public final String displayName;
		public final Color accent;

		Region(String id, String displayName, int rgb) {
			this.id = id;
			this.displayName = displayName;
			this.accent = new Color(rgb);
		}
	}

	public static final List<Region> ALL = Arrays.asList(
		new Region("varlamore", "Varlamore", 0xe8c258),
		new Region("karamja", "Karamja", 0x57a64a),
		new Region("asgarnia", "Asgarnia", 0x4f7ee3),
		new Region("desert", "Desert", 0xd1913f),
		new Region("fremennik", "Fremennik", 0x9ec7dd),
		new Region("kandarin", "Kandarin", 0xd9534f),
		new Region("morytania", "Morytania", 0x9b59b6),
		new Region("tirannwn", "Tirannwn", 0x45c48f),
		new Region("wilderness", "Wilderness", 0x8b8b8b),
		new Region("kourend", "Kourend", 0x3aa6a6),
		new Region("misthalin", "Misthalin", 0x6faedb),
		new Region("global", "Global", 0xe3b341),
		new Region("sailing", "Sailing", 0x3b9ec9)
	);

	private static final Region UNKNOWN = new Region("unknown", "Unknown", 0x8b8b8b);

	public static Region byId(String id) {
		for (Region r : ALL) {
			if (r.id.equalsIgnoreCase(id)) return r;
		}
		return id == null ? UNKNOWN : new Region(id, capitalize(id), 0x8b8b8b);
	}

	/** Position of a region in the canonical order; unknown ids sort last. */
	public static int order(String id) {
		for (int i = 0; i < ALL.size(); i++) {
			if (ALL.get(i).id.equalsIgnoreCase(id)) return i;
		}
		return ALL.size();
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private LeaguesRegions() {
	}
}

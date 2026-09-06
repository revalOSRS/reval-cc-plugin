package com.revalclan.ui.leaguesbingo;

import net.runelite.api.gameval.SpriteID;

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
		/** Area banner from the game cache (Leagues map shield), or -1. */
		public final int bannerSprite;
		/** Same banner with the game's highlight border, for a finished board; or -1. */
		public final int bannerHighlightSprite;

		Region(String id, String displayName, int rgb, int bannerSprite, int bannerHighlightSprite) {
			this.id = id;
			this.displayName = displayName;
			this.accent = new Color(rgb);
			this.bannerSprite = bannerSprite;
			this.bannerHighlightSprite = bannerHighlightSprite;
		}
	}

	/*
	 * Banner sprites are the Leagues VI area shields (League6MapShields02: a
	 * plain 20x30 set followed by a highlighted 22x32 set in the same order).
	 * The order was checked against the wiki's area badges, slot by slot:
	 * 0 Misthalin, 1 Karamja, 2 Asgarnia, 3 Desert, 4 Morytania, 5 Wilderness,
	 * 6 Kandarin, 7 Fremennik, 8 Tirannwn, 9 Kourend, 10 Varlamore. Slot 11
	 * (crossed swords) has no area of its own and stands in for Global;
	 * Sailing uses the skill icon.
	 */
	public static final List<Region> ALL = Arrays.asList(
		new Region("varlamore", "Varlamore", 0xe8c258, SpriteID.League6MapShields02._10, SpriteID.League6MapShields02._22),
		new Region("karamja", "Karamja", 0x57a64a, SpriteID.League6MapShields02._1, SpriteID.League6MapShields02._13),
		new Region("asgarnia", "Asgarnia", 0x4f7ee3, SpriteID.League6MapShields02._2, SpriteID.League6MapShields02._14),
		new Region("desert", "Desert", 0xd1913f, SpriteID.League6MapShields02._3, SpriteID.League6MapShields02._15),
		new Region("fremennik", "Fremennik", 0x9ec7dd, SpriteID.League6MapShields02._7, SpriteID.League6MapShields02._19),
		new Region("kandarin", "Kandarin", 0xd9534f, SpriteID.League6MapShields02._6, SpriteID.League6MapShields02._18),
		new Region("morytania", "Morytania", 0x9b59b6, SpriteID.League6MapShields02._4, SpriteID.League6MapShields02._16),
		new Region("tirannwn", "Tirannwn", 0x45c48f, SpriteID.League6MapShields02._8, SpriteID.League6MapShields02._20),
		new Region("wilderness", "Wilderness", 0x8b8b8b, SpriteID.League6MapShields02._5, SpriteID.League6MapShields02._17),
		new Region("kourend", "Kourend", 0x3aa6a6, SpriteID.League6MapShields02._9, SpriteID.League6MapShields02._21),
		new Region("misthalin", "Misthalin", 0x6faedb, SpriteID.League6MapShields02._0, SpriteID.League6MapShields02._12),
		new Region("global", "Global", 0xe3b341, SpriteID.League6MapShields02._11, SpriteID.League6MapShields02._23),
		new Region("sailing", "Sailing", 0x3b9ec9, SpriteID.Staticons2.SAILING, SpriteID.Staticons2.SAILING)
	);

	private static final Region UNKNOWN = new Region("unknown", "Unknown", 0x8b8b8b, -1, -1);

	public static Region byId(String id) {
		for (Region r : ALL) {
			if (r.id.equalsIgnoreCase(id)) return r;
		}
		return id == null ? UNKNOWN : new Region(id, capitalize(id), 0x8b8b8b, -1, -1);
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

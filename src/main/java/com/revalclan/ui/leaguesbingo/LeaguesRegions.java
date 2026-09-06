package com.revalclan.ui.leaguesbingo;

import lombok.Value;
import net.runelite.api.gameval.SpriteID;

import java.awt.Color;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The 13 fixed Leagues Bingo regions, mirroring the app's regions.ts so the
 * side panel uses the same names, order and accent colors as the homepage.
 */
public final class LeaguesRegions {
	@Value
	public static class Region {
		String id;
		String displayName;
		Color accent;
		/** Area banner from the game cache (Leagues map shield), or -1. */
		int bannerSprite;
		/** Same banner with the game's highlight border, for a finished board; or -1. */
		int bannerHighlightSprite;
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
		new Region("varlamore", "Varlamore", new Color(0xe8c258), SpriteID.League6MapShields02._10, SpriteID.League6MapShields02._22),
		new Region("karamja", "Karamja", new Color(0x57a64a), SpriteID.League6MapShields02._1, SpriteID.League6MapShields02._13),
		new Region("asgarnia", "Asgarnia", new Color(0x4f7ee3), SpriteID.League6MapShields02._2, SpriteID.League6MapShields02._14),
		new Region("desert", "Desert", new Color(0xd1913f), SpriteID.League6MapShields02._3, SpriteID.League6MapShields02._15),
		new Region("fremennik", "Fremennik", new Color(0x9ec7dd), SpriteID.League6MapShields02._7, SpriteID.League6MapShields02._19),
		new Region("kandarin", "Kandarin", new Color(0xd9534f), SpriteID.League6MapShields02._6, SpriteID.League6MapShields02._18),
		new Region("morytania", "Morytania", new Color(0x9b59b6), SpriteID.League6MapShields02._4, SpriteID.League6MapShields02._16),
		new Region("tirannwn", "Tirannwn", new Color(0x45c48f), SpriteID.League6MapShields02._8, SpriteID.League6MapShields02._20),
		new Region("wilderness", "Wilderness", new Color(0x8b8b8b), SpriteID.League6MapShields02._5, SpriteID.League6MapShields02._17),
		new Region("kourend", "Kourend", new Color(0x3aa6a6), SpriteID.League6MapShields02._9, SpriteID.League6MapShields02._21),
		new Region("misthalin", "Misthalin", new Color(0x6faedb), SpriteID.League6MapShields02._0, SpriteID.League6MapShields02._12),
		new Region("global", "Global", new Color(0xe3b341), SpriteID.League6MapShields02._11, SpriteID.League6MapShields02._23),
		new Region("sailing", "Sailing", new Color(0x3b9ec9), SpriteID.Staticons2.SAILING, SpriteID.Staticons2.SAILING)
	);

	private static final Map<String, Region> BY_ID = new LinkedHashMap<>();
	/** Ids the backend may send that we do not know; one placeholder each, built once. */
	private static final Map<String, Region> UNKNOWN = new ConcurrentHashMap<>();
	private static final Color UNKNOWN_ACCENT = new Color(0x8b8b8b);

	static {
		for (Region r : ALL) BY_ID.put(r.getId(), r);
	}

	public static Region byId(String id) {
		String key = id == null ? "" : id.toLowerCase();
		Region known = BY_ID.get(key);
		if (known != null) return known;
		return UNKNOWN.computeIfAbsent(key, k ->
			new Region(k, k.isEmpty() ? "Unknown" : Character.toUpperCase(k.charAt(0)) + k.substring(1), UNKNOWN_ACCENT, -1, -1));
	}

	/** Position of a region in the canonical order; unknown ids sort last. */
	public static int order(String id) {
		int i = 0;
		for (Region r : ALL) {
			if (r.getId().equalsIgnoreCase(id)) return i;
			i++;
		}
		return ALL.size();
	}

	private LeaguesRegions() {
	}
}

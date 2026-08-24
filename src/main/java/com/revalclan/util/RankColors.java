package com.revalclan.util;

import com.revalclan.ui.constants.UIConstants;

import java.awt.Color;
import java.util.Map;

/** Accent color per clan rank slug, loosely matching each rank icon. */
public final class RankColors {
	private static final Map<String, Color> BY_SLUG = Map.ofEntries(
		Map.entry("mentor", new Color(0xB08D57)),
		Map.entry("prefect", new Color(0xC0C0C8)),
		Map.entry("leader", new Color(0xE0B84F)),
		Map.entry("supervisor", new Color(0x565B66)),
		Map.entry("superior", new Color(0x4C7FD6)),
		Map.entry("executive", new Color(0x4FBF5A)),
		Map.entry("senator", new Color(0x6FA8DC)),
		Map.entry("monarch", new Color(0x9B59B6)),
		Map.entry("red_topaz", new Color(0xD9663C)),
		Map.entry("sapphire", new Color(0x3B6FD9)),
		Map.entry("emerald", new Color(0x2FBF71)),
		Map.entry("ruby", new Color(0xD93B5A)),
		Map.entry("diamond", new Color(0xD8D8E8)),
		Map.entry("dragonstone", new Color(0xB05CD9)),
		Map.entry("onyx", new Color(0x8E6FC0)),
		Map.entry("zenyte", new Color(0xE08A3C)),
		Map.entry("marshal", new Color(0xFFC83C))
	);

	private RankColors() {
	}

	/** Falls back to gold for unknown or missing ranks. */
	public static Color forSlug(String rankSlug) {
		Color color = rankSlug != null ? BY_SLUG.get(rankSlug) : null;
		return color != null ? color : UIConstants.ACCENT_GOLD;
	}
}

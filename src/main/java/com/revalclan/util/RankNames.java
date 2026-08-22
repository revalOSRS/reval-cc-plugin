package com.revalclan.util;

/**
 * Formats web rank slugs (e.g. "red_topaz") as in-game rank titles ("Red Topaz").
 * Rank names on the backend are the in-game clan ranks since the single-line
 * rank rework, so this is the only mapping the plugin needs.
 */
public final class RankNames {
	private RankNames() {}

	public static String display(String rank) {
		if (rank == null || rank.isEmpty()) return "?";
		StringBuilder out = new StringBuilder();
		for (String word : rank.split("[_\\s]+")) {
			if (word.isEmpty()) continue;
			if (out.length() > 0) out.append(' ');
			out.append(Character.toUpperCase(word.charAt(0)));
			if (word.length() > 1) out.append(word.substring(1).toLowerCase());
		}
		return out.length() > 0 ? out.toString() : rank;
	}
}

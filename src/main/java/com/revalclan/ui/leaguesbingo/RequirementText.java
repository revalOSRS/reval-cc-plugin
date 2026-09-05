package com.revalclan.ui.leaguesbingo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One-line human descriptions of tile requirements, ported from the
 * homepage's tile modal so both surfaces read the same way.
 */
public final class RequirementText {
	private RequirementText() {
	}

	public static String describe(JsonObject r) {
		String type = str(r, "type", "");
		switch (type) {
			case "ITEM_DROP": {
				List<String> names = itemNames(r);
				int total = num(r, "totalAmount", 0);
				if (total > 0) {
					return bool(r, "allowDuplicates")
						? "Any " + total + " of " + names.size() + " items"
						: total + " unique of " + names.size() + " items";
				}
				if (names.size() <= 1) return "Obtain " + (names.isEmpty() ? "item" : names.get(0));
				return "Collect all " + names.size() + " items";
			}
			case "GP_EARNING": return "Earn " + gp(num(r, "targetValue", num(r, "value", 0))) + " GP from the list";
			case "VALUE_DROP": return "A single drop worth " + gp(num(r, "minValue", num(r, "value", 0))) + "+";
			case "EXPERIENCE":
			case "SKILL_XP": return fmt(num(r, "experience", 0)) + " " + str(r, "skill", "") + " XP";
			case "SPEEDRUN": return str(r, "location", "Speedrun") + " under " + mmss(num(r, "goalSeconds", num(r, "time", 0)));
			case "KILL_COUNT": return numOr(r, "count") + "x " + str(r, "target", str(r, "npcName", "?")) + " KC";
			case "DETAILED_KILL": return num(r, "count", 1) + "x " + str(r, "target", "?");
			case "PET": {
				JsonArray pets = r.has("pets") && r.get("pets").isJsonArray() ? r.getAsJsonArray("pets") : null;
				if (pets != null && pets.size() > 0) {
					List<String> names = new ArrayList<>();
					for (JsonElement p : pets) {
						if (p.isJsonObject()) names.add(str(p.getAsJsonObject(), "petName", "?"));
					}
					return "Pet (" + num(r, "anyCount", 1) + " of " + pets.size() + "): " + String.join(", ", names);
				}
				return "Pet: " + str(r, "petName", "?");
			}
			case "ALPHABET_KILL": return "Kill a boss for each letter";
			case "PUZZLE": return str(r, "displayName", "Puzzle");
			case "COMBAT_ACHIEVEMENT": return "CA: " + str(r, "achievementName", str(r, "tier", "?"));
			case "COMBAT_ACHIEVEMENT_TIER": return "All " + str(r, "tier", "?") + "-tier combat achievements";
			case "QUEST": return "Quest: " + str(r, "questName", "?");
			case "ACHIEVEMENT_DIARY": return (str(r, "diaryName", "") + " diary (" + str(r, "tier", "?") + ")").trim();
			case "COLLECTION_LOG": return numOr(r, "count") + " collection log slots";
			case "COLLECTION_LOG_TIER": return "Collection log tier: " + str(r, "tier", "?");
			case "KILL_STREAK": return numOr(r, "count") + " " + str(r, "target", "?") + " kills in a row, no deaths";
			case "SKILL_LEVEL": return (str(r, "skill", "") + " level " + numOr(r, "level")).trim();
			case "TOTAL_LEVEL": return "Total level " + numOr(r, "level");
			case "TOTAL_XP": return fmt(num(r, "experience", 0)) + " total XP";
			case "PERSONAL_BEST": return "New PB: " + str(r, "location", "?");
			case "CLUE_COMPLETION": return numOr(r, "count") + "x " + str(r, "tier", "?") + " clue";
			case "CLUE_ITEM": {
				List<String> names = itemNames(r);
				return "Clue item: " + (names.isEmpty() ? "?" : String.join(", ", names));
			}
			case "BA_GAMBLES": return numOr(r, "count") + " BA high gambles";
			case "MINIGAME_SCORE": return str(r, "minigame", "?") + ": " + num(r, "score", 0) + " score";
			case "CHAT_MESSAGE": return "Message: \"" + str(r, "message", "?") + "\"";
			case "EMOTE": return "Emote: " + str(r, "emoteName", "?");
			case "MUSIC_PLAYED": return "Play track: " + str(r, "trackName", "?");
			case "MANUAL": {
				String v = str(r, "verificationDescription", "");
				return v.isEmpty() ? "Manually verified by an admin" : v;
			}
			case "PET_COUNT": return numOr(r, "count") + " unique pets";
			case "ITEM_DROP_REQUIREMENT": return num(r, "requiredCount", 2) + "+ items in a single drop";
			case "LEAGUES_AREA": return "Unlock area: " + str(r, "areaName", "?");
			case "LEAGUES_RELIC": {
				String name = str(r, "relicName", "");
				return "Relic: " + (name.isEmpty() ? "tier " + numOr(r, "relicTier") : name);
			}
			case "LEAGUES_TASK_COUNT": return numOr(r, "minTaskCount") + " league tasks";
			case "LEAGUES_POINTS": return numOr(r, "minPoints") + " league points";
			case "LEAGUES_MASTERY": return numOr(r, "minNodes") + " pact nodes";
			case "COMPOUND": return "All sub-requirements";
			case "CHOICE": return num(r, "requiredCount", 1) + " of the options";
			case "SCHEDULED": return "Scheduled (time window)";
			default: return type.isEmpty() ? "Requirement" : type;
		}
	}

	/** Item names for ITEM_DROP style requirements, in order. */
	public static List<String> itemNames(JsonObject r) {
		List<String> names = new ArrayList<>();
		if (r == null || !r.has("items") || !r.get("items").isJsonArray()) return names;
		for (JsonElement e : r.getAsJsonArray("items")) {
			if (!e.isJsonObject()) continue;
			String n = str(e.getAsJsonObject(), "itemName", null);
			if (n != null) names.add(n);
		}
		return names;
	}

	public static String str(JsonObject o, String key, String fallback) {
		if (o == null) return fallback;
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) return fallback;
		try {
			return e.getAsString();
		} catch (Exception ex) {
			return fallback;
		}
	}

	public static int num(JsonObject o, String key, int fallback) {
		if (o == null) return fallback;
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) return fallback;
		try {
			return (int) Math.round(e.getAsDouble());
		} catch (Exception ex) {
			return fallback;
		}
	}

	public static Double number(JsonObject o, String key) {
		if (o == null) return null;
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) return null;
		try {
			return e.getAsDouble();
		} catch (Exception ex) {
			return null;
		}
	}

	private static String numOr(JsonObject o, String key) {
		Double d = number(o, key);
		return d == null ? "?" : fmt(d.intValue());
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		try {
			return e != null && !e.isJsonNull() && e.getAsBoolean();
		} catch (Exception ex) {
			return false;
		}
	}

	public static String fmt(long n) {
		return String.format("%,d", n);
	}

	public static String gp(long n) {
		if (n >= 1_000_000_000L) return trim(n / 1_000_000_000d) + "B";
		if (n >= 1_000_000L) return trim(n / 1_000_000d) + "M";
		if (n >= 1_000L) return trim(n / 1_000d) + "K";
		return String.valueOf(n);
	}

	private static String trim(double d) {
		String s = String.format("%.1f", d);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static String mmss(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}

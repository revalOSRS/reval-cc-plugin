package com.revalclan.ui.leaguesbingo;

import com.google.gson.JsonObject;
import com.revalclan.util.Json;
import com.revalclan.util.NumberFmt;

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
		// Admins can attach their own wording; it beats anything we derive.
		String custom = Json.str(r, "description", "").trim();
		return custom.isEmpty() ? derive(r) : custom;
	}

	/** Everything a requirement lets the team choose from: items, then pets. */
	public static List<String> optionNames(JsonObject r) {
		List<String> names = itemNames(r);
		names.addAll(petNames(r));
		return names;
	}

	/** Item names for ITEM_DROP style requirements, in order. */
	public static List<String> itemNames(JsonObject r) {
		return Json.strings(Json.array(r, "items"), "itemName");
	}

	private static List<String> petNames(JsonObject r) {
		return Json.strings(Json.array(r, "pets"), "petName");
	}

	private static String derive(JsonObject r) {
		String type = Json.str(r, "type", "");
		switch (type) {
			case "ITEM_DROP": {
				List<String> names = itemNames(r);
				int total = Json.num(r, "totalAmount", 0);
				if (total > 0) {
					return Json.bool(r, "allowDuplicates")
						? "Any " + total + " of " + names.size() + " items"
						: total + " unique of " + names.size() + " items";
				}
				if (names.size() <= 1) return "Obtain " + (names.isEmpty() ? "item" : names.get(0));
				return "Collect all " + names.size() + " items";
			}
			case "GP_EARNING": return "Earn " + NumberFmt.compact(Json.num(r, "targetValue", Json.num(r, "value", 0))) + " GP from the list";
			case "VALUE_DROP": return "A single drop worth " + NumberFmt.compact(Json.num(r, "minValue", Json.num(r, "value", 0))) + "+";
			case "EXPERIENCE":
			case "SKILL_XP": return NumberFmt.group(Json.num(r, "experience", 0)) + " " + Json.str(r, "skill", "") + " XP";
			case "SPEEDRUN": return Json.str(r, "location", "Speedrun") + " under " + mmss(Json.num(r, "goalSeconds", Json.num(r, "time", 0)));
			case "KILL_COUNT": return numOr(r, "count") + "x " + Json.str(r, "target", Json.str(r, "npcName", "?")) + " KC";
			case "DETAILED_KILL": return Json.num(r, "count", 1) + "x " + Json.str(r, "target", "?");
			case "PET": {
				List<String> pets = petNames(r);
				if (pets.size() > 1) return Json.num(r, "anyCount", Json.num(r, "totalAmount", 1)) + " unique of " + pets.size() + " pets";
				if (pets.size() == 1) return "Pet: " + pets.get(0);
				return "Pet: " + Json.str(r, "petName", "?");
			}
			case "ALPHABET_KILL": return "Kill a boss for each letter";
			case "PUZZLE": return Json.str(r, "displayName", "Puzzle");
			case "COMBAT_ACHIEVEMENT": return "CA: " + Json.str(r, "achievementName", Json.str(r, "tier", "?"));
			case "COMBAT_ACHIEVEMENT_TIER": return "All " + Json.str(r, "tier", "?") + "-tier combat achievements";
			case "QUEST": return "Quest: " + Json.str(r, "questName", "?");
			case "ACHIEVEMENT_DIARY": return (Json.str(r, "diaryName", "") + " diary (" + Json.str(r, "tier", "?") + ")").trim();
			case "COLLECTION_LOG": return numOr(r, "count") + " collection log slots";
			case "COLLECTION_LOG_TIER": return "Collection log tier: " + Json.str(r, "tier", "?");
			case "KILL_STREAK": return numOr(r, "count") + " " + Json.str(r, "target", "?") + " kills in a row, no deaths";
			case "SKILL_LEVEL": return (Json.str(r, "skill", "") + " level " + numOr(r, "level")).trim();
			case "TOTAL_LEVEL": return "Total level " + numOr(r, "level");
			case "TOTAL_XP": return NumberFmt.group(Json.num(r, "experience", 0)) + " total XP";
			case "PERSONAL_BEST": return "New PB: " + Json.str(r, "location", "?");
			case "CLUE_COMPLETION": return numOr(r, "count") + "x " + Json.str(r, "tier", "?") + " clue";
			case "CLUE_ITEM": {
				List<String> names = itemNames(r);
				return "Clue item: " + (names.isEmpty() ? "?" : String.join(", ", names));
			}
			case "BA_GAMBLES": return numOr(r, "count") + " BA high gambles";
			case "MINIGAME_SCORE": return Json.str(r, "minigame", "?") + ": " + Json.num(r, "score", 0) + " score";
			case "CHAT_MESSAGE": {
				String message = Json.str(r, "message", Json.str(r, "pattern", ""));
				int count = Json.num(r, "count", 1);
				if (message.isEmpty()) return count > 1 ? count + "x game message" : "Game message";
				return (count > 1 ? count + "x " : "") + "\"" + message + "\"";
			}
			case "EMOTE": return "Emote: " + Json.str(r, "emoteName", "?");
			case "MUSIC_PLAYED": return "Play track: " + Json.str(r, "trackName", "?");
			case "MANUAL": {
				String v = Json.str(r, "verificationDescription", "");
				return v.isEmpty() ? "Manually verified by an admin" : v;
			}
			case "PET_COUNT": return numOr(r, "count") + " unique pets";
			case "ITEM_DROP_REQUIREMENT": return Json.num(r, "requiredCount", 2) + "+ items in a single drop";
			case "LEAGUES_AREA": return "Unlock area: " + Json.str(r, "areaName", "?");
			case "LEAGUES_RELIC": {
				String name = Json.str(r, "relicName", "");
				return "Relic: " + (name.isEmpty() ? "tier " + numOr(r, "relicTier") : name);
			}
			case "LEAGUES_TASK_COUNT": return numOr(r, "minTaskCount") + " league tasks";
			case "LEAGUES_POINTS": return numOr(r, "minPoints") + " league points";
			case "LEAGUES_MASTERY": return numOr(r, "minNodes") + " pact nodes";
			case "COMPOUND": return "All sub-requirements";
			case "CHOICE": return Json.num(r, "requiredCount", 1) + " of the options";
			case "SCHEDULED": return "Scheduled (time window)";
			default: return type.isEmpty() ? "Requirement" : type;
		}
	}

	private static String numOr(JsonObject o, String key) {
		Double d = Json.number(o, key);
		return d == null ? "?" : NumberFmt.group(d.longValue());
	}

	private static String mmss(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}

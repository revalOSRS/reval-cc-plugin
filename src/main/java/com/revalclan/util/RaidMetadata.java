package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native numeric evidence for a snapshot whose lifecycle belongs to RaidPartyTracker. */
final class RaidMetadata {
	final String name;
	private String mode;
	private Integer partySize;
	private Integer partySizeAtStart;
	private Integer scale;
	private Integer invocationLevel;
	private Integer invocationLevelAtStart;
	Integer completionCount;
	RaidMetadata(String name) { this.name = name; }

	void complete(String source, int count) {
		mode = modeOf(source);
		completionCount = count;
	}

	boolean matchesCompletion(String source, int count) {
		return completionCount != null && completionCount == count && mode.equals(modeOf(source));
	}

	void observe(Client client, boolean starting) {
		int size;
		if (RaidPartyTracker.CHAMBERS_OF_XERIC.equals(name)) {
			size = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE);
			int scale = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED);
			if (scale > 0) this.scale = scale;
			if (completionCount == null) mode = client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE) == 1
				? "challenge" : "normal";
		} else if (RaidPartyTracker.THEATRE_OF_BLOOD.equals(name)) {
			size = occupied(client, VarbitID.TOB_CLIENT_P0, 5);
		} else if (RaidPartyTracker.TOMBS_OF_AMASCUT.equals(name)) {
			size = occupied(client, VarbitID.TOA_CLIENT_P0, 8);
			int level = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
			if (size > 0 && level >= 0 && level <= 600) {
				invocationLevel = level;
				if (starting) invocationLevelAtStart = Math.max(invocationLevelAtStart == null
					? 0 : invocationLevelAtStart, level);
			}
		} else {
			size = occupied(client, VarbitID.TFA_CLIENT_P0, 8);
		}
		if (size > 0) {
			partySize = size;
			if (starting) partySizeAtStart = Math.max(partySizeAtStart == null ? 0 : partySizeAtStart, size);
		}
	}

	private int occupied(Client client, int first, int slots) {
		int count = 0;
		for (int i = 0; i < slots; i++) if (client.getVarbitValue(first + i) > 0) count++;
		return count;
	}

	private static String modeOf(String source) {
		String lower = source.toLowerCase(java.util.Locale.ROOT);
		if (lower.contains("challenge mode")) return "challenge";
		if (lower.contains("hard mode")) return "hard";
		if (lower.contains("entry mode")) return "entry";
		if (lower.contains("expert mode")) return "expert";
		return "normal";
	}

	void addTo(Map<String, Object> data) {
		Map<String, Object> raid = new LinkedHashMap<>();
		raid.put("name", name);
		if (mode != null) raid.put("mode", mode);
		if (partySize != null) raid.put("partySize", partySize);
		if (partySizeAtStart != null) raid.put("partySizeAtStart", partySizeAtStart);
		Integer effectiveScale = scale;
		if (partySize != null && RaidPartyTracker.FRACTURED_ARCHIVE.equals(name)) effectiveScale = Math.max(2, partySize);
		if (partySize != null && RaidPartyTracker.THEATRE_OF_BLOOD.equals(name) && mode != null) {
			effectiveScale = "entry".equals(mode) ? partySize : Math.max(3, partySize);
		}
		if (effectiveScale != null) raid.put("scale", effectiveScale);
		if (invocationLevel != null) raid.put("invocationLevel", invocationLevel);
		if (invocationLevelAtStart != null) raid.put("invocationLevelAtStart", invocationLevelAtStart);
		if (completionCount != null) raid.put("completionCount", completionCount);
		data.put("raid", raid);
	}
}

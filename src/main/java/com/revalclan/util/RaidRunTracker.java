package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

/** Native size/difficulty evidence, retained with the completion that produced a reward. */
@Singleton
public class RaidRunTracker {
	@Inject private Client client;
	@Inject private RaidDeathTracker deathTracker;
	private final Map<String, Run> completions = new LinkedHashMap<>();
	private Run active;
	private int startTick;
	private int outsideTicks;
	private boolean sawStart;
	private boolean finishOnTick;

	void onChatMessage(String message) {
		String started = RaidDeathTracker.startedRaid(message);
		if (started != null) {
			active = new Run(started);
			completions.remove(started);
			startTick = client.getTickCount();
			sawStart = true;
			outsideTicks = 0;
			finishOnTick = false;
			return;
		}
		Matcher match = RaidPartyTracker.COMPLETED.matcher(message);
		if (!match.matches()) return;
		String raid = RaidPartyTracker.raidOf(match.group(1));
		if (raid == null || externalSpectator(raid)) return;
		int count;
		try { count = Integer.parseInt(match.group(2).replace(",", "")); }
		catch (NumberFormatException e) { return; }
		Run previous = completions.get(raid);
		if (previous != null && previous.completionCount != null && previous.completionCount == count) return;
		if (active == null || !active.name.equals(raid) || active.completionCount != null) {
			active = new Run(raid);
			sawStart = false;
		}
		active.mode = modeOf(match.group(1));
		active.completionCount = count;
		observe();
		completions.put(raid, active);
		finishOnTick = true;
	}

	void onGameTick() {
		if (client.getGameState() != GameState.LOGGED_IN) return;
		if (active == null) {
			for (String raid : new String[] {RaidPartyTracker.CHAMBERS_OF_XERIC, RaidPartyTracker.THEATRE_OF_BLOOD,
				RaidPartyTracker.TOMBS_OF_AMASCUT, RaidPartyTracker.FRACTURED_ARCHIVE}) {
				if (deathTracker.isInside(raid) && !externalSpectator(raid) && !completions.containsKey(raid)) {
					active = new Run(raid);
					sawStart = false;
					break;
				}
			}
		}
		if (active == null) return;
		if (active.completionCount != null && !finishOnTick) return;
		observe();
		finishOnTick = false;
		if (deathTracker.isInside(active.name)) outsideTicks = 0;
		else if (++outsideTicks > 3) {
			active = null;
			sawStart = false;
		}
	}

	private void observe() {
		if (!deathTracker.isInside(active.name) || externalSpectator(active.name)) return;
		int size;
		if (RaidPartyTracker.CHAMBERS_OF_XERIC.equals(active.name)) {
			size = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE);
			int scale = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED);
			if (scale > 0) active.scale = scale;
			if (active.completionCount == null) active.mode = client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE) == 1
				? "challenge" : "normal";
		} else if (RaidPartyTracker.THEATRE_OF_BLOOD.equals(active.name)) {
			size = occupied(VarbitID.TOB_CLIENT_P0, 5);
		} else if (RaidPartyTracker.TOMBS_OF_AMASCUT.equals(active.name)) {
			size = occupied(VarbitID.TOA_CLIENT_P0, 8);
			int level = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
			if (size > 0 && level >= 0 && level <= 600) {
				active.invocationLevel = level;
				if (starting()) active.invocationLevelAtStart = Math.max(active.invocationLevelAtStart == null
					? 0 : active.invocationLevelAtStart, level);
			}
		} else {
			size = occupied(VarbitID.TFA_CLIENT_P0, 8);
		}
		if (size > 0) {
			active.partySize = size;
			if (starting()) active.partySizeAtStart = Math.max(active.partySizeAtStart == null ? 0 : active.partySizeAtStart, size);
		}
	}

	private boolean starting() {
		// Native fields populate over several entry ticks; never infer a start after an observation gap.
		return sawStart && active.completionCount == null && client.getTickCount() - startTick <= 10;
	}

	private int occupied(int first, int slots) {
		int count = 0;
		for (int i = 0; i < slots; i++) if (client.getVarbitValue(first + i) > 0) count++;
		return count;
	}

	private boolean externalSpectator(String raid) {
		return RaidPartyTracker.FRACTURED_ARCHIVE.equals(raid)
			&& client.getVarbitValue(VarbitID.TFA_CLIENT_SPECTATE_MODE) >= 2;
	}

	public Run forSource(String source) {
		String raid = RaidPartyTracker.raidOf(source);
		if (raid == null || externalSpectator(raid)) return null;
		if (active != null && raid.equals(active.name) && !externalSpectator(raid)
			&& (active.completionCount != null || deathTracker.isInside(raid))) return active;
		return completions.get(raid);
	}

	void reset() {
		active = null;
		completions.clear();
		sawStart = false;
		finishOnTick = false;
		outsideTicks = 0;
	}

	static String modeOf(String source) {
		String lower = source.toLowerCase(java.util.Locale.ROOT);
		if (lower.contains("challenge mode")) return "challenge";
		if (lower.contains("hard mode")) return "hard";
		if (lower.contains("entry mode")) return "entry";
		if (lower.contains("expert mode")) return "expert";
		return "normal";
	}

	public static final class Run {
		private final String name;
		private String mode;
		private Integer partySize;
		private Integer partySizeAtStart;
		private Integer scale;
		private Integer invocationLevel;
		private Integer invocationLevelAtStart;
		private Integer completionCount;

		private Run(String name) { this.name = name; }

		public void addTo(Map<String, Object> data) {
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
}

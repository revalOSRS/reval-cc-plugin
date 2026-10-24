package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.gameval.VarbitID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Death counts for one observed raid, independent of standalone death notifications.
 * A start message establishes coverage; enabling the plugin mid-raid does not.
 * ToB/ToA/Archive broadcast named deaths (including the game's logout penalties). CoX
 * only provides a reliable local-player signal: unseen teammates remain unknown.
 * Call on the client thread.
 */
@Singleton
public class RaidDeathTracker {
	static final String ARCHIVE_START_MESSAGE = "You enter the Archive...";

	private static final Pattern DEATH = Pattern.compile(
		"^(?:(?<name>.+) has (?<action>died|logged out)|You have (?<self>died))\\. "
			+ "(?<label>Death count|Total deaths): (?<total>[\\d,]+)\\.$");
	private static final Set<Integer> TOA_REGIONS = Set.of(
		14160, 15698, 15700, 14162, 14164, 15186, 15188, 14674, 14676, 15184, 15696, 14672);

	@Inject private Client client;

	private String raid;
	private String localName;
	private final Map<String, Integer> deaths = new HashMap<>();
	private boolean covered;
	private boolean completed;
	private int completionTick = -1;
	private int teamDeaths;
	private String lastDeathMessage;
	private int lastLocalDeathTick = -1;
	private int outsideTicks;

	/** @return true only for an explicit new-raid start. */
	public boolean onChatMessage(String message) {
		String started = null;
		if ("The raid has begun!".equals(message)) started = RaidPartyTracker.CHAMBERS_OF_XERIC;
		if (message.startsWith("You enter the Theatre of Blood")) started = RaidPartyTracker.THEATRE_OF_BLOOD;
		if (message.startsWith("You enter the Tombs of Amascut")) started = RaidPartyTracker.TOMBS_OF_AMASCUT;
		if (ARCHIVE_START_MESSAGE.equals(message)) started = RaidPartyTracker.FRACTURED_ARCHIVE;
		if (started != null) {
			reset();
			raid = started;
			Player local = client.getLocalPlayer();
			localName = local == null ? null : local.getName();
			covered = localName != null;
			return true;
		}
		if (raid == null || afterCompletionTick() || RaidPartyTracker.CHAMBERS_OF_XERIC.equals(raid)) return false;

		Matcher match = DEATH.matcher(message);
		if (!match.matches()) return false;
		boolean theatre = RaidPartyTracker.THEATRE_OF_BLOOD.equals(raid);
		if (theatre != "Death count".equals(match.group("label"))) return false;
		try {
			int total = Integer.parseInt(match.group("total").replace(",", ""));
			// The suffix is the TEAM total. It detects gaps/duplicate delivery; it
			// must never be reported as this player's personal death count.
			if (message.equals(lastDeathMessage)) return false;
			if (total != teamDeaths + 1) covered = false;
			teamDeaths = total;
			lastDeathMessage = message;
			String name = match.group("self") != null ? localName : match.group("name");
			if (name == null) {
				covered = false;
			} else {
				deaths.merge(RaidPartyTracker.nameKey(name), 1, Integer::sum);
			}
		} catch (NumberFormatException e) {
			covered = false;
		}
		return false;
	}

	public void onActorDeath(ActorDeath event) {
		if (!RaidPartyTracker.CHAMBERS_OF_XERIC.equals(raid) || !covered || afterCompletionTick()
			|| event.getActor() != client.getLocalPlayer() || !isInside(raid)) return;
		int tick = client.getTickCount();
		if (tick == lastLocalDeathTick) return;
		lastLocalDeathTick = tick;
		deaths.merge(RaidPartyTracker.nameKey(localName), 1, Integer::sum);
	}

	/** Stop a left/abandoned run without confusing room loads or dead spectating with a new run. */
	public void onGameTick() {
		if (raid == null) return;
		outsideTicks = isInside(raid) ? 0 : outsideTicks + 1;
		if (outsideTicks > 3) reset();
	}

	public void complete(String source) {
		if (source.equals(raid) && !completed) {
			completed = true;
			completionTick = client.getTickCount();
		}
	}

	private boolean afterCompletionTick() {
		return completed && client.getTickCount() > completionTick;
	}

	public boolean isCompleted(String source) {
		return completed && source.equals(raid);
	}

	/** Keys are canonical names. Absence means unknown, including CoX teammates. */
	public Map<String, Integer> countsFor(String source, List<String> names) {
		Map<String, Integer> counts = new HashMap<>();
		if (!covered || !source.equals(raid)) return counts;
		boolean chambers = RaidPartyTracker.CHAMBERS_OF_XERIC.equals(raid);
		for (String name : names) {
			String key = RaidPartyTracker.nameKey(name);
			if (!chambers || key.equals(RaidPartyTracker.nameKey(localName))) {
				counts.put(key, deaths.getOrDefault(key, 0));
			}
		}
		return counts;
	}

	public boolean isInside(String source) {
		if (RaidPartyTracker.CHAMBERS_OF_XERIC.equals(source)) {
			return client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
		}
		if (RaidPartyTracker.THEATRE_OF_BLOOD.equals(source)) {
			int status = client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS);
			return status == 2 || status == 3;
		}
		WorldView world = client.getTopLevelWorldView();
		if (RaidPartyTracker.FRACTURED_ARCHIVE.equals(source)) {
			int status = client.getVarbitValue(VarbitID.TFA_CLIENT_PARTYSTATUS);
			return (status == 2 || status == 3) && world != null && world.isInstance();
		}
		Player local = client.getLocalPlayer();
		return world != null && world.isInstance() && local != null
			&& TOA_REGIONS.contains(WorldPoint.fromLocalInstance(client, local.getLocalLocation()).getRegionID());
	}

	public void reset() {
		raid = null;
		localName = null;
		deaths.clear();
		covered = false;
		completed = false;
		completionTick = -1;
		teamDeaths = 0;
		lastDeathMessage = null;
		lastLocalDeathTick = -1;
		outsideTicks = 0;
	}
}

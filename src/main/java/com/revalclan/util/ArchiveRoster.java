package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Native Archive roster evidence for one observed run, retained briefly for its rewards. */
@Singleton
final class ArchiveRoster {
	private static final int MAX_PARTY_SIZE = 8;
	private static final Set<Integer> IN_RAID_PARTY_STATES = Set.of(2, 3);
	private static final long RETENTION_MS = 10 * 60_000L;
	private static final Pattern PLAYER_NAME = Pattern.compile("[a-zA-Z0-9 _-]{1,12}");

	@Inject private Client client;
	private final Map<String, String> names = new LinkedHashMap<>();
	private int maxSize;
	private int verifiedSize;
	private int completionCount = -1;
	private boolean inside;
	private boolean completed;
	private RaidPartyTracker.Party completedParty;
	private long lastActiveAt;

	/** Capture native names and occupied slots; no clan lookup or Party allocation on ticks. */
	void observe() {
		if (client.getGameState() != GameState.LOGGED_IN) return;
		WorldView world = client.getTopLevelWorldView();
		boolean active = IN_RAID_PARTY_STATES.contains(client.getVarbitValue(VarbitID.TFA_CLIENT_PARTYSTATUS))
			&& world != null && world.isInstance();
		long now = System.currentTimeMillis();
		if (active && !inside) clearRun();
		inside = active;
		if (!inside) {
			if (now - lastActiveAt > RETENTION_MS) clearRun();
			return;
		}
		if (completed) return;

		int size = 0;
		Set<String> liveNames = new HashSet<>();
		Set<String> occupiedNames = new HashSet<>();
		for (int i = 0; i < MAX_PARTY_SIZE; i++) {
			boolean occupied = client.getVarbitValue(VarbitID.TFA_CLIENT_P0 + i) > 0;
			if (occupied) size++;
			String raw = client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0 + i);
			String name = raw == null ? "" : Text.removeTags(raw).replace('\u00a0', ' ').trim();
			String key = RaidPartyTracker.nameKey(name);
			if (!PLAYER_NAME.matcher(name).matches() || key.trim().isEmpty()) continue;
			liveNames.add(key);
			if (occupied) occupiedNames.add(key);
			names.putIfAbsent(key, name);
		}
		maxSize = Math.max(maxSize, size);
		// Stale names in empty slots cannot fill an unknown occupied slot.
		if (size > 0 && occupiedNames.size() == size && occupiedNames.equals(liveNames)) {
			verifiedSize = Math.max(verifiedSize, size);
		}
		lastActiveAt = now;
	}

	RaidPartyTracker.Party complete(int count) {
		observe();
		if (count <= completionCount) return null;
		completionCount = count;
		// A second completion without an observed new entry has unknown participants.
		if (completed) names.clear();
		completed = true;
		if (!names.isEmpty()) lastActiveAt = System.currentTimeMillis();
		// Keep the final tick's death snapshot with the names until this reward expires.
		completedParty = names.isEmpty() ? null : RaidPartyTracker.Party.of(RaidPartyTracker.FRACTURED_ARCHIVE,
			RaidPartyTracker.Origin.COMPLETION, new ArrayList<>(names.values()), null);
		return completedParty;
	}

	RaidPartyTracker.Party party(Set<String> revalRoster) {
		observe();
		if (names.isEmpty()) return null;
		Player local = client.getLocalPlayer();
		boolean complete = maxSize > 0 && verifiedSize == maxSize && names.size() == maxSize
			&& local != null && names.containsKey(RaidPartyTracker.nameKey(local.getName()));
		RaidPartyTracker.Party party = completedParty != null ? completedParty.withClanRoster(revalRoster)
			: RaidPartyTracker.Party.of(RaidPartyTracker.FRACTURED_ARCHIVE,
				RaidPartyTracker.Origin.LIVE, new ArrayList<>(names.values()), revalRoster);
		return party.withRosterEvidence(maxSize, complete);
	}

	void reset() {
		clearRun();
		inside = false;
		completionCount = -1;
	}

	private void clearRun() {
		names.clear();
		maxSize = 0;
		verifiedSize = 0;
		completed = false;
		completedParty = null;
		lastActiveAt = 0;
	}
}

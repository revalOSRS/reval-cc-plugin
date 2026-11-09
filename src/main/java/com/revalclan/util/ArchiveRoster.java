package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Native Archive roster observations; the caller owns run boundaries and retention. */
@Singleton
final class ArchiveRoster {
	private static final int MAX_PARTY_SIZE = 8;
	private static final Set<Integer> IN_RAID_PARTY_STATES = Set.of(2, 3);
	private static final Pattern PLAYER_NAME = Pattern.compile("[a-zA-Z0-9 _-]{1,12}");

	@Inject private Client client;
	private final Map<String, String> names = new LinkedHashMap<>();
	private int maxSize;
	private int verifiedSize;

	/** Capture native names and occupied slots; no clan lookup or Party allocation on ticks. */
	void observe() {
		if (client.getGameState() != GameState.LOGGED_IN) return;
		WorldView world = client.getTopLevelWorldView();
		if (!IN_RAID_PARTY_STATES.contains(client.getVarbitValue(VarbitID.TFA_CLIENT_PARTYSTATUS))
			|| world == null || !world.isInstance()) return;

		int size = 0;
		Set<String> occupiedNames = new HashSet<>();
		for (int i = 0; i < MAX_PARTY_SIZE; i++) {
			// Empty slots can retain names from a previous party.
			if (client.getVarbitValue(VarbitID.TFA_CLIENT_P0 + i) <= 0) continue;
			size++;
			String raw = client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0 + i);
			String name = RaidPartyTracker.sanitize(raw);
			String key = RaidPartyTracker.nameKey(name);
			if (!PLAYER_NAME.matcher(name).matches() || key.trim().isEmpty()) continue;
			occupiedNames.add(key);
			names.putIfAbsent(key, name);
		}
		maxSize = Math.max(maxSize, size);
		if (size > 0 && occupiedNames.size() == size) {
			verifiedSize = Math.max(verifiedSize, size);
		}
	}

	RaidPartyTracker.Party party(RaidPartyTracker.Origin origin, Set<String> revalRoster) {
		if (names.isEmpty()) return null;
		Player local = client.getLocalPlayer();
		boolean complete = maxSize > 0 && verifiedSize == maxSize && names.size() == maxSize
			&& local != null && names.containsKey(RaidPartyTracker.nameKey(local.getName()));
		RaidPartyTracker.Party party = RaidPartyTracker.Party.of(RaidPartyTracker.FRACTURED_ARCHIVE,
			origin, new ArrayList<>(names.values()), revalRoster);
		return party.withRosterEvidence(maxSize, complete);
	}

	void reset() {
		names.clear();
		maxSize = 0;
		verifiedSize = 0;
	}
}

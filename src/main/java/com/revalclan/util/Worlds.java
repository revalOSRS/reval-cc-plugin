package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.WorldType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * World-type gate: sessions (and anything else main-game-only) track normal
 * progression worlds only. PvP/Bounty/High-Risk/Skill-Total are normal
 * progression and stay trackable.
 */
public final class Worlds {
	private Worlds() {}

	private static final Set<WorldType> UNTRACKED = EnumSet.of(
		WorldType.SEASONAL,           // leagues
		WorldType.QUEST_SPEEDRUNNING, // separate speedrun profiles
		WorldType.DEADMAN,
		WorldType.PVP_ARENA,
		WorldType.TOURNAMENT_WORLD,
		WorldType.BETA_WORLD,
		WorldType.FRESH_START_WORLD,
		WorldType.LAST_MAN_STANDING,
		WorldType.NOSAVE_MODE
	);

	public static boolean isTrackable(Client client) {
		return Collections.disjoint(client.getWorldType(), UNTRACKED);
	}
}

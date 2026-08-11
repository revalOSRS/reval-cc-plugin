package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.WorldType;

import java.util.ArrayList;
import java.util.List;

/**
 * World-type helpers. The backend owns the regular-worlds-only rule: every
 * payload is stamped with the client's WorldType flag names and the server
 * dispatcher blocks non-regular worlds (seasonal, deadman, speedrun, ...)
 * centrally.
 */
public final class Worlds {
	private Worlds() {}

	/** WorldType flag names of the world the client is on, for payload stamping. */
	public static List<String> flagNames(Client client) {
		List<String> flags = new ArrayList<>();
		for (WorldType t : client.getWorldType()) {
			flags.add(t.name());
		}
		return flags;
	}
}

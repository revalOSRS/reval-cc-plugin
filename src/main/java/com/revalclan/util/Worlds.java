package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.WorldType;

import java.util.ArrayList;
import java.util.List;

/**
 * World-type helpers. Payloads are stamped with WorldType flag names;
 * the backend owns the regular-worlds-only rule.
 */
public final class Worlds {
	private Worlds() {}

	/** Is the client on a seasonal (leagues) world? */
	public static boolean isSeasonal(Client client) {
		return client.getWorldType().contains(WorldType.SEASONAL);
	}

	/** WorldType flag names of the current world. */
	public static List<String> flagNames(Client client) {
		List<String> flags = new ArrayList<>();
		for (WorldType t : client.getWorldType()) {
			flags.add(t.name());
		}
		return flags;
	}
}

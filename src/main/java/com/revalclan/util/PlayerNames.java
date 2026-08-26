package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.Player;

/**
 * Resolves the local player's name, falling back to the last name observed for
 * the current account when the local player is unavailable.
 *
 * {@code client.getLocalPlayer()} is already null when the LOGIN_SCREEN
 * transition fires (which is when LOGOUT is sent) and can be null for a tick or
 * two after login. Sending the "Unknown" placeholder in those windows caused the
 * backend to rename accounts to "Unknown".
 */
public final class PlayerNames {
	public static final String UNKNOWN = "Unknown";

	private static volatile String lastKnownName;
	private static volatile long lastKnownAccountHash = -1L;

	private PlayerNames() {}

	/**
	 * Record the current local player's name if it is available. Cheap; safe to
	 * call every game tick.
	 */
	public static void remember(Client client) {
		Player local = client.getLocalPlayer();
		if (local == null) return;
		String name = local.getName();
		if (name == null || name.isEmpty()) return;
		lastKnownName = name;
		lastKnownAccountHash = client.getAccountHash();
	}

	/**
	 * The live local player name, else the last name seen for this account hash,
	 * else {@link #UNKNOWN}.
	 */
	public static String resolve(Client client) {
		remember(client);
		Player local = client.getLocalPlayer();
		if (local != null && local.getName() != null && !local.getName().isEmpty()) {
			return local.getName();
		}
		String cached = lastKnownName;
		if (cached == null) return UNKNOWN;
		long hash = client.getAccountHash();
		// -1 = client no longer reports a hash (logged out); the cache is still ours.
		if (hash != -1L && lastKnownAccountHash != -1L && hash != lastKnownAccountHash) return UNKNOWN;
		return cached;
	}
}

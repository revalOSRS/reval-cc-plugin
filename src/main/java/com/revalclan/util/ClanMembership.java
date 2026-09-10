package com.revalclan.util;

import com.revalclan.util.ClanValidator.Probe;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Cached "is this player a Reval member" answer for the current login.
 *
 * Membership is proven once per login from the clan settings (or the channel,
 * whichever loads first) and then STAYS proven until a source positively says
 * otherwise. Nulls never clear it: the channel drops for a few ticks on every
 * hop, and a player who left the chat channel has a null channel for the rest
 * of their login while still being a member — the case that used to silence
 * the plugin entirely. The state resets on the login screen so every login
 * re-proves it (an alt that is not in Reval is simply never proven).
 *
 * The backend is the final authority (it knows the roster from Wise Old Man);
 * this gate only decides what the client bothers to send.
 */
@Slf4j
@Singleton
public class ClanMembership {
	@Inject private Client client;

	private volatile boolean member = false;
	/** Last non-null local player name — the login screen nulls the player before the last reads. */
	private String playerName;

	/** Current answer. Safe from any thread. */
	public boolean isMember() {
		return member;
	}

	/** Player name as last seen while logged in (survives the LOGIN_SCREEN null). */
	public String getPlayerName() {
		return playerName;
	}

	/**
	 * Re-read the sources. Client thread. Returns true when the answer flipped
	 * from not-member to member (the caller runs its once-per-login hooks).
	 */
	public boolean refresh() {
		if (client.getGameState() != GameState.LOGGED_IN) return false;
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
			playerName = client.getLocalPlayer().getName();
		}
		if (playerName == null) return false;

		Probe settings = ClanValidator.probeSettings(client, playerName);
		Probe channel = ClanValidator.probeChannel(client, playerName);

		boolean was = member;
		if (settings == Probe.NOT_MEMBER) {
			member = false;
		} else if (settings == Probe.MEMBER || channel == Probe.MEMBER) {
			member = true;
		} else if (channel == Probe.NOT_MEMBER && settings == Probe.UNKNOWN) {
			// Channel names another clan and settings are not loaded — a guest channel
			// while still a Reval member is possible, so only trust this when nothing
			// has proven membership yet this login.
			if (!was) member = false;
		}
		// Both UNKNOWN → keep the previous answer (hop, channel left, still loading)

		if (member != was) {
			log.info("Clan membership {} (settings={}, channel={})", member ? "proven" : "lost", settings, channel);
		}
		return !was && member;
	}

	/** New login must re-prove membership. */
	public void reset() {
		member = false;
		playerName = null;
	}
}

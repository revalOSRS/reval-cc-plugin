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
 * Proven once per login from the clan settings (or the channel, whichever
 * loads first) and then kept for the rest of the login: the channel drops for
 * a few ticks on every hop, and a player who left the chat channel has a null
 * channel for the rest of their login while still being a member — the case
 * that used to silence the plugin entirely. The state resets on the login
 * screen so every login re-proves it (an alt that is not in Reval is simply
 * never proven).
 *
 * The backend is the final authority (it knows the roster from Wise Old Man);
 * this gate only decides what the client bothers to send.
 */
@Slf4j
@Singleton
public class ClanMembership {
	@Inject private Client client;

	private volatile boolean member = false;

	/** Current answer. Safe from any thread. */
	public boolean isMember() {
		return member;
	}

	/**
	 * Probe the sources until membership is proven (client thread, every
	 * tick; a no-op once proven). Returns true exactly on the tick the answer
	 * flips to member, so the caller can run its once-per-login hooks.
	 */
	public boolean refresh() {
		if (member || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return false;
		String name = client.getLocalPlayer().getName();
		if (name == null) return false;

		Probe settings = ClanValidator.probeSettings(client, name);
		if (settings != Probe.MEMBER && ClanValidator.probeChannel(client, name) != Probe.MEMBER) return false;
		member = true;
		log.info("Clan membership proven (settings={})", settings);
		return true;
	}

	/** New login must re-prove membership. */
	public void reset() {
		member = false;
	}
}

package com.revalclan.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;

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
 * Two sources, verified in-client (2026-09-10): the clan settings (the clan
 * the account belongs to; loaded 2–5 ticks after login/hop, survives a left
 * channel, relog and restart — authoritative) and the clan channel (null for
 * ~4 ticks after every hop, null once the player leaves it, possibly stale at
 * LOGGING_IN — only an accelerator).
 *
 * The backend is the final authority (it knows the roster from Wise Old Man);
 * this gate only decides what the client bothers to send.
 */
@Slf4j
@Singleton
public class ClanMembership {
	public static final String CLAN_NAME = "Reval";

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

		boolean bySettings = inSettings(name);
		if (!bySettings && !inChannel(name)) return false;
		member = true;
		log.info("Clan membership proven (settings={})", bySettings);
		return true;
	}

	/** New login must re-prove membership. */
	public void reset() {
		member = false;
	}

	private boolean inSettings(String name) {
		ClanSettings settings = client.getClanSettings();
		if (settings == null || !CLAN_NAME.equalsIgnoreCase(settings.getName())) return false;
		ClanMember m = settings.findMember(name);
		return m != null && rankOk(m.getRank());
	}

	private boolean inChannel(String name) {
		ClanChannel channel = client.getClanChannel();
		if (channel == null || !CLAN_NAME.equalsIgnoreCase(channel.getName())) return false;
		ClanChannelMember m = channel.findMember(name);
		return m != null && rankOk(m.getRank());
	}

	/** Guests in the clan channel carry a negative rank. */
	private static boolean rankOk(ClanRank rank) {
		return rank == null || rank.getRank() >= 0;
	}
}

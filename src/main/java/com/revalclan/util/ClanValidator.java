package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;

/**
 * Constants and stateless clan-membership probes. {@link ClanMembership}
 * owns the cached answer; these only read what the client currently knows.
 *
 * Two sources, verified in-client (2026-09-10):
 * <ul>
 *   <li><b>Clan settings</b> — the clan the account belongs to. Available 2–5
 *       ticks after login/hop, survives a LEFT clan channel, a relog and a
 *       client restart. The authoritative source.</li>
 *   <li><b>Clan channel</b> — the chat channel. Null for ~4 ticks after every
 *       hop, null indefinitely once the player leaves the channel, and can be
 *       a stale object from the previous session at LOGGING_IN. Only an
 *       accelerator.</li>
 * </ul>
 */
public final class ClanValidator {
	/** Required clan name (null or empty to disable clan check) */
	public static final String REQUIRED_CLAN_NAME = "Reval";

	/** Minimum clan rank required (-1 to 127, higher = more permissions). 0 = any member. */
	public static final ClanRank MINIMUM_CLAN_RANK = new ClanRank(0);

	private ClanValidator() {}

	/** Tri-state probe result: the source is not loaded, says member, or says not a member. */
	public enum Probe { UNKNOWN, MEMBER, NOT_MEMBER }

	public static boolean isCheckDisabled() {
		return REQUIRED_CLAN_NAME == null || REQUIRED_CLAN_NAME.trim().isEmpty();
	}

	/** What the clan settings say about {@code playerName}. */
	public static Probe probeSettings(Client client, String playerName) {
		if (isCheckDisabled()) return Probe.MEMBER;
		ClanSettings settings = client.getClanSettings();
		if (settings == null || playerName == null) return Probe.UNKNOWN;
		if (!REQUIRED_CLAN_NAME.equalsIgnoreCase(settings.getName())) return Probe.NOT_MEMBER;
		ClanMember member = settings.findMember(playerName);
		if (member == null) return Probe.NOT_MEMBER;
		return rankOk(member.getRank()) ? Probe.MEMBER : Probe.NOT_MEMBER;
	}

	/** What the clan channel says about {@code playerName}. */
	public static Probe probeChannel(Client client, String playerName) {
		if (isCheckDisabled()) return Probe.MEMBER;
		ClanChannel channel = client.getClanChannel();
		if (channel == null || playerName == null) return Probe.UNKNOWN;
		if (!REQUIRED_CLAN_NAME.equalsIgnoreCase(channel.getName())) return Probe.NOT_MEMBER;
		ClanChannelMember member = channel.findMember(playerName);
		if (member == null) return Probe.UNKNOWN; // channel roster is only who is online
		return rankOk(member.getRank()) ? Probe.MEMBER : Probe.NOT_MEMBER;
	}

	private static boolean rankOk(ClanRank rank) {
		return MINIMUM_CLAN_RANK == null || rank == null || rank.getRank() >= MINIMUM_CLAN_RANK.getRank();
	}
}

package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;

/** In-game clan rank checks shared by admin-gated UI. */
public final class ClanRanks {
	/** Deputy Owner and above - the rank the plugin treats as clan staff. */
	private static final int ADMIN_MIN_RANK = 125;

	private ClanRanks() {
	}

	public static boolean isDeputyOwnerPlus(Client client) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return false;
		}
		ClanChannel clanChannel = client.getClanChannel();
		String name = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		if (clanChannel == null || name == null) {
			return false;
		}
		ClanChannelMember member = clanChannel.findMember(name);
		return member != null && member.getRank().getRank() >= ADMIN_MIN_RANK;
	}
}

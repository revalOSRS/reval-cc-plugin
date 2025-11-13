package com.revalclan.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanRank;

/**
 * Utility class for validating clan membership and rank
 * Used to restrict webhook notifications to specific clan members
 */
@Slf4j
public class ClanValidator {
	/** Required clan name (null or empty to disable clan check) */
	private static final String REQUIRED_CLAN_NAME = "Reval";
	
	/** 
	 * Minimum clan rank required (-1 to 127, where higher = more permissions)
	 */
	private static final ClanRank MINIMUM_CLAN_RANK = new ClanRank(10); // 10 = new member
	
	// ===============================================

	/**
	 * Validate that the player is in the required clan with sufficient rank
	 * @param client The RuneLite client instance
	 * @return true if player passes clan validation (or validation is disabled)
	 */
	public static boolean validateClan(Client client) {
		if (REQUIRED_CLAN_NAME == null || REQUIRED_CLAN_NAME.trim().isEmpty()) {
			return true;
		}

		ClanChannel clanChannel = client.getClanChannel();
		if (clanChannel == null) return false;

		boolean isInTheWrongClan = !REQUIRED_CLAN_NAME.equalsIgnoreCase(clanChannel.getName());
		if (isInTheWrongClan) return false;

		if (MINIMUM_CLAN_RANK == null) return true;

		String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";
		ClanChannelMember member = clanChannel.findMember(playerName);		
		if (member == null) return false;

		ClanRank playerRank = member.getRank();
		boolean isBelowMinimumRank = playerRank.getRank() < MINIMUM_CLAN_RANK.getRank();
		if (isBelowMinimumRank) return false;

		return true;
	}
}


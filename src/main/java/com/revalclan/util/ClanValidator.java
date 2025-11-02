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
public class ClanValidator
{
	// ========== CLAN VALIDATION SETTINGS ==========
	
	/** Required clan name (null or empty to disable clan check) */
	private static final String REQUIRED_CLAN_NAME = "Reval";
	
	/** 
	 * Minimum clan rank required (-1 to 127, where higher = more permissions)
	 * Common ranks: GUEST=-1, regular members=0-99, ADMINISTRATOR=100, DEPUTY_OWNER=125, OWNER=126, JMOD=127
	 * Set to null to allow all ranks
	 */
	private static final ClanRank MINIMUM_CLAN_RANK = new ClanRank(0); // 0 = regular member
	
	// ===============================================

	/**
	 * Validate that the player is in the required clan with sufficient rank
	 * @param client The RuneLite client instance
	 * @return true if player passes clan validation (or validation is disabled)
	 */
	public static boolean validateClan(Client client)
	{
		if (REQUIRED_CLAN_NAME == null || REQUIRED_CLAN_NAME.trim().isEmpty())
		{
			return true;
		}

		ClanChannel clanChannel = client.getClanChannel();
		if (clanChannel == null)
		{
			log.debug("Player is not in a clan - blocking notification");
			return false;
		}

		String clanName = clanChannel.getName();
		if (!REQUIRED_CLAN_NAME.equalsIgnoreCase(clanName))
		{
			log.debug("Player is in clan '{}' but required clan is '{}' - blocking notification", 
				clanName, REQUIRED_CLAN_NAME);
			return false;
		}

		if (MINIMUM_CLAN_RANK == null)
		{
			return true;
		}

		String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";
		ClanChannelMember member = clanChannel.findMember(playerName);
		
		if (member == null)
		{
			log.warn("Could not find player '{}' in clan channel - blocking notification", playerName);
			return false;
		}

		ClanRank playerRank = member.getRank();
		if (playerRank.getRank() < MINIMUM_CLAN_RANK.getRank())
		{
			log.debug("Player rank {} is below minimum rank {} - blocking notification", 
				playerRank.getRank(), MINIMUM_CLAN_RANK.getRank());
			return false;
		}

		return true;
	}
}


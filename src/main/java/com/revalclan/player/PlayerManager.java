package com.revalclan.player;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages player metadata and statistics
 */
@Slf4j
@Singleton
public class PlayerManager {
	@Inject private Client client;

	/**
	 * Sync and get player metadata
	 */
	public Map<String, Object> sync() {
		Map<String, Object> metadata = new HashMap<>();
		
		if (client.getLocalPlayer() != null) {
			metadata.put("username", client.getLocalPlayer().getName());
			metadata.put("combatLevel", client.getLocalPlayer().getCombatLevel());
		} else {
			metadata.put("username", "Unknown");
			metadata.put("combatLevel", 0);
		}
		
		metadata.put("accountHash", client.getAccountHash());
		metadata.put("totalLevel", client.getTotalLevel());
		metadata.put("totalExperience", client.getOverallExperience());
		
		return metadata;
	}
}



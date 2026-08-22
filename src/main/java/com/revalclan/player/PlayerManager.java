package com.revalclan.player;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;

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
		metadata.put("accountType", getAccountType(client.getVarbitValue(VarbitID.IRONMAN)));
		metadata.put("totalLevel", client.getTotalLevel());
		metadata.put("totalExperience", client.getOverallExperience());
		
		Map<String, Map<String, Integer>> skills = new HashMap<>();
		for (Skill skill : Skill.values()) {
			Map<String, Integer> skillData = new HashMap<>();
			skillData.put("level", client.getRealSkillLevel(skill));
			skillData.put("experience", client.getSkillExperience(skill));
			skills.put(skill.getName().toLowerCase(), skillData);
		}
		metadata.put("skills", skills);
		
		return metadata;
	}

	private static String getAccountType(int varbitValue) {
		switch (varbitValue) {
			case 1: return "IRONMAN";
			case 2: return "ULTIMATE_IRONMAN";
			case 3: return "HARDCORE_IRONMAN";
			case 4: return "GROUP_IRONMAN";
			case 5: return "HARDCORE_GROUP_IRONMAN";
			case 6: return "UNRANKED_GROUP_IRONMAN";
			default: return "NORMAL";
		}
	}
}

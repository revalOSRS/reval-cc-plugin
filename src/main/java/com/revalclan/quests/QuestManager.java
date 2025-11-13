package com.revalclan.quests;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages quest completion data
 */
@Slf4j
@Singleton
public class QuestManager {
	@Inject private Client client;

	/**
	 * Sync and get quest completion data
	 */
	public Map<String, Object> sync() {
		Map<String, Object> questData = new HashMap<>();
		Map<String, String> questStates = new HashMap<>();
		
		for (Quest quest : Quest.values()) {
			QuestState state = quest.getState(client);
			questStates.put(quest.getName(), state.name());
		}
		
		questData.put("questPoints", client.getVarpValue(101));
		questData.put("questStates", questStates);
		
		return questData;
	}
}


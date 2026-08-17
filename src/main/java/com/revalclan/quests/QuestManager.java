package com.revalclan.quests;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages quest completion data
 */
@Slf4j
@Singleton
public class QuestManager {
	@Inject private Client client;

	/**
	 * RuneLite's Quest enum has no quest/miniquest flag, so membership is curated
	 * here (wiki miniquest list, 19 as of July 2025 — update on new releases).
	 * RFD subquests are NOT miniquests; they stay in questStates.
	 */
	private static final Set<String> MINIQUEST_NAMES = Set.of(
		"Alfred Grimhand's Barcrawl",
		"Barbarian Training",
		"Bear Your Soul",
		"Curse of the Empty Lord",
		"Daddy's Home",
		"The Enchanted Key",
		"Enter the Abyss",
		"Family Pest",
		"The Frozen Door",
		"The General's Shadow",
		"His Faithful Servants",
		"Hopespear's Will",
		"In Search of Knowledge",
		"Into the Tombs",
		"Lair of Tarn Razorlor",
		"Mage Arena I",
		"Mage Arena II",
		"Skippy and the Mogres",
		"Vale Totems"
	);

	/**
	 * Sync and get quest completion data, miniquests separated from quests
	 */
	public Map<String, Object> sync() {
		Map<String, Object> questData = new HashMap<>();
		Map<String, String> questStates = new HashMap<>();
		Map<String, String> miniquestStates = new HashMap<>();

		for (Quest quest : Quest.values()) {
			QuestState state = quest.getState(client);
			if (MINIQUEST_NAMES.contains(quest.getName())) {
				miniquestStates.put(quest.getName(), state.name());
			} else {
				questStates.put(quest.getName(), state.name());
			}
		}

		questData.put("questPoints", client.getVarpValue(101));
		questData.put("questStates", questStates);
		questData.put("miniquestStates", miniquestStates);

		return questData;
	}
}


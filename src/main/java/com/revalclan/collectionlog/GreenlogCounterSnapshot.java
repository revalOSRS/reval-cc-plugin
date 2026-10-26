package com.revalclan.collectionlog;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Reads game counters on the client thread, without scanning collection items. */
@Singleton
public class GreenlogCounterSnapshot {
	@Inject private Client client;
	@Inject private CollectionLogManager collectionLogManager;

	public Map<String, Object> capture(Collection<Integer> itemIds) {
		Map<String, Object> snapshot = new HashMap<>();
		Map<String, Integer> skills = new HashMap<>();
		for (Skill skill : Skill.values()) {
			if (skill == Skill.OVERALL) continue;
			skills.put(skill.getName().toLowerCase(java.util.Locale.ROOT), client.getSkillExperience(skill));
		}
		snapshot.put("capturedAt", System.currentTimeMillis());
		snapshot.put("skills", skills);
		snapshot.put("pages", collectionLogManager.getCounterSnapshot(itemIds));
		return snapshot;
	}

	/** Optional context: a failed read leaves the event unchanged. */
	public void attachTo(Map<String, Object> data, Collection<Integer> itemIds) {
		try {
			data.put("greenlogCounters", capture(itemIds));
		} catch (RuntimeException ignored) {
			// Unavailable counters must never prevent the event.
		}
	}
}

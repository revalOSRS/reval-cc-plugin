package com.revalclan.collectionlog;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GreenlogCounterSnapshotTest {
	private Client client;
	private CollectionLogManager manager;
	private GreenlogCounterSnapshot snapshot;

	private static void inject(Object target, String field, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	@Before public void setUp() throws Exception {
		client = mock(Client.class);
		manager = new CollectionLogManager();
		inject(manager, "client", client);
		Method init = CollectionLogManager.class.getDeclaredMethod("initializeKCMap");
		init.setAccessible(true);
		init.invoke(manager);
		snapshot = new GreenlogCounterSnapshot();
		inject(snapshot, "client", client);
		inject(snapshot, "collectionLogManager", manager);
	}

	@Test public void capturesCurrentXpWithoutScanningOrIncludingItemLists() {
		when(client.getSkillExperience(Skill.WOODCUTTING)).thenReturn(13365058);
		Map<String, Object> data = snapshot.capture(Collections.emptySet());
		assertEquals(13365058, ((Map<?, ?>) data.get("skills")).get("woodcutting"));
		assertTrue(((Number) data.get("capturedAt")).longValue() > 0);
		assertTrue(((Map<?, ?>) data.get("pages")).isEmpty());
		assertFalse(((Map<?, ?>) data.get("skills")).containsKey("overall"));
		assertTrue(manager.getObtainedItems().isEmpty());
		verify(client, never()).getIndexConfig();
	}

	@Test public void lootIncludesOnlyAffectedPagesAndKeepsTheCurrentKc() {
		manager.getCategoryStructIdMap().put("vorkath", 1);
		manager.getCategoryItemMap().put(1, Collections.singleton(123));
		when(client.getVarpValue(1691)).thenReturn(812);
		Map<String, Map<String, Integer>> pages = manager.getCounterSnapshot(Collections.singleton(123));
		assertEquals(Collections.singleton("vorkath"), pages.keySet());
		assertEquals(Integer.valueOf(812), pages.get("vorkath").get("kc"));
	}

	@Test public void sharedClueItemsRetainAllTierCounters() {
		manager.getCategoryStructIdMap().put("shared_treasure_trail_rewards", 1);
		manager.getCategoryItemMap().put(1, Collections.singleton(123));
		when(client.getVarbitValue(11998)).thenReturn(2000);
		Map<String, Map<String, Integer>> pages = manager.getCounterSnapshot(Collections.singleton(123));
		assertEquals(Integer.valueOf(2000), pages.get("medium_treasure_trails").get("kc"));
		assertTrue(pages.containsKey("beginner_treasure_trails"));
		assertTrue(pages.containsKey("master_treasure_trails"));
		assertFalse(pages.containsKey("vorkath"));
	}

	@Test public void failedReadsAreOmittedInsteadOfRecordedAsZero() {
		when(client.getVarbitValue(11998)).thenThrow(new IllegalStateException("unavailable"));
		assertFalse(manager.getCounterSnapshot(null).containsKey("medium_treasure_trails"));
	}

	@Test public void preservesRaidModesIndividually() {
		when(client.getVarpValue(3646)).thenReturn(30);
		when(client.getVarpValue(3645)).thenReturn(10);
		when(client.getVarpValue(3647)).thenReturn(70);
		Map<String, Integer> counts = manager.getCounterSnapshot(null).get("tombs_of_amascut");
		assertEquals(Integer.valueOf(30), counts.get("kc"));
		assertEquals(Integer.valueOf(10), counts.get("entry_mode"));
		assertEquals(Integer.valueOf(70), counts.get("expert_mode"));
	}
}

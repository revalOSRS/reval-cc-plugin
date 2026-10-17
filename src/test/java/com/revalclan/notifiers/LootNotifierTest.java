package com.revalclan.notifiers;

import com.revalclan.TestSupport;
import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.session.SessionTracker;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.RaidRewardLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class LootNotifierTest {
	@Test public void logoutDropsBufferedLootBeforeTheNextAccountTicks() throws Exception {
		List<Map<String, Object>> sent = new ArrayList<>();
		LootNotifier notifier = new LootNotifier() {
			@Override protected void sendNotification(Map<String, Object> data) { sent.add(data); }
		};
		notifier.filterManager = new EventFilterManager();
		notifier.itemManager = mock(ItemManager.class);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Pharaoh's sceptre");
		when(notifier.itemManager.getItemPrice(anyInt())).thenReturn(2_000_000L);
		when(notifier.itemManager.getItemComposition(anyInt())).thenReturn(item);
		TestSupport.inject(notifier, "sessionTracker", mock(SessionTracker.class));
		TestSupport.inject(notifier, "raidRewardLedger", mock(RaidRewardLedger.class));
		TestSupport.inject(notifier, "collectionLogManager", mock(CollectionLogManager.class));
		notifier.onGameMessage("You have found the Pharaoh's sceptre!");
		notifier.reset();
		notifier.onGameTick();
		notifier.onGameTick();
		assertTrue("A previous account's buffered loot must be released at logout", sent.isEmpty());
	}
}

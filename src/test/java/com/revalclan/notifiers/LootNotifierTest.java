package com.revalclan.notifiers;

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

@org.junit.runner.RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class LootNotifierTest {
	@org.mockito.Mock private SessionTracker sessionTracker;
	@org.mockito.Mock private RaidRewardLedger raidRewardLedger;
	@org.mockito.Mock private CollectionLogManager collectionLogManager;
	private final List<Map<String, Object>> sent = new ArrayList<>();
	@org.mockito.InjectMocks private LootNotifier notifier = new LootNotifier() {
		@Override protected void sendNotification(Map<String, Object> data) { sent.add(data); }
	};
	@Test public void logoutDropsBufferedLootBeforeTheNextAccountTicks() throws Exception {
		notifier.filterManager = new EventFilterManager(mock(okhttp3.OkHttpClient.class), new com.google.gson.Gson());
		notifier.itemManager = mock(ItemManager.class);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Pharaoh's sceptre");
		when(notifier.itemManager.getItemPrice(anyInt())).thenReturn(2_000_000L);
		when(notifier.itemManager.getItemComposition(anyInt())).thenReturn(item);
		notifier.onGameMessage("You have found the Pharaoh's sceptre!");
		notifier.reset();
		notifier.onGameTick();
		notifier.onGameTick();
		assertTrue("A previous account's buffered loot must be released at logout", sent.isEmpty());
	}
}

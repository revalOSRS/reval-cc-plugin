package com.revalclan.notifiers;

import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.collectionlog.GreenlogCounterSnapshot;
import com.revalclan.session.SessionTracker;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.RaidRewardLedger;
import com.revalclan.util.RaidPartyTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@org.junit.runner.RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class LootNotifierTest {
	@org.mockito.Mock private SessionTracker sessionTracker;
	@org.mockito.Mock private RaidRewardLedger raidRewardLedger;
	@org.mockito.Mock private CollectionLogManager collectionLogManager;
	@org.mockito.Mock private GreenlogCounterSnapshot greenlogCounterSnapshot;
	@org.mockito.Mock private RaidPartyTracker raidPartyTracker;
	private final List<Map<String, Object>> sent = new ArrayList<>();
	@org.mockito.InjectMocks private LootNotifier notifier = new LootNotifier() {
		@Override protected void sendNotification(Map<String, Object> data) { sent.add(data); }
	};
	@Before public void setup() {
		notifier.filterManager = new EventFilterManager(mock(okhttp3.OkHttpClient.class), new com.google.gson.Gson());
		notifier.itemManager = mock(ItemManager.class);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Dragon claws");
		when(notifier.itemManager.getItemPrice(anyInt())).thenReturn(2_000_000L);
		when(notifier.itemManager.getItemComposition(anyInt())).thenReturn(item);
	}

	private void ticks(int count) {
		for (int tick = 0; tick < count; tick++) notifier.onGameTick();
	}

	private void loot(String source) {
		LootReceived event = mock(LootReceived.class);
		when(event.getName()).thenReturn(source);
		when(event.getType()).thenReturn(LootRecordType.EVENT);
		when(event.getItems()).thenReturn(List.of(new ItemStack(13652, 1)));
		when(raidRewardLedger.record(eq(source), anyCollection())).thenReturn(true);
		notifier.onLootReceived(event);
	}

	private void announce() {
		notifier.onGameMessage("New item added to your collection log: Dragon claws");
	}

	private boolean announced(int index) {
		List<?> items = (List<?>) sent.get(index).get("items");
		return Boolean.TRUE.equals(((Map<?, ?>) items.get(0)).get("isNewCollectionLogItem"));
	}

	@Test public void delayedRaidRewardsKeepTheAnnouncementUntilTheChestIsOpened() {
		for (String source : List.of("Chambers of Xeric", "Chambers of Xeric Challenge Mode",
			"Theatre of Blood", "Theatre of Blood: Hard Mode", "Tombs of Amascut: Expert Mode", "The Fractured Archive")) {
			announce();
			ticks(40); // The reported CoX delay was about 23 seconds.
			loot(source);
			ticks(2);
			assertTrue(source, announced(sent.size() - 1));
		}
	}

	@Test public void aRaidRewardCanBeLeftUnopenedForMoreThanAFewMinutes() {
		announce();
		ticks(1_000);
		loot("Chambers of Xeric");
		ticks(2);
		assertTrue(announced(0));
	}

	@Test public void oneAnnouncementOnlyMarksTheFirstMatchingLoot() {
		announce();
		loot("Chambers of Xeric");
		loot("Chambers of Xeric");
		ticks(2);
		assertTrue(announced(0));
		assertFalse(announced(1));
	}

	@Test public void anAnnouncementAfterLootStillMatchesWithinTheBuffer() {
		loot("Chambers of Xeric");
		ticks(1);
		announce();
		ticks(1);
		assertTrue(announced(0));
	}

	@Test public void anUnrelatedChestDoesNotUseAnOldRaidAnnouncement() {
		announce();
		ticks(40);
		loot("Barrows");
		ticks(2);
		assertFalse(announced(0));
		loot("Chambers of Xeric");
		ticks(2);
		assertTrue(announced(1));
	}

	@Test public void nonRaidLootStillMatchesARecentAnnouncement() {
		announce();
		loot("Barrows");
		ticks(2);
		assertTrue(announced(0));
	}

	@Test public void logoutClearsUnclaimedAnnouncements() {
		announce();
		notifier.reset();
		loot("Chambers of Xeric");
		ticks(2);
		assertFalse(announced(0));
	}

	@Test public void logoutDropsBufferedLootBeforeTheNextAccountTicks() throws Exception {
		notifier.onGameMessage("You have found the Pharaoh's sceptre!");
		notifier.reset();
		notifier.onGameTick();
		notifier.onGameTick();
		assertTrue("A previous account's buffered loot must be released at logout", sent.isEmpty());
	}
}

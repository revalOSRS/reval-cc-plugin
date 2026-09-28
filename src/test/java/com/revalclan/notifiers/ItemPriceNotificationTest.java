package com.revalclan.notifiers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revalclan.RevalClanConfig;
import com.revalclan.session.SessionTracker;
import com.revalclan.util.EventFilterManager;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.SkullIcon;
import net.runelite.api.WorldType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ItemPriceNotificationTest {
	@Mock private Client client;
	@Mock private ItemManager itemManager;
	@Mock private SessionTracker sessionTracker;
	@Mock private EventFilterManager filterManager;
	@Mock private RevalClanConfig config;

	private JsonObject notification;

	@InjectMocks private final LootNotifier lootNotifier = new LootNotifier() {
		@Override protected void sendNotification(Map<String, Object> data) {
			capture(data);
		}
	};
	@InjectMocks private final ClueNotifier clueNotifier = new ClueNotifier() {
		@Override protected void sendNotification(Map<String, Object> data) {
			capture(data);
		}
	};
	@InjectMocks private final DeathNotifier deathNotifier = new DeathNotifier() {
		@Override protected void sendNotificationWithScreenshot(Map<String, Object> data) {
			capture(data);
		}
	};

	@Before
	public void setUp() {
		when(filterManager.getFilters()).thenReturn(new EventFilterManager.EventFilters());
	}

	@Test
	public void lootPreservesLongPricesInFilteringTotalsAndSessionTracking() {
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(1);
		when(npc.getName()).thenReturn("Boss");
		priceItem(123, 3_000_000_000L);
		priceItem(124, 700_000L);

		lootNotifier.onNpcLootReceived(new NpcLootReceived(npc, Arrays.asList(
			new ItemStack(123, 2), new ItemStack(124, 4))));
		lootNotifier.onGameTick();
		assertNull(notification);
		lootNotifier.onGameTick();

		assertNotNull(notification);
		assertEquals(6_000_000_000L, notification.get("totalGEValue").getAsLong());
		JsonArray items = notification.getAsJsonArray("items");
		assertEquals(1, items.size()); // The minimum value applies to the unit price.
		assertEquals(3_000_000_000L, items.get(0).getAsJsonObject().get("gePrice").getAsLong());
		verify(sessionTracker).addLoot("Boss", 123, "Item 123", 2, 3_000_000_000L);
		verify(sessionTracker).addLoot("Boss", 124, "Item 124", 4, 700_000L);
	}

	@Test
	public void clueRewardsPreserveLongUnitPricesAndStackTotals() {
		priceItem(123, 3_000_000_000L);
		priceItem(124, 100_000L);
		Widget rewards = mock(Widget.class);
		when(client.getWidget(InterfaceID.TrailRewardscreen.ITEMS)).thenReturn(rewards);
		Widget[] itemsInWidget = {reward(123, 2), reward(124, 3)};
		when(rewards.getChildren()).thenReturn(itemsInWidget);

		clueNotifier.onChatMessage("You have completed 12 elite Treasure Trails.");
		WidgetLoaded event = new WidgetLoaded();
		event.setGroupId(InterfaceID.TRAIL_REWARDSCREEN);
		clueNotifier.onWidgetLoaded(event);

		assertNotNull(notification);
		assertEquals(6_000_300_000L, notification.get("totalValue").getAsLong());
		JsonArray items = notification.getAsJsonArray("items");
		assertEquals(2, items.size());
		assertEquals(3_000_000_000L, items.get(0).getAsJsonObject().get("price").getAsLong());
		verify(sessionTracker).addClue("elite");
	}

	@Test
	public void deathSortsLongPricesAndReportsTheFullLostValue() {
		Player player = mock(Player.class);
		Player killer = mock(Player.class);
		when(config.notifyDeath()).thenReturn(true);
		when(client.getLocalPlayer()).thenReturn(player);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		when(player.getSkullIcon()).thenReturn(SkullIcon.NONE);
		when(killer.getCombatLevel()).thenReturn(100);
		when(killer.getInteracting()).thenReturn(player);
		when(killer.getName()).thenReturn("Killer");
		priceItem(123, 3_000_000_000L);
		priceItem(124, 6_000_000_000L);
		priceItem(125, 5_000_000_000L);
		priceItem(126, 4_000_000_000L);
		priceItem(127, 100_000L);
		ItemContainer inventory = container(new Item(127, 3), new Item(123, 2), new Item(124, 1));
		ItemContainer equipment = container(new Item(125, 1), new Item(126, 1));
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);

		deathNotifier.onInteractingChanged(new InteractingChanged(player, killer));
		deathNotifier.onActorDeath(new ActorDeath(player));

		assertNotNull(notification);
		JsonArray kept = notification.getAsJsonArray("keptItems");
		assertEquals(3, kept.size());
		assertEquals(124, kept.get(0).getAsJsonObject().get("id").getAsInt());
		assertEquals(125, kept.get(1).getAsJsonObject().get("id").getAsInt());
		assertEquals(126, kept.get(2).getAsJsonObject().get("id").getAsInt());
		JsonArray lost = notification.getAsJsonArray("lostItems");
		assertEquals(2, lost.size());
		assertEquals(3_000_000_000L, lost.get(0).getAsJsonObject().get("gePrice").getAsLong());
		assertEquals(6_000_300_000L, notification.get("totalLostValue").getAsLong());
		verify(sessionTracker).addDeath("Killer", 6_000_300_000L);
	}

	private void capture(Map<String, Object> data) {
		// Check the numeric values that the webhook serializer sends to the backend.
		notification = new Gson().toJsonTree(data).getAsJsonObject();
	}

	private void priceItem(int id, long price) {
		ItemComposition composition = mock(ItemComposition.class);
		when(itemManager.getItemPrice(id)).thenReturn(price);
		when(itemManager.getItemComposition(id)).thenReturn(composition);
		when(composition.getName()).thenReturn("Item " + id);
	}

	private static Widget reward(int id, int quantity) {
		Widget widget = mock(Widget.class);
		when(widget.getItemId()).thenReturn(id);
		when(widget.getItemQuantity()).thenReturn(quantity);
		return widget;
	}

	private static ItemContainer container(Item... items) {
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		return container;
	}
}

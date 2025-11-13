package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class LootNotifier extends BaseNotifier
{
	@Inject
	private RevalClanConfig config;

	@Inject
	private ItemManager itemManager;

	@Override
	public boolean isEnabled() {
		return config.notifyLoot() && filterManager.getFilters().isLootEnabled();
	}

	@Override
	protected String getEventType() {
		return "LOOT";
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event){
		if (!isEnabled()) return;

		NPC npc = event.getNpc();
		Collection<ItemStack> items = event.getItems();

		handleLootDrop(items, npc.getName(), "NPC", npc.getId());
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event) {
		if (!isEnabled()) return;

		String playerName = event.getPlayer().getName();
		Collection<ItemStack> items = event.getItems();

		handleLootDrop(items, playerName, "PLAYER", null);
	}

	private void handleLootDrop(Collection<ItemStack> items, String source, String sourceType, Integer sourceId) {
		// Get dynamic filters
		long minLootValue = filterManager.getFilters().getLootMinValue();
		Set<Integer> whitelistItemIds = filterManager.getFilters().getLootWhitelist();
		Set<Integer> blacklistItemIds = filterManager.getFilters().getLootBlacklist();
		
		List<Map<String, Object>> itemsList = new ArrayList<>();
		long totalGEValue = 0;
		long totalHAValue = 0;
		boolean hasWhitelistedItem = false;
		boolean hasUntradeable = false;

		for (ItemStack item : items) {
			int itemId = item.getId();
			
			// Skip blacklisted items
			if (blacklistItemIds.contains(itemId)) continue;

			int gePrice = itemManager.getItemPrice(itemId);
			int haValue = itemManager.getItemComposition(itemId).getPrice();
			boolean isTradeable = itemManager.getItemComposition(itemId).isTradeable();
			String itemName = itemManager.getItemComposition(itemId).getName();

			Map<String, Object> itemData = new HashMap<>();
			itemData.put("id", itemId);
			itemData.put("name", itemName);
			itemData.put("quantity", item.getQuantity());
			itemData.put("gePrice", gePrice);
			itemData.put("haValue", haValue);
			itemData.put("tradeable", isTradeable);
			itemsList.add(itemData);

			totalGEValue += (long) gePrice * item.getQuantity();
			totalHAValue += (long) haValue * item.getQuantity();

			// Check for special items
			if (whitelistItemIds.contains(itemId)) hasWhitelistedItem = true;
			if (!isTradeable) hasUntradeable = true;
		}
		// 1. Total value >= minLootValue (from API)
		// 2. Contains a whitelisted item (from API)
		// 3. Contains an untradeable item
		boolean shouldNotify = totalGEValue >= minLootValue || hasWhitelistedItem || hasUntradeable;

		if (!shouldNotify) return;

		Map<String, Object> lootData = new HashMap<>();
		lootData.put("player", getPlayerName());
		lootData.put("source", source);
		lootData.put("sourceType", sourceType);
		if (sourceId != null) {
			lootData.put("sourceId", sourceId);
		}
		lootData.put("totalGEValue", totalGEValue);
		lootData.put("totalHAValue", totalHAValue);
		lootData.put("items", itemsList);

		sendNotification(lootData);
	}
}


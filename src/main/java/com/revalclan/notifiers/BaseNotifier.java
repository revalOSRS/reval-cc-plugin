package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import com.revalclan.util.ClanValidator;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.WebhookService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all notification types (loot, death, pets, etc.)
 */
@Slf4j
public abstract class BaseNotifier {
	@Inject protected Client client;

	@Inject protected WebhookService webhookService;

	@Inject protected RevalClanConfig config;

	@Inject protected EventFilterManager filterManager;
	
	@Inject protected ItemManager itemManager;

	/**
	 * Check if this notifier should be active
	 * @return true if the notifier is enabled and conditions are met
	 */
	public abstract boolean isEnabled();

	/**
	 * Get the event type identifier for this notifier
	 * @return Event type string (e.g., "LOOT", "DEATH", "PET")
	 */
	protected abstract String getEventType();

	/**
	 * Send a notification with the given data
	 * 
	 * @param data The notification data
	 */
	protected void sendNotification(Map<String, Object> data) {
		if (!ClanValidator.validateClan(client)) return;

		// Add event metadata
		data.put("eventType", getEventType());
		data.put("eventTimestamp", System.currentTimeMillis());
		data.put("accountHash", client.getAccountHash());
		data.put("username", getPlayerName());
		data.put("world", client.getWorld());
		
		if (client.getLocalPlayer() != null) {
			WorldPoint wp = client.getLocalPlayer().getWorldLocation();
			data.put("worldX", wp.getX());
			data.put("worldY", wp.getY());
			data.put("plane", wp.getPlane());
			data.put("regionId", wp.getRegionID());
		}
		
		data.put("inventory", getInventoryData());
		data.put("equipment", getEquippedItems());
		
		// Send webhook
		webhookService.sendDataAsync(data);
	}

	/**
	 * Get the player's name
	 */
	protected String getPlayerName() {
		if (client.getLocalPlayer() != null) return client.getLocalPlayer().getName();
		return "Unknown";
	}
	
	/**
	 * Get player's equipped items using the equipment ItemContainer
	 */
	protected List<Map<String, Object>> getEquippedItems() {
		return getItemContainerData(94); // Equipment container
	}
	
	/**
	 * Get player's inventory data
	 */
	protected List<Map<String, Object>> getInventoryData() {
		return getItemContainerData(93); // Inventory container
	}
	
	/**
	 * Get items from a specific ItemContainer
	 * @param containerId The container ID (93=inventory, 94=equipment)
	 */
	private List<Map<String, Object>> getItemContainerData(int containerId) {
		List<Map<String, Object>> items = new ArrayList<>();
		
		ItemContainer container = client.getItemContainer(containerId);
		if (container == null) return items;
		
		Item[] containerItems = container.getItems();
		if (containerItems == null) return items;
		
		for (int i = 0; i < containerItems.length; i++) {
			Item item = containerItems[i];
			
			// Skip empty slots
			if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
			
			Map<String, Object> itemData = new HashMap<>();
			itemData.put("id", item.getId());
			itemData.put("quantity", item.getQuantity());
			itemData.put("gePrice", itemManager.getItemPrice(item.getId()));
			itemData.put("slot", i);
			
			try {
				ItemComposition itemComp = itemManager.getItemComposition(item.getId());
				if (itemComp != null) {
					itemData.put("name", itemComp.getName());
				} else {
					itemData.put("name", "Unknown");
				}
			} catch (Exception e) {
				itemData.put("name", "Unknown");
			}
			
			items.add(itemData);
		}
		
		return items;
	}
}


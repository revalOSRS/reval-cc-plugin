package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import com.revalclan.util.ClanValidator;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.WebhookService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
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
		// Validate clan membership before sending
		if (!ClanValidator.validateClan(client)) return;

		// Add event metadata
		data.put("eventType", getEventType());
		data.put("eventTimestamp", System.currentTimeMillis());

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
	 * Get player's equipped items
	 */
	protected List<Map<String, Object>> getEquippedItems() {
		List<Map<String, Object>> equipment = new ArrayList<>();
		
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getPlayerComposition() == null) {
			return equipment;
		}
		
		int[] equipped = client.getLocalPlayer().getPlayerComposition().getEquipmentIds();
		if (equipped == null) return equipment;
		
		for (int i = 0; i < equipped.length; i++) {
			int itemId = equipped[i];
			
			// Skip empty slots (ID 0 or -1 means empty)
			if (itemId <= 0) continue;
			
			// ItemComposition IDs are offset by 512 for worn items
			int actualItemId = itemId - 512;
			if (actualItemId <= 0) continue;
			
			Map<String, Object> itemData = new HashMap<>();
			itemData.put("id", actualItemId);
			itemData.put("slot", i);
			
			try {
				ItemComposition itemComp = itemManager.getItemComposition(actualItemId);
				itemData.put("name", itemComp.getName());
			} catch (Exception e) {
				itemData.put("name", "Unknown");
			}
			
			equipment.add(itemData);
		}
		
		return equipment;
	}
}


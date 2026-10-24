package com.revalclan.notifiers;

import com.revalclan.session.SessionScoped;
import com.revalclan.session.SessionState;

import com.google.gson.JsonObject;
import com.revalclan.RevalClanConfig;
import com.revalclan.util.ClanMembership;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.WebhookService;
import com.revalclan.util.Worlds;
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
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for all notification types (loot, death, pets, etc.)
 */
@Slf4j
public abstract class BaseNotifier implements SessionScoped {
	@Inject final void registerSessionState(SessionState state) { state.register(this); }

	/** Stateless notifiers need no cleanup; buffered subclasses override this hook. */
	@Override public void reset() {}
	@Inject protected Client client;

	@Inject protected WebhookService webhookService;

	@Inject protected RevalClanConfig config;

	@Inject protected EventFilterManager filterManager;

	@Inject protected ClanMembership clanMembership;
	
	@Inject protected ItemManager itemManager;

	/** The game's new collection log slot announcement; group "item" is the item name. */
	protected static final Pattern COLLECTION_LOG_PATTERN = Pattern.compile(
		"New item added to your collection log: (?<item>.+)",
		Pattern.CASE_INSENSITIVE
	);

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
	 * Clan-membership gate applied before every send — the cached per-login
	 * answer, so a hop's channel drop or a closed clan channel never swallows
	 * an event. Notifiers whose event fires after the login screen resets the
	 * cache (LOGOUT) override this; their callers gate on the previous answer.
	 */
	protected boolean passesClanCheck() {
		return clanMembership.isMember();
	}

	protected void sendNotification(Map<String, Object> data) {
		sendNotification(getEventType(), data, null);
	}

	/**
	 * Send, optionally handing the parsed JSON response to the consumer.
	 * Only successful responses reach the consumer; client access must be marshalled to ClientThread.
	 */
	protected void sendNotification(Map<String, Object> data, Consumer<JsonObject> onResponse) {
		sendNotification(getEventType(), data, onResponse);
	}

	/** The one send primitive behind the two overloads above. */
	private void sendNotification(String eventType, Map<String, Object> data, Consumer<JsonObject> onResponse) {
		if (!passesClanCheck()) return;
		addEventMetadata(eventType, data, true);
		CompletableFuture<JsonObject> delivery = webhookService.sendDataAsync(data);
		if (onResponse != null) delivery.thenAccept(onResponse);
	}

	/**
	 * Send without the inventory and equipment snapshots. For events that are a
	 * number or a name rather than a thing the player is holding (a varbit
	 * value, an item that was used up), the containers are dead weight — and a
	 * varbit can tick every 30 seconds for the length of a game.
	 */
	protected void sendCompactNotification(String eventType, Map<String, Object> data) {
		if (!passesClanCheck()) return;
		addEventMetadata(eventType, data, false);
		webhookService.sendDataAsync(data);
	}

	/**
	 * Adds standard event metadata (type, timestamp, location, inventory, etc.) to the data map.
	 * Must be called on the game thread where client access is safe.
	 */
	private void addEventMetadata(String eventType, Map<String, Object> data, boolean includeContainers) {
		data.put("eventType", eventType);
		data.put("eventTimestamp", System.currentTimeMillis());
		data.put("accountHash", client.getAccountHash());
		data.put("username", getPlayerName());
		data.put("world", client.getWorld());
		data.put("worldFlags", Worlds.flagNames(client));
		
		if (client.getLocalPlayer() != null) {
			WorldPoint wp = client.getLocalPlayer().getWorldLocation();
			data.put("worldX", wp.getX());
			data.put("worldY", wp.getY());
			data.put("plane", wp.getPlane());
			data.put("regionId", wp.getRegionID());
		}
		
		if (includeContainers) {
			data.put("inventory", getItemContainerData(93)); // Inventory container
			data.put("equipment", getItemContainerData(94)); // Equipment container
		}
	}

	/**
	 * Get the player's name
	 */
	protected String getPlayerName() {
		if (client.getLocalPlayer() != null) return client.getLocalPlayer().getName();
		return "Unknown";
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
			itemData.put("name", itemName(item.getId(), "Unknown"));

			items.add(itemData);
		}

		return items;
	}

	/**
	 * The item's name, or {@code fallback} when its composition is missing or
	 * cannot be read.
	 */
	protected String itemName(int itemId, String fallback) {
		try {
			ItemComposition composition = itemManager.getItemComposition(itemId);
			return composition != null ? composition.getName() : fallback;
		} catch (Exception e) {
			return fallback;
		}
	}
}

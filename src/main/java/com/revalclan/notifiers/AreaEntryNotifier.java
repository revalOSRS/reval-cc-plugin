package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class AreaEntryNotifier extends BaseNotifier
{
	@Inject
	private RevalClanConfig config;

	private int lastRegionId = -1;

	@Override
	public boolean isEnabled() {
		return config.notifyAreaEntry() && filterManager.getFilters().isAreaEntryEnabled();
	}

	@Override
	protected String getEventType() {
		return "AREA_ENTRY";
	}

	public void onGameTick(GameTick event)
	{
		if (!isEnabled()) return;
		if (client.getLocalPlayer() == null) return;

		WorldPoint location = client.getLocalPlayer().getWorldLocation();
		int regionId = location.getRegionID();

		if (regionId != lastRegionId && lastRegionId != -1) {
			// Only trigger if this region is in the filter list (empty = no regions trigger)
			if (filterManager.getFilters().getAreaEntryRegions().contains(regionId)) {
				handleAreaEntry(regionId, location);
			}
		}

		lastRegionId = regionId;
	}

	private void handleAreaEntry(int regionId, WorldPoint location) {
		Map<String, Object> areaData = new HashMap<>();
		areaData.put("player", getPlayerName());
		areaData.put("regionId", regionId);
		areaData.put("x", location.getX());
		areaData.put("y", location.getY());
		areaData.put("plane", location.getPlane());
		
		areaData.put("equipment", getEquippedItems());
		
		areaData.put("inventory", getInventoryData());

		sendNotification(areaData);
	}

	/**
	 * Get player's inventory data
	 */
	private List<Map<String, Object>> getInventoryData() {
		List<Map<String, Object>> inventory = new ArrayList<>();
		
		ItemContainer container = client.getItemContainer(93); // 93 or 149
		if (container == null) return inventory;
		
		Item[] items = container.getItems();
		if (items == null) return inventory;
		
		for (int i = 0; i < items.length; i++) {
			Item item = items[i];
			
			// Skip empty slots
			if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
			
			Map<String, Object> itemData = new HashMap<>();
			itemData.put("id", item.getId());
			itemData.put("quantity", item.getQuantity());
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
			
			inventory.add(itemData);
		}
		
		return inventory;
	}

	public void reset() {
		lastRegionId = -1;
	}
}


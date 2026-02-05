package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import net.runelite.api.NPC;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class LootNotifier extends BaseNotifier {
	@Inject
	private RevalClanConfig config;

	@Inject
	private ItemManager itemManager;

	/**
	 * NPC IDs that fire LootReceived instead of NpcLootReceived
	 * These should be handled in onLootReceived, not onNpcLootReceived
	 */
	private static final Set<Integer> SPECIAL_LOOT_NPC_IDS = Set.of(
		NpcID.WHISPERER, NpcID.WHISPERER_MELEE, NpcID.WHISPERER_QUEST, NpcID.WHISPERER_MELEE_QUEST,
		NpcID.ARAXXOR, NpcID.ARAXXOR_DEAD, NpcID.RT_FIRE_QUEEN_INACTIVE, NpcID.RT_ICE_KING_INACTIVE,
		NpcID.YAMA,
		NpcID.HESPORI
	);

	/**
	 * NPC names that fire LootReceived instead of NpcLootReceived
	 */
	private static final Set<String> SPECIAL_LOOT_NPC_NAMES = Set.of(
		"The Whisperer", "Araxxor",
		"Branda the Fire Queen", "Eldric the Ice King",
		"Crystalline Hunllef", "Corrupted Hunllef",
		"The Gauntlet", "Corrupted Gauntlet",
		"Shellbane Gryphon"
	);

	@Override
	public boolean isEnabled() {
		return config.notifyLoot() && filterManager.getFilters().isLootEnabled();
	}

	@Override
	protected String getEventType() {
		return "LOOT";
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event) {
		if (!isEnabled()) return;

		// Most NPCs are handled by NpcLootReceived or LootReceived to avoid duplicates
		int npcId = event.getComposition().getId();
		var name = event.getComposition().getName();
		
		// Only handle Yama, Hespori, and Hallowed Sepulchre
		if (npcId != NpcID.YAMA && npcId != NpcID.HESPORI && !name.startsWith("Hallowed Sepulchre")) {
			return;
		}

		var comp = event.getComposition();
		handleLootDrop(event.getItems(), comp.getName(), "NPC", comp.getId());
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event){
		if (!isEnabled()) return;

		NPC npc = event.getNpc();
		int npcId = npc.getId();

		// Skip NPCs that fire LootReceived instead (to avoid duplicates)
		if (SPECIAL_LOOT_NPC_IDS.contains(npcId)) return;

		Collection<ItemStack> items = event.getItems();
		handleLootDrop(items, npc.getName(), "NPC", npcId);
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event) {
		if (!isEnabled()) return;

		String playerName = event.getPlayer().getName();
		Collection<ItemStack> items = event.getItems();

		handleLootDrop(items, playerName, "PLAYER", null);
	}

	@Subscribe
	public void onLootReceived(LootReceived event) {
		if (!isEnabled()) return;

		// Handle EVENT and PICKPOCKET types
		// EVENT type includes: raids (Chambers of Xeric, Theatre of Blood, Tombs of Amascut),
		// moons (Moons of Peril), barrows chests, gauntlet chests, and other special content
		if (event.getType() == LootRecordType.EVENT || event.getType() == LootRecordType.PICKPOCKET) {
			String source = event.getName();
			handleLootDrop(event.getItems(), source, "EVENT", null);
		} 
		// Handle special NPCs that fire LootReceived instead of NpcLootReceived
		else if (event.getType() == LootRecordType.NPC && SPECIAL_LOOT_NPC_NAMES.contains(event.getName())) {
			String source = event.getName();
			if ("The Gauntlet".equals(source) || "Corrupted Gauntlet".equals(source)) {
				handleLootDrop(event.getItems(), source, "EVENT", null);
			} else {
				handleLootDrop(event.getItems(), source, "NPC", null);
			}
		}
	}

	/**
	 * Handle game messages for special loot cases that don't fire normal loot events
	 */
	public void onGameMessage(String message) {
		if (!isEnabled()) return;

		// Pyramid Plunder: Pharaoh's sceptre doesn't fire a normal loot event
		if ("You have found the Pharaoh's sceptre!".equals(message) || "You have found a Pharaoh's sceptre!".equals(message)) {
			handleLootDrop(List.of(new ItemStack(ItemID.PHARAOHS_SCEPTRE, 1)), "Pyramid Plunder", "EVENT", null);
		}
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

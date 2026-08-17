package com.revalclan.notifiers;

import com.revalclan.session.SessionTracker;
import com.revalclan.util.RaidParty;
import net.runelite.api.NPC;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class LootNotifier extends BaseNotifier {
	@Inject
	private SessionTracker sessionTracker;

	private static final Pattern COLLECTION_LOG_PATTERN = Pattern.compile(
		"New item added to your collection log: (?<item>.+)",
		Pattern.CASE_INSENSITIVE
	);

	/** Ticks to hold a loot payload before sending (correlation window). */
	private static final int LOOT_BUFFER_TICKS = 2;

	/** How many ticks a clog announcement stays eligible for matching. */
	private static final int CLOG_MESSAGE_TTL_TICKS = 10;

	/** Lowercased item name → tick the clog announcement was seen on. */
	private final Map<String, Integer> recentClogItems = new HashMap<>();

	/** Loot payloads waiting out the correlation window. */
	private final List<PendingLoot> pendingLoot = new ArrayList<>();

	private int tickCounter = 0;

	private static class PendingLoot {
		final Map<String, Object> lootData;
		final List<Map<String, Object>> items;
		final int sendOnTick;

		PendingLoot(Map<String, Object> lootData, List<Map<String, Object>> items, int sendOnTick) {
			this.lootData = lootData;
			this.items = items;
			this.sendOnTick = sendOnTick;
		}
	}

	/**
	 * Mad Angel (Wyrmscraig) — all encounter variant ids: base, initial, anim and
	 * dead forms, their quest mirrors, and the cathedral (+ vis) forms. Constants
	 * are not yet in the released runelite-api gameval NpcID, so the ids are
	 * inlined (values match MAD_ANGEL..MAD_ANGEL_CATHEDRAL_VIS_QUEST on the api
	 * master branch).
	 */
	private static final Set<Integer> MAD_ANGEL_IDS = Set.of(
		16305, 16306, 16307, 16308, 16309, 16310, 16311, 16312, 16313, 16314, 16315
	);

	/**
	 * Sailing sea creatures only fire ServerNpcLoot (loot is granted server-side
	 * to the whole crew, often while nobody is adjacent to the corpse), so they
	 * must be allowed through onServerNpcLoot.
	 */
	private static final Set<Integer> SAILING_NPC_IDS = Set.of(
		NpcID.SAILING_BULL_SHARK_DEAD,
		NpcID.SAILING_HAMMERHEAD_SHARK_DEAD,
		NpcID.SAILING_TIGER_SHARK_DEAD,
		NpcID.SAILING_GREAT_WHITE_SHARK_DEAD,
		NpcID.SAILING_NARWHAL_DEAD,
		NpcID.SAILING_ORCA_DEAD,
		NpcID.SAILING_PYGMY_KRAKEN_DEAD,
		NpcID.SAILING_SPINED_KRAKEN_DEAD,
		NpcID.SAILING_ARMOURED_KRAKEN_DEAD,
		NpcID.SAILING_VAMPYRE_KRAKEN_DEAD,
		NpcID.SAILING_EAGLE_RAY_DEAD,
		NpcID.SAILING_BUTTERFLY_RAY_DEAD,
		NpcID.SAILING_STINGRAY_DEAD,
		NpcID.SAILING_MANTA_RAY_DEAD,
		NpcID.SAILING_OSPREY_DEAD,
		NpcID.SAILING_ALBATROSS_DEAD,
		NpcID.SAILING_FRIGATEBIRD_DEAD,
		NpcID.SAILING_TERN_DEAD,
		NpcID.SAILING_SEA_MOGRE_DEAD,
		NpcID.SAILING_DOLPHIN_DEAD,
		NpcID.SAILING_VEILED_KRAKEN_DEAD
	);

	/**
	 * NPC IDs that fire LootReceived instead of NpcLootReceived
	 * These should be handled in onLootReceived, not onNpcLootReceived
	 */
	private static final Set<Integer> SPECIAL_LOOT_NPC_IDS = Set.of(
		NpcID.WHISPERER, NpcID.WHISPERER_MELEE, NpcID.WHISPERER_QUEST, NpcID.WHISPERER_MELEE_QUEST,
		NpcID.ARAXXOR, NpcID.ARAXXOR_DEAD, NpcID.RT_FIRE_QUEEN_INACTIVE, NpcID.RT_ICE_KING_INACTIVE,
		NpcID.YAMA,
		NpcID.HESPORI,
		NpcID.GRYPHON_BOSS,
		NpcID.GB_HILLGIANT_CHEST,
		NpcID.GB_MOSSGIANT_CHEST
	);

	/**
	 * Sailing deep sea trawling trophy fish are announced by chat message only —
	 * no loot event fires for them.
	 */
	private static final Map<String, Integer> TRAWLING_TROPHY_MESSAGES = Map.of(
		"You catch a giant blue krill!", ItemID.POH_TROPHYDROP_GIANT_KRILL,
		"You catch a golden haddock!", ItemID.POH_TROPHYDROP_HADDOCK,
		"You catch a orangefin!", ItemID.POH_TROPHYDROP_YELLOWFIN,
		"You catch a huge halibut!", ItemID.POH_TROPHYDROP_HALIBUT,
		"You catch a purplefin!", ItemID.POH_TROPHYDROP_BLUEFIN,
		"You catch a swift marlin!", ItemID.POH_TROPHYDROP_MARLIN,
		"You've received some paint!", ItemID.SAILING_PAINT_ANGLERS
	);

	/**
	 * NPC names that fire LootReceived instead of NpcLootReceived
	 */
	private static final Set<String> SPECIAL_LOOT_NPC_NAMES = Set.of(
		"The Whisperer", "Araxxor", "Maggot King",
		"Branda the Fire Queen", "Eldric the Ice King",
		"Crystalline Hunllef", "Corrupted Hunllef",
		"The Gauntlet", "Corrupted Gauntlet",
		"Shellbane gryphon",
		"Obor (Chest)",
		"Bryophyta (Chest)"
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

		// Only handle Yama, Hespori, Mad Angel, sailing sea creatures, and Hallowed Sepulchre
		if (npcId != NpcID.YAMA && npcId != NpcID.HESPORI && !MAD_ANGEL_IDS.contains(npcId)
			&& !SAILING_NPC_IDS.contains(npcId) && !name.startsWith("Hallowed Sepulchre")) {
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

		// Skip NPCs that fire LootReceived or ServerNpcLoot instead (to avoid duplicates)
		if (SPECIAL_LOOT_NPC_IDS.contains(npcId) || MAD_ANGEL_IDS.contains(npcId) || SAILING_NPC_IDS.contains(npcId)) return;

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
			// For raid chests (CoX / ToB / ToA) attach the party members present
			handleLootDrop(event.getItems(), source, "EVENT", null, RaidParty.getMembers(client, source));
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
	 * Handle game messages for special loot cases that don't fire normal loot events,
	 * and record collection log announcements for loot correlation
	 */
	public void onGameMessage(String message) {
		if (!isEnabled()) return;

		// Track "New item added to your collection log: X" for the buffered loot flag
		Matcher clogMatcher = COLLECTION_LOG_PATTERN.matcher(message);
		if (clogMatcher.find()) {
			recentClogItems.put(clogMatcher.group("item").trim().toLowerCase(), tickCounter);
			return;
		}

		// Pyramid Plunder: Pharaoh's sceptre doesn't fire a normal loot event
		if ("You have found the Pharaoh's sceptre!".equals(message) || "You have found a Pharaoh's sceptre!".equals(message)) {
			handleLootDrop(List.of(new ItemStack(ItemID.PHARAOHS_SCEPTRE, 1)), "Pyramid Plunder", "EVENT", null);
			return;
		}

		// Sailing deep sea trawling: trophy fish don't fire a normal loot event
		Integer trophyFish = TRAWLING_TROPHY_MESSAGES.get(message);
		if (trophyFish != null) {
			handleLootDrop(List.of(new ItemStack(trophyFish, 1)), "Deep sea trawling", "EVENT", null);
		}
	}

	/**
	 * Flush loot payloads whose correlation window has elapsed, marking each item
	 * with whether the game announced it as a new collection log slot
	 */
	public void onGameTick() {
		tickCounter++;

		if (!pendingLoot.isEmpty()) {
			Iterator<PendingLoot> it = pendingLoot.iterator();
			while (it.hasNext()) {
				PendingLoot pending = it.next();
				if (tickCounter >= pending.sendOnTick) {
					// Remove before sending so a failure can never wedge the
					// queue into retrying (and re-throwing) every tick
					it.remove();
					try {
						for (Map<String, Object> item : pending.items) {
							String name = String.valueOf(item.get("name")).toLowerCase();
							Integer seenTick = recentClogItems.get(name);
							boolean isNewClogSlot = seenTick != null
								&& tickCounter - seenTick <= CLOG_MESSAGE_TTL_TICKS;
							item.put("isNewCollectionLogItem", isNewClogSlot);
						}
						sendNotification(pending.lootData);
					} catch (Exception ignored) {
						// Never let one payload break the tick dispatch for
						// other pending loot or the notifiers after us
					}
				}
			}
		}

		// Expire stale clog announcements so the map can't grow unbounded
		if (!recentClogItems.isEmpty()) {
			recentClogItems.values().removeIf(tick -> tickCounter - tick > CLOG_MESSAGE_TTL_TICKS);
		}
	}

	private void handleLootDrop(Collection<ItemStack> items, String source, String sourceType, Integer sourceId) {
		handleLootDrop(items, source, sourceType, sourceId, null);
	}

	private void handleLootDrop(Collection<ItemStack> items, String source, String sourceType, Integer sourceId, List<String> partyMembers) {
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

			sessionTracker.addLoot(source, itemId, itemName, item.getQuantity(), gePrice);

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
		if (partyMembers != null) {
			lootData.put("partyMembers", partyMembers);
		}
		lootData.put("totalGEValue", totalGEValue);
		lootData.put("totalHAValue", totalHAValue);
		lootData.put("items", itemsList);

		// Buffer for the clog correlation window instead of sending immediately;
		// onGameTick stamps isNewCollectionLogItem on each item and sends
		pendingLoot.add(new PendingLoot(lootData, itemsList, tickCounter + LOOT_BUFFER_TICKS));
	}
}

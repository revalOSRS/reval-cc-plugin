package com.revalclan.notifiers;

import com.revalclan.session.SessionTracker;
import com.revalclan.util.RaidParty;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
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

	/** Ticks a self-dropped item stays suspect for ground-spawn loot attribution. */
	private static final int SELF_DROP_SUSPECT_TICKS = 10;

	/** The swap is same-tick with the loot event; slack only absorbs event ordering. */
	private static final int UNEQUIP_SUSPECT_TICKS = 2;

	/** Lowercased item name → tick the clog announcement was seen on. */
	private final Map<String, Integer> recentClogItems = new HashMap<>();

	/** Item id → observed self-drop by the local player. */
	private final Map<Integer, ObservedInflow> recentSelfDrops = new HashMap<>();

	/** Item id → observed removal from the worn container (unequip or 2h/shield swap). */
	private final Map<Integer, ObservedInflow> recentUnequips = new HashMap<>();

	/** Last observed worn contents (id → quantity), diffed to spot removals. */
	private final Map<Integer, Integer> equipmentSnapshot = new HashMap<>();

	/** Loot payloads waiting out the correlation window. */
	private final List<PendingLoot> pendingLoot = new ArrayList<>();

	/** A real loot event this recently for the same source means: do not synthesise. */
	private static final int REAL_LOOT_GUARD_TICKS = 3;

	/** A watched item vanishing this soon after the local player died is the death, not a spend. */
	private static final int DEATH_GUARD_TICKS = 5;

	/**
	 * Interfaces through which an item can leave the inventory without being
	 * spent. A watched item vanishing while any of these is open is storage or
	 * transfer, never consumption — reporting it would mint prep-gate allowance
	 * for work nobody did, which is the one thing this feature must not do.
	 */
	private static final int[] NON_CONSUMPTION_INTERFACES = {
		InterfaceID.BANKMAIN,
		InterfaceID.BANK_DEPOSITBOX,
		InterfaceID.GE_OFFERS,
		InterfaceID.GE_OFFERS_SIDE,
		InterfaceID.TRADEMAIN,
		InterfaceID.TRADECONFIRM,
		InterfaceID.SHOPMAIN,
		InterfaceID.SHOPSIDE,
	};

	/**
	 * Source name (lowercase) → tick of the last loot event the GAME reported.
	 * Only genuine reports belong here: recording our own synthesised events
	 * makes each one suppress the next.
	 */
	private final Map<String, Integer> recentRealLootSources = new HashMap<>();

	/**
	 * Inventory as of the previous change (id → quantity). Every silent
	 * consumption is a watched item's count dropping between two changes, so the
	 * previous state is the only "before" needed — no click has to arm anything,
	 * which matters because Tithe fruit is deposited by clicking the sack.
	 */
	private final Map<Integer, Integer> lastInventory = new HashMap<>();
	private boolean lastInventoryKnown = false;

	private int lastLocalDeathTick = Integer.MIN_VALUE;

	private int tickCounter = 0;

	private static class PendingLoot {
		final Map<String, Object> lootData;
		final List<Map<String, Object>> items;
		final int lootTick;
		final int sendOnTick;

		PendingLoot(Map<String, Object> lootData, List<Map<String, Object>> items, int lootTick, int sendOnTick) {
			this.lootData = lootData;
			this.items = items;
			this.lootTick = lootTick;
			this.sendOnTick = sendOnTick;
		}
	}

	/**
	 * Observations inside the window accumulate, so a loot stack can match either
	 * the last single quantity or the window total (three separate 1x drops may
	 * be attributed as one 3x stack).
	 */
	private static class ObservedInflow {
		int tick;
		int lastQuantity;
		int totalQuantity;
	}

	private static void recordInflow(Map<Integer, ObservedInflow> map, int itemId, int quantity, int tick, int windowTicks) {
		ObservedInflow entry = map.get(itemId);
		if (entry == null || tick - entry.tick > windowTicks) {
			entry = new ObservedInflow();
			map.put(itemId, entry);
		}
		entry.tick = tick;
		entry.lastQuantity = quantity;
		entry.totalQuantity += quantity;
	}

	private static boolean matchesInflow(ObservedInflow entry, int quantity) {
		return entry != null && (quantity == entry.lastQuantity || quantity == entry.totalQuantity);
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

	/** Note that the game reported loot for this source, for the diff's duplicate guard. */
	private void recordRealLootSource(String source) {
		if (source != null && !source.isEmpty()) {
			recentRealLootSources.put(source.toLowerCase(), tickCounter);
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event){
		if (!isEnabled()) return;

		NPC npc = event.getNpc();
		int npcId = npc.getId();
		recordRealLootSource(npc.getName());

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
		recordRealLootSource(playerName);

		handleLootDrop(items, playerName, "PLAYER", null);
	}

	@Subscribe
	public void onLootReceived(LootReceived event) {
		if (!isEnabled()) return;

		// Any loot the tracker itself reports makes an armed diff stand down.
		recordRealLootSource(event.getName());

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
	 * Dropping an item on a death tile inside LootManager's attribution window
	 * forges a "drop", so remember every Drop click. Never gated on isEnabled:
	 * tracking state must stay correct.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (!"Drop".equals(event.getMenuOption())) return;

		int itemId = event.getItemId();
		if (itemId <= 0) return;

		// Dropping ejects the whole clicked stack (param0 = inventory slot)
		int quantity = 1;
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null) {
			Item slotItem = inventory.getItem(event.getParam0());
			if (slotItem != null && slotItem.getId() == itemId) {
				quantity = slotItem.getQuantity();
			}
		}

		recordInflow(recentSelfDrops, itemId, quantity, tickCounter, SELF_DROP_SUSPECT_TICKS);
	}

	/** Current inventory as id → total quantity. Empty when there is no inventory. */
	private Map<Integer, Integer> snapshotInventory() {
		Map<Integer, Integer> counts = new HashMap<>();
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null) return counts;
		for (Item item : inventory.getItems()) {
			if (item.getId() > 0 && item.getQuantity() > 0) {
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return counts;
	}

	private String itemName(int itemId) {
		try {
			ItemComposition composition = itemManager.getItemComposition(itemId);
			return composition != null ? composition.getName() : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Report watched items that left the inventory in this change.
	 *
	 * If exactly one watched item dropped and other items rose, the gain is that
	 * item's contents: sent as loot with the item's own name as the source, which
	 * is what lets the backend tell a jumbo squid dissect from a swordtip one.
	 * Otherwise each lost item is a consumption with nothing given back — Tithe
	 * fruit into the sack. Each dissect is its own container change, so a whole
	 * stack processed from one click is reported one item at a time.
	 */
	private void reportWatchedConsumption(Map<Integer, Integer> before, Map<Integer, Integer> after) {
		if (!isEnabled()) return;

		Set<String> watched = filterManager.getFilters().getInventoryWatchItems();
		if (watched.isEmpty()) return;

		Map<Integer, Integer> lost = new HashMap<>();
		for (Map.Entry<Integer, Integer> was : before.entrySet()) {
			int delta = was.getValue() - after.getOrDefault(was.getKey(), 0);
			if (delta <= 0) continue;
			String name = itemName(was.getKey());
			if (name != null && watched.contains(name.toLowerCase()) && !isNonConsumption(was.getKey())) {
				lost.put(was.getKey(), delta);
			}
		}
		if (lost.isEmpty()) return;

		Map<Integer, Integer> gained = new HashMap<>();
		for (Map.Entry<Integer, Integer> now : after.entrySet()) {
			int delta = now.getValue() - before.getOrDefault(now.getKey(), 0);
			if (delta > 0 && !lost.containsKey(now.getKey())) gained.put(now.getKey(), delta);
		}

		if (lost.size() == 1 && !gained.isEmpty()) {
			int itemId = lost.keySet().iterator().next();
			String source = itemName(itemId);
			// The loot tracker already reported this source; ours would be a duplicate.
			Integer realTick = recentRealLootSources.get(source.toLowerCase());
			if (realTick != null && tickCounter - realTick <= REAL_LOOT_GUARD_TICKS) return;

			List<ItemStack> stacks = new ArrayList<>();
			for (Map.Entry<Integer, Integer> g : gained.entrySet()) stacks.add(new ItemStack(g.getKey(), g.getValue()));
			// No sourceId: that field is an NPC id everywhere else and the backend
			// filters on it. The source name is the contract here.
			handleLootDrop(stacks, source, "EVENT", null);
			return;
		}

		for (Map.Entry<Integer, Integer> l : lost.entrySet()) {
			sendItemConsumed(l.getKey(), itemName(l.getKey()), l.getValue());
		}
	}

	/** Storage, transfer, death or a deliberate drop — the item left, but nothing was spent. */
	private boolean isNonConsumption(int itemId) {
		if (tickCounter - lastLocalDeathTick <= DEATH_GUARD_TICKS) return true;
		for (int interfaceId : NON_CONSUMPTION_INTERFACES) {
			if (client.getWidget(interfaceId) != null) return true;
		}
		ObservedInflow dropped = recentSelfDrops.get(itemId);
		return dropped != null && tickCounter - dropped.tick <= SELF_DROP_SUSPECT_TICKS;
	}

	/**
	 * Report an item that gave nothing back. Not routed through handleLootDrop:
	 * there is no loot, so the value filters would discard it.
	 */
	private void sendItemConsumed(int itemId, String itemName, int quantity) {
		if (quantity <= 0 || itemName == null) return;

		Map<String, Object> data = new HashMap<>();
		data.put("itemId", itemId);
		data.put("itemName", itemName);
		data.put("quantity", quantity);
		data.put("source", itemName);
		sendNotification("ITEM_CONSUMED", data, null);
	}

	/** The local player died: whatever leaves the inventory next is the death, not a spend. */
	public void onActorDeath(ActorDeath event) {
		if (event.getActor() == client.getLocalPlayer()) {
			lastLocalDeathTick = tickCounter;
		}
	}

	/** Forget everything tied to the session that just ended. */
	public void reset() {
		lastInventory.clear();
		lastInventoryKnown = false;
		recentRealLootSources.clear();
		lastLocalDeathTick = Integer.MIN_VALUE;
	}

	/**
	 * Inventory-diff loot sources (nests, caskets, pickpockets, chest bosses)
	 * count gear pushed out of the worn container by a same-tick equip swap as
	 * loot, so remember what left it. Never gated on isEnabled: a missed change
	 * makes later diffs report phantom removals.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		if (event.getContainerId() == InventoryID.INV) {
			Map<Integer, Integer> after = snapshotInventory();
			if (lastInventoryKnown) {
				reportWatchedConsumption(lastInventory, after);
			}
			lastInventory.clear();
			lastInventory.putAll(after);
			lastInventoryKnown = true;
			return;
		}

		if (event.getContainerId() != InventoryID.WORN) return;

		Map<Integer, Integer> worn = new HashMap<>();
		for (Item item : event.getItemContainer().getItems()) {
			if (item.getId() > 0 && item.getQuantity() > 0) {
				worn.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		for (Map.Entry<Integer, Integer> was : equipmentSnapshot.entrySet()) {
			int removed = was.getValue() - worn.getOrDefault(was.getKey(), 0);
			if (removed > 0) {
				recordInflow(recentUnequips, was.getKey(), removed, tickCounter, UNEQUIP_SUSPECT_TICKS);
			}
		}

		equipmentSnapshot.clear();
		equipmentSnapshot.putAll(worn);
	}

	/**
	 * Reason this loot stack is explained by an observed local action, or null
	 * when it looks legitimate. Exact id + quantity match, so a legitimate stack
	 * is never tainted by a near-miss. The drop window is one-sided (the click
	 * precedes the spawn); the unequip window is two-sided (the container event
	 * can land either side of the loot event).
	 */
	private String suspectReason(int itemId, int quantity, int lootTick) {
		ObservedInflow drop = recentSelfDrops.get(itemId);
		if (drop != null && lootTick - drop.tick <= SELF_DROP_SUSPECT_TICKS && drop.tick - lootTick <= 1
			&& matchesInflow(drop, quantity)) {
			return "self-drop";
		}

		ObservedInflow unequip = recentUnequips.get(itemId);
		if (unequip != null && Math.abs(lootTick - unequip.tick) <= UNEQUIP_SUSPECT_TICKS
			&& matchesInflow(unequip, quantity)) {
			return "gear-swap";
		}

		return null;
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
						boolean newlySuspect = false;
						List<Map<String, Object>> cleanItems = new ArrayList<>();
						List<Map<String, Object>> suspectedItems = new ArrayList<>();

						for (Map<String, Object> item : pending.items) {
							// Re-check at flush — the worn-container event can land
							// a tick after the loot event
							if (item.get("suspectReason") == null) {
								String reason = suspectReason((Integer) item.get("id"),
									((Number) item.get("quantity")).intValue(), pending.lootTick);
								if (reason != null) {
									item.put("suspectReason", reason);
									newlySuspect = true;
								}
							}

							String name = String.valueOf(item.get("name")).toLowerCase();
							Integer seenTick = recentClogItems.get(name);
							boolean isNewClogSlot = seenTick != null
								&& tickCounter - seenTick <= CLOG_MESSAGE_TTL_TICKS;
							item.put("isNewCollectionLogItem", isNewClogSlot);

							if (item.get("suspectReason") != null) {
								suspectedItems.add(item);
							} else {
								cleanItems.add(item);
							}
						}

						// Consumers award off items[]; suspectedItems[] keeps the
						// attempt visible server-side for auditing
						pending.lootData.put("items", cleanItems);
						if (!suspectedItems.isEmpty()) {
							pending.lootData.put("suspectedItems", suspectedItems);
							if (newlySuspect) {
								applyCleanTotals(pending.lootData, cleanItems);
							}
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

		// Expire provenance entries once they can no longer match a buffered payload
		if (!recentSelfDrops.isEmpty()) {
			recentSelfDrops.values().removeIf(e -> tickCounter - e.tick > SELF_DROP_SUSPECT_TICKS + LOOT_BUFFER_TICKS);
		}
		if (!recentUnequips.isEmpty()) {
			recentUnequips.values().removeIf(e -> tickCounter - e.tick > UNEQUIP_SUSPECT_TICKS + LOOT_BUFFER_TICKS);
		}
		if (!recentRealLootSources.isEmpty()) {
			recentRealLootSources.values().removeIf(tick -> tickCounter - tick > REAL_LOOT_GUARD_TICKS + LOOT_BUFFER_TICKS);
		}
	}

	/** Rewrite totals when suspects were detected after receive-time totals were computed. */
	private void applyCleanTotals(Map<String, Object> lootData, List<Map<String, Object>> cleanItems) {
		long totalGEValue = 0;
		long totalHAValue = 0;
		for (Map<String, Object> item : cleanItems) {
			long quantity = ((Number) item.get("quantity")).longValue();
			totalGEValue += ((Number) item.get("gePrice")).longValue() * quantity;
			totalHAValue += ((Number) item.get("haValue")).longValue() * quantity;
		}
		lootData.put("totalGEValue", totalGEValue);
		lootData.put("totalHAValue", totalHAValue);
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

		for (ItemStack item : items) {
			int itemId = item.getId();

			// Skip blacklisted items
			if (blacklistItemIds.contains(itemId)) continue;

			int gePrice = itemManager.getItemPrice(itemId);
			int haValue = itemManager.getItemComposition(itemId).getPrice();
			boolean isTradeable = itemManager.getItemComposition(itemId).isTradeable();
			String itemName = itemManager.getItemComposition(itemId).getName();
			long stackValue = (long) gePrice * item.getQuantity();

			String suspectReason = suspectReason(itemId, item.getQuantity(), tickCounter);

			// Sessions count everything received, so track before filtering
			if (suspectReason == null) {
				sessionTracker.addLoot(source, itemId, itemName, item.getQuantity(), gePrice);
			}

			// Unit price, not stack: two 700k items are not a 1M drop
			if (gePrice < minLootValue && !whitelistItemIds.contains(itemId)) continue;

			Map<String, Object> itemData = new HashMap<>();
			itemData.put("id", itemId);
			itemData.put("name", itemName);
			itemData.put("quantity", item.getQuantity());
			itemData.put("gePrice", gePrice);
			itemData.put("haValue", haValue);
			itemData.put("tradeable", isTradeable);
			if (suspectReason != null) {
				itemData.put("suspectReason", suspectReason);
			}
			itemsList.add(itemData);

			// Split out of items[] at flush, so they must not count toward totals
			if (suspectReason != null) continue;

			totalGEValue += stackValue;
			totalHAValue += (long) haValue * item.getQuantity();
		}

		if (itemsList.isEmpty()) return;

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
		pendingLoot.add(new PendingLoot(lootData, itemsList, tickCounter, tickCounter + LOOT_BUFFER_TICKS));
	}
}

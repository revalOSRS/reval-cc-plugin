package com.revalclan.shops;

import com.revalclan.notifiers.BaseNotifier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * EXPERIMENTAL: detects shop BUY / SELL transactions.
 *
 * Two shop modes are covered:
 * 1. Standard GP shops — interface group {@link InterfaceID#SHOPMAIN} (300). The shop
 *    stock lives in a per-shop item container (each shop has its own gameval
 *    InventoryID, e.g. AXESHOP=1 — there is NO single generic shop container id), so
 *    the stock container id is learned from the first non-player ItemContainerChanged
 *    that arrives while the shop interface is open.
 * 2. "Reward" shops (Mastering Mixology, Tithe Farm, NMZ, LMS, ...) — arbitrary
 *    interfaces whose prices are paid in points/currencies stored in varps/varbits.
 *    Heuristic: while any non-standard (non-blacklisted) interface group is open, any
 *    menu click whose option looks like Buy/Sell/Purchase/Exchange/Confirm/Redeem is
 *    treated as a candidate transaction.
 *
 * On a candidate click the tracker snapshots the player inventory (container 93),
 * coins, and the shop stock (standard mode); two ticks later it diffs and pairs the
 * result with a rolling window of recent varp/varbit deltas to produce a
 * SHOP_TRANSACTION payload.
 *
 * Experiment gating: webhook delivery and in-game chat feedback only happen when
 * config.debugMode() is on; local slf4j + JSONL logging always happens for detected
 * transactions. All raw event instrumentation (ShopDebugLog) additionally requires
 * debug mode.
 */
@Slf4j
@Singleton
public class ShopTracker extends BaseNotifier {
	/** Player containers (gameval InventoryID): INV=93, WORN=94, BANK=95 */
	private static final int INV_CONTAINER = InventoryID.INV; // 93
	private static final int EQUIPMENT_CONTAINER = 94;
	private static final int BANK_CONTAINER = InventoryID.BANK; // 95

	private static final int COINS_ITEM_ID = ItemID.COINS; // 995

	/** Menu options that look like a shop transaction: "Buy 5", "Buy-10", "Purchase", "Confirm", ... */
	private static final Pattern CANDIDATE_OPTION = Pattern.compile(
		"^(buy|sell|purchase|exchange|redeem|claim|confirm)([ -].*)?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SELL_OPTION = Pattern.compile("^sell([ -].*)?$", Pattern.CASE_INSENSITIVE);

	/** Interface groups that are always open / clearly not shops — never treated as reward-shop candidates. */
	private static final Set<Integer> IGNORED_GROUPS = new HashSet<>(List.of(
		InterfaceID.TOPLEVEL,             // 548 fixed viewport
		InterfaceID.TOPLEVEL_OSRS_STRETCH,// 161 resizable classic
		InterfaceID.TOPLEVEL_PRE_EOC,     // 164 resizable modern
		InterfaceID.CHATBOX,              // 162
		InterfaceID.PM_CHAT,              // 163
		InterfaceID.ORBS,                 // 160
		InterfaceID.INVENTORY,            // 149
		InterfaceID.EQUIPMENT,            // 84
		InterfaceID.BANKMAIN,             // 12
		InterfaceID.BANKSIDE,             // 15
		InterfaceID.GE_OFFERS,            // 465 grand exchange (has Buy/Sell but is not a shop)
		InterfaceID.GE_COLLECT,           // 402
		InterfaceID.SEED_VAULT,           // 631
		InterfaceID.WORLDMAP,             // 595
		InterfaceID.CHAT_LEFT,            // 231 npc dialog
		InterfaceID.CHAT_RIGHT,           // 217 player dialog
		InterfaceID.CHATMENU,             // 219 option dialog
		InterfaceID.MUSIC,                // 239
		InterfaceID.QUESTLIST,            // 399
		InterfaceID.FRIENDS,              // 429
		InterfaceID.SETTINGS_SIDE,        // 116
		InterfaceID.PRAYERBOOK,           // 541
		InterfaceID.MAGIC_SPELLBOOK,      // 218
		InterfaceID.COMBAT_INTERFACE,     // 593
		InterfaceID.SHOPSIDE              // 301 tracked implicitly with SHOPMAIN
	));

	/** How many ticks of varp/varbit history to keep for currency-delta attribution. */
	private static final int VAR_WINDOW_TICKS = 10;
	/** Ticks to wait after a candidate click before diffing state. */
	private static final int RESOLVE_DELAY_TICKS = 2;
	private static final int MAX_CURRENCY_DELTAS = 40;

	@Inject
	private ShopDebugLog debugLog;

	// ── Tracker state (client thread only) ─────────────────────────────
	private int tick;
	private boolean standardShopOpen;
	private String shopName;
	/** Learned stock container id of the currently open standard shop, -1 if unknown. */
	private int shopContainerId = -1;
	/** Non-standard, non-blacklisted interface groups currently loaded (reward-shop candidates). */
	private final Set<Integer> openCandidateGroups = new HashSet<>();

	/** Last observed values so VarbitChanged (which only carries the new value) can report old -> new. */
	private final Map<Integer, Integer> lastVarpValues = new HashMap<>();
	private final Map<Integer, Integer> lastVarbitValues = new HashMap<>();
	/** Rolling window of recent varp/varbit changes. */
	private final Deque<VarChange> recentVarChanges = new ArrayDeque<>();

	/** Per-container previous state, kept only while shop-ish tracking is active (for changed-slot debug logging). */
	private final Map<Integer, Map<Integer, int[]>> containerStates = new HashMap<>();

	private PendingTransaction pending;

	@Override
	public boolean isEnabled() {
		return config.debugMode();
	}

	@Override
	protected String getEventType() {
		return "SHOP_TRANSACTION";
	}

	/** A shop-ish interface is open — raw event instrumentation is worth recording. */
	private boolean shopContextOpen() {
		return standardShopOpen || !openCandidateGroups.isEmpty();
	}

	private boolean debugActive() {
		return shopContextOpen() && config.debugMode();
	}

	// ── Event handlers ─────────────────────────────────────────────────

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		int group = event.getGroupId();

		if (group == InterfaceID.SHOPMAIN) {
			standardShopOpen = true;
			shopName = null;      // read from the title widget on the next tick (text may not be set yet)
			shopContainerId = -1; // learned from the next non-player ItemContainerChanged
		} else if (!IGNORED_GROUPS.contains(group)) {
			openCandidateGroups.add(group);
		}

		// Group ids are the key to mapping unknown reward shops — log all loads while debugging
		if (config.debugMode()) {
			Map<String, Object> f = new LinkedHashMap<>();
			f.put("groupId", group);
			f.put("standardShop", group == InterfaceID.SHOPMAIN);
			f.put("candidate", openCandidateGroups.contains(group));
			debugLog.write("widget_loaded", tick, f);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		int group = event.getGroupId();

		if (config.debugMode()) {
			Map<String, Object> f = new LinkedHashMap<>();
			f.put("groupId", group);
			f.put("modalMode", event.getModalMode());
			f.put("unload", event.isUnload());
			debugLog.write("widget_closed", tick, f);
		}

		if (group == InterfaceID.SHOPMAIN) {
			// Resolve any in-flight click before losing the shop context
			resolvePendingNow("shop_closed");
			standardShopOpen = false;
			shopName = null;
			shopContainerId = -1;
		} else {
			openCandidateGroups.remove(group);
		}

		if (!shopContextOpen()) {
			containerStates.clear();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		String option = event.getMenuOption();

		if (debugActive()) {
			debugLog.write("menu_click", tick, describeClick(event));
		}

		if (option == null || !shopContextOpen() || !CANDIDATE_OPTION.matcher(option).matches()) {
			return;
		}

		// Overlapping clicks: settle the previous candidate with the state we have now
		resolvePendingNow("superseded");

		PendingTransaction p = new PendingTransaction();
		p.clickTick = tick;
		p.resolveTick = tick + RESOLVE_DELAY_TICKS;
		p.menuOption = option;
		p.menuTarget = event.getMenuTarget();
		p.menuAction = event.getMenuAction() != null ? event.getMenuAction().name() : null;
		p.clickedItemId = event.getItemId();
		p.widgetId = widgetIdOf(event);
		p.param0 = event.getParam0();
		p.param1 = event.getParam1();
		p.standardShop = standardShopOpen;
		p.shopName = shopName != null ? shopName : "unknown";
		p.interfaceGroup = resolveInterfaceGroup(event);
		p.invSnapshot = snapshotContainer(INV_CONTAINER);
		p.coinsSnapshot = countOf(p.invSnapshot, COINS_ITEM_ID);
		p.shopSnapshot = (standardShopOpen && shopContainerId != -1) ? snapshotContainer(shopContainerId) : null;
		pending = p;

		if (debugActive()) {
			Map<String, Object> f = new LinkedHashMap<>();
			f.put("menuOption", option);
			f.put("resolveTick", p.resolveTick);
			f.put("interfaceGroup", p.interfaceGroup);
			f.put("standardShop", p.standardShop);
			debugLog.write("candidate_click", tick, f);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		int id = event.getContainerId();

		// Learn the standard shop's stock container: each shop has its own inventory id,
		// so take the first non-player container that changes while the shop is open.
		if (standardShopOpen && shopContainerId == -1
			&& id != INV_CONTAINER && id != EQUIPMENT_CONTAINER && id != BANK_CONTAINER) {
			shopContainerId = id;
			if (config.debugMode()) {
				Map<String, Object> f = new LinkedHashMap<>();
				f.put("containerId", id);
				debugLog.write("shop_container_learned", tick, f);
			}
		}

		if (debugActive()) {
			debugLog.write("container_changed", tick, describeContainerChange(id, event.getItemContainer()));
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		int varpId = event.getVarpId();
		int varbitId = event.getVarbitId();
		int newValue = event.getValue();

		Integer old = varbitId != -1
			? lastVarbitValues.put(varbitId, newValue)
			: lastVarpValues.put(varpId, newValue);

		// Only real value changes are interesting (VarbitChanged also fires on same-value writes)
		if (old != null && old == newValue) {
			return;
		}

		VarChange change = new VarChange();
		change.tick = tick;
		change.varpId = varpId;
		change.varbitId = varbitId;
		change.oldValue = old;
		change.newValue = newValue;
		recentVarChanges.addLast(change);

		if (debugActive()) {
			Map<String, Object> f = new LinkedHashMap<>();
			f.put("varpId", varpId);
			f.put("varbitId", varbitId);
			f.put("old", old); // null = first sighting this session
			f.put("new", newValue);
			debugLog.write("varbit", tick, f);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		tick++;

		while (!recentVarChanges.isEmpty() && recentVarChanges.peekFirst().tick < tick - VAR_WINDOW_TICKS) {
			recentVarChanges.removeFirst();
		}

		if (standardShopOpen && shopName == null) {
			shopName = readShopTitle();
			if (shopName != null && config.debugMode()) {
				Map<String, Object> f = new LinkedHashMap<>();
				f.put("shopName", shopName);
				debugLog.write("shop_title", tick, f);
			}
		}

		if (pending != null && tick >= pending.resolveTick) {
			PendingTransaction p = pending;
			pending = null;
			resolve(p, "timer");
		}
	}

	/** Full reset — called from plugin shutDown. */
	public void reset() {
		tick = 0;
		standardShopOpen = false;
		shopName = null;
		shopContainerId = -1;
		openCandidateGroups.clear();
		lastVarpValues.clear();
		lastVarbitValues.clear();
		recentVarChanges.clear();
		containerStates.clear();
		pending = null;
		debugLog.close();
	}

	// ── Transaction resolution ─────────────────────────────────────────

	private void resolvePendingNow(String reason) {
		if (pending != null) {
			PendingTransaction p = pending;
			pending = null;
			resolve(p, reason);
		}
	}

	private void resolve(PendingTransaction p, String trigger) {
		Map<Integer, Integer> invNow = snapshotContainer(INV_CONTAINER);
		long coinsNow = countOf(invNow, COINS_ITEM_ID);
		long gpDelta = coinsNow - p.coinsSnapshot;

		Map<Integer, Integer> invDiff = diff(p.invSnapshot, invNow);
		invDiff.remove(COINS_ITEM_ID); // coins are reported separately as gpDelta

		List<Map<String, Object>> gained = new ArrayList<>();
		List<Map<String, Object>> lost = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : invDiff.entrySet()) {
			(e.getValue() > 0 ? gained : lost).add(itemDelta(e.getKey(), e.getValue()));
		}

		List<Map<String, Object>> currencyDeltas = collectCurrencyDeltas(p.clickTick);

		List<Map<String, Object>> shopStockDelta = null;
		if (p.shopSnapshot != null && shopContainerId != -1) {
			shopStockDelta = new ArrayList<>();
			for (Map.Entry<Integer, Integer> e : diff(p.shopSnapshot, snapshotContainer(shopContainerId)).entrySet()) {
				shopStockDelta.add(itemDelta(e.getKey(), e.getValue()));
			}
		}

		boolean sawAnything = gpDelta != 0 || !invDiff.isEmpty() || !currencyDeltas.isEmpty()
			|| (shopStockDelta != null && !shopStockDelta.isEmpty());

		// Classify the action: explicit option first, then fall back to item flow
		String action;
		if (SELL_OPTION.matcher(p.menuOption).matches()) {
			action = "sell";
		} else if (!gained.isEmpty() || gpDelta < 0) {
			action = "buy";
		} else if (!lost.isEmpty() && gpDelta > 0) {
			action = "sell";
		} else {
			action = "buy"; // Buy/Purchase/Confirm-style option with no clear flow
		}

		// Pick the primary item: prefer the clicked widget's item, else the largest inventory move
		int itemId = p.clickedItemId > 0 ? p.clickedItemId : -1;
		int quantity = 0;
		if (itemId > 0 && invDiff.containsKey(itemId)) {
			quantity = Math.abs(invDiff.get(itemId));
		} else if (itemId <= 0) {
			for (Map.Entry<Integer, Integer> e : invDiff.entrySet()) {
				if (Math.abs(e.getValue()) > quantity) {
					itemId = e.getKey();
					quantity = Math.abs(e.getValue());
				}
			}
		}
		String itemName = itemId > 0 ? itemNameOf(itemId) : null;

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("action", action);
		payload.put("itemId", itemId > 0 ? itemId : null);
		payload.put("itemName", itemName);
		payload.put("quantity", quantity);
		payload.put("gpDelta", gpDelta);
		payload.put("currencyDeltas", currencyDeltas);
		payload.put("shopName", p.shopName);
		payload.put("interfaceGroup", p.interfaceGroup);
		payload.put("menuOption", p.menuOption);
		payload.put("menuTarget", p.menuTarget);
		payload.put("menuAction", p.menuAction);
		payload.put("standardShop", p.standardShop);
		payload.put("itemsGained", gained);
		payload.put("itemsLost", lost);
		payload.put("shopStockDelta", shopStockDelta);
		payload.put("clickTick", p.clickTick);
		payload.put("resolvedBy", trigger);
		payload.put("widgetId", p.widgetId);
		payload.put("param0", p.param0);
		payload.put("param1", p.param1);

		// Always log locally — this is the experiment's primary output
		log.info("Shop transaction candidate: {} {} x{} shop='{}' gp={} vars={} (trigger={})",
			action, itemName != null ? itemName : "?", quantity, p.shopName, gpDelta, currencyDeltas.size(), trigger);
		if (config.debugMode()) {
			payload.put("noOp", !sawAnything);
			debugLog.write("transaction", tick, payload);
		}

		if (!sawAnything) {
			// e.g. Buy with a full inventory, or a Confirm that did nothing — don't emit
			return;
		}

		if (config.debugMode()) {
			announce(action, itemName, quantity, gpDelta, currencyDeltas, p.shopName);
			// Experiment gate: only deliver to the webhook in debug mode for now
			sendNotification(payload);
		}
	}

	private void announce(String action, String itemName, int quantity, long gpDelta,
						  List<Map<String, Object>> currencyDeltas, String shopName) {
		StringBuilder sb = new StringBuilder("Reval Shop: ")
			.append(action.toUpperCase())
			.append(' ');
		if (quantity > 0) {
			sb.append(quantity).append("x ");
		}
		sb.append(itemName != null ? itemName : "?");
		if (gpDelta != 0) {
			sb.append(" (").append(gpDelta > 0 ? "+" : "").append(gpDelta).append(" gp)");
		}
		if (!currencyDeltas.isEmpty()) {
			Map<String, Object> first = currencyDeltas.get(0);
			sb.append(" [var ").append(first.get("varbitId") != null && (int) first.get("varbitId") != -1
					? "b" + first.get("varbitId") : "p" + first.get("varpId"))
				.append(": ").append(first.get("delta")).append(currencyDeltas.size() > 1
					? ", +" + (currencyDeltas.size() - 1) + " more]" : "]");
		}
		sb.append(" @ ").append(shopName);

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", sb.toString(), "");
	}

	/**
	 * Aggregate the var changes that happened since the click into per-var net deltas.
	 * These are the candidate "currency" movements for reward shops.
	 */
	private List<Map<String, Object>> collectCurrencyDeltas(int clickTick) {
		// key -> [firstOld (nullable), lastNew]
		Map<String, Integer[]> perVar = new LinkedHashMap<>();
		Map<String, int[]> ids = new HashMap<>();

		for (VarChange c : recentVarChanges) {
			if (c.tick < clickTick) {
				continue;
			}
			String key = c.varbitId != -1 ? "b" + c.varbitId : "p" + c.varpId;
			Integer[] agg = perVar.get(key);
			if (agg == null) {
				perVar.put(key, new Integer[]{c.oldValue, c.newValue});
				ids.put(key, new int[]{c.varpId, c.varbitId});
			} else {
				agg[1] = c.newValue;
			}
		}

		List<Map<String, Object>> out = new ArrayList<>();
		for (Map.Entry<String, Integer[]> e : perVar.entrySet()) {
			Integer firstOld = e.getValue()[0];
			int lastNew = e.getValue()[1];
			Integer delta = firstOld != null ? lastNew - firstOld : null;
			if (delta != null && delta == 0) {
				continue; // bounced back to the starting value — not a spend
			}

			Map<String, Object> m = new LinkedHashMap<>();
			m.put("varpId", ids.get(e.getKey())[0]);
			m.put("varbitId", ids.get(e.getKey())[1]); // -1 when the change was a plain varp
			m.put("delta", delta);                     // null when the pre-click value was never seen
			m.put("oldValue", firstOld);
			m.put("newValue", lastNew);
			out.add(m);
			if (out.size() >= MAX_CURRENCY_DELTAS) {
				break;
			}
		}
		return out;
	}

	// ── Snapshots / diffs ──────────────────────────────────────────────

	private Map<Integer, Integer> snapshotContainer(int containerId) {
		Map<Integer, Integer> counts = new HashMap<>();
		ItemContainer container = client.getItemContainer(containerId);
		if (container == null) {
			return counts;
		}
		for (Item item : container.getItems()) {
			if (item.getId() > 0 && item.getQuantity() > 0) {
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return counts;
	}

	/** after - before, omitting zero deltas. */
	private static Map<Integer, Integer> diff(Map<Integer, Integer> before, Map<Integer, Integer> after) {
		Map<Integer, Integer> out = new HashMap<>();
		Set<Integer> keys = new HashSet<>(before.keySet());
		keys.addAll(after.keySet());
		for (int id : keys) {
			int d = after.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			if (d != 0) {
				out.put(id, d);
			}
		}
		return out;
	}

	private static long countOf(Map<Integer, Integer> snapshot, int itemId) {
		return snapshot.getOrDefault(itemId, 0);
	}

	private Map<String, Object> itemDelta(int itemId, int delta) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("itemId", itemId);
		m.put("itemName", itemNameOf(itemId));
		m.put("delta", delta);
		return m;
	}

	private String itemNameOf(int itemId) {
		try {
			ItemComposition comp = itemManager.getItemComposition(itemId);
			return comp != null ? comp.getName() : "Unknown";
		} catch (Exception e) {
			return "Unknown";
		}
	}

	// ── Widget helpers ─────────────────────────────────────────────────

	/**
	 * Standard shop title: SHOPMAIN (300) child 1 is the frame; the title text is one
	 * of its dynamic children. Scan for the first non-empty text rather than trusting
	 * a fixed dynamic index.
	 */
	private String readShopTitle() {
		Widget frame = client.getWidget(InterfaceID.Shopmain.FRAME);
		if (frame == null) {
			return null;
		}
		String text = firstText(frame.getDynamicChildren());
		if (text == null) {
			text = firstText(frame.getStaticChildren());
		}
		return text;
	}

	private static String firstText(Widget[] children) {
		if (children == null) {
			return null;
		}
		for (Widget child : children) {
			if (child == null) {
				continue;
			}
			String text = child.getText();
			if (text != null && !text.trim().isEmpty()) {
				return text.trim();
			}
		}
		return null;
	}

	/** Component id of the clicked widget (packed group &lt;&lt; 16 | child), or -1 for non-widget clicks. */
	private static int widgetIdOf(MenuOptionClicked event) {
		Widget w = event.getWidget();
		return w != null ? w.getId() : -1;
	}

	private int resolveInterfaceGroup(MenuOptionClicked event) {
		int widgetId = widgetIdOf(event);
		if (widgetId > 0) {
			return widgetId >>> 16;
		}
		if (standardShopOpen) {
			return InterfaceID.SHOPMAIN;
		}
		// Best effort: any open candidate group
		return openCandidateGroups.isEmpty() ? -1 : openCandidateGroups.iterator().next();
	}

	// ── Debug record builders ──────────────────────────────────────────

	private Map<String, Object> describeClick(MenuOptionClicked event) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("option", event.getMenuOption());
		f.put("target", event.getMenuTarget());
		f.put("menuAction", event.getMenuAction() != null ? event.getMenuAction().name() : null);
		f.put("id", event.getId());
		f.put("itemId", event.getItemId());
		f.put("isItemOp", event.isItemOp());
		f.put("itemOp", event.getItemOp());
		f.put("param0", event.getParam0());
		f.put("param1", event.getParam1());
		int widgetId = widgetIdOf(event);
		f.put("widgetId", widgetId);
		f.put("widgetGroup", widgetId > 0 ? widgetId >>> 16 : -1);
		f.put("widgetChild", widgetId > 0 ? widgetId & 0xFFFF : -1);
		Widget w = event.getWidget();
		if (w != null) {
			f.put("widgetIndex", w.getIndex());
			f.put("widgetItemId", w.getItemId());
			f.put("widgetItemQuantity", w.getItemQuantity());
			f.put("widgetName", w.getName());
			f.put("widgetText", w.getText());
		}
		f.put("openGroups", new ArrayList<>(openCandidateGroups));
		f.put("standardShopOpen", standardShopOpen);
		return f;
	}

	private Map<String, Object> describeContainerChange(int containerId, ItemContainer container) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("containerId", containerId);

		Map<Integer, int[]> now = new HashMap<>();
		int itemCount = 0;
		if (container != null) {
			Item[] items = container.getItems();
			for (int slot = 0; slot < items.length; slot++) {
				Item item = items[slot];
				if (item.getId() > 0 && item.getQuantity() > 0) {
					now.put(slot, new int[]{item.getId(), item.getQuantity()});
					itemCount++;
				}
			}
		}
		f.put("itemCount", itemCount);

		Map<Integer, int[]> prev = containerStates.put(containerId, now);
		List<Map<String, Object>> changedSlots = new ArrayList<>();
		Set<Integer> slots = new HashSet<>(now.keySet());
		if (prev != null) {
			slots.addAll(prev.keySet());
		}
		for (int slot : slots) {
			int[] b = prev != null ? prev.get(slot) : null;
			int[] a = now.get(slot);
			boolean same = (b == null && a == null)
				|| (b != null && a != null && b[0] == a[0] && b[1] == a[1]);
			if (same && prev != null) {
				continue;
			}
			if (prev == null && a == null) {
				continue;
			}
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("slot", slot);
			s.put("before", b);
			s.put("after", a);
			changedSlots.add(s);
			if (changedSlots.size() >= 64) {
				break;
			}
		}
		f.put("changedSlots", changedSlots);
		f.put("firstObservation", prev == null);
		return f;
	}

	// ── Data holders ───────────────────────────────────────────────────

	private static class VarChange {
		int tick;
		int varpId;
		int varbitId; // -1 for plain varp changes
		Integer oldValue; // null if never seen before
		int newValue;
	}

	private static class PendingTransaction {
		int clickTick;
		int resolveTick;
		String menuOption;
		String menuTarget;
		String menuAction;
		int clickedItemId;
		int widgetId;
		int param0;
		int param1;
		boolean standardShop;
		String shopName;
		int interfaceGroup;
		Map<Integer, Integer> invSnapshot;
		long coinsSnapshot;
		Map<Integer, Integer> shopSnapshot; // null when not a standard shop / container unknown
	}
}

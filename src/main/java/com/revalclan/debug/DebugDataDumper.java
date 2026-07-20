package com.revalclan.debug;

import com.google.gson.Gson;
import com.revalclan.PlayerDataCollector;
import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.combatachievements.CombatAchievementManager;
import com.revalclan.diaries.AchievementDiaryManager;
import com.revalclan.player.PlayerManager;
import com.revalclan.quests.QuestManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Item;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Test-mode helper that dumps RAW game data to local JSON files so we can
 * inspect exactly what the client exposes (sync payloads, cache structures,
 * varps, containers, widgets) without involving the backend.
 *
 * Files land in ~/.runelite/reval-debug/. All dump methods must be called on
 * the client thread; serialization and file IO are offloaded to the executor.
 */
@Slf4j
@Singleton
public class DebugDataDumper {
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	/** Highest param id probed when exploring structs. Params with value 0/-1 or empty strings are omitted. */
	private static final int MAX_PROBED_PARAM = 2000;

	private static final int CLOG_TOP_LEVEL_TABS_ENUM = 2102;
	private static final int CLOG_ITEM_REPLACEMENT_ENUM = 3721;
	private static final int CLOG_PARAM_SUBTABS = 683;
	private static final int CLOG_PARAM_PAGE_NAME = 689;
	private static final int CLOG_PARAM_PAGE_ITEMS = 690;

	@Inject private Client client;
	@Inject private Gson gson;
	@Inject private ScheduledExecutorService executor;
	@Inject private PlayerDataCollector playerDataCollector;
	@Inject private PlayerManager playerManager;
	@Inject private QuestManager questManager;
	@Inject private AchievementDiaryManager achievementDiaryManager;
	@Inject private CombatAchievementManager combatAchievementManager;
	@Inject private CollectionLogManager collectionLogManager;

	public File getDebugDir() {
		return new File(RuneLite.RUNELITE_DIR, "reval-debug");
	}

	/**
	 * Serialize and write a dump off the client thread. Data must already be
	 * plain maps/lists/primitives (fully extracted from client objects).
	 */
	public void writeDump(String name, Object data) {
		String fileName = name + "-" + LocalDateTime.now().format(FILE_STAMP) + ".json";
		executor.execute(() -> {
			try {
				File dir = getDebugDir();
				if (!dir.exists() && !dir.mkdirs()) {
					log.warn("Could not create debug dir {}", dir);
					return;
				}
				File out = new File(dir, fileName);
				String json = gson.newBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(data);
				Files.write(out.toPath(), json.getBytes(StandardCharsets.UTF_8));
				log.info("Reval debug dump written: {}", out.getAbsolutePath());
			} catch (Exception e) {
				log.warn("Failed to write debug dump {}: {}", fileName, e.getMessage());
			}
		});
	}

	public void chat(String message) {
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Reval Debug: " + message, "");
	}

	// ─── Composite dumps ───────────────────────────────────────────────

	/** Everything at once, one file per domain. Client thread only. */
	public void dumpAll() {
		dumpSyncPayload();
		dumpPlayerRaw();
		dumpQuestsRaw();
		dumpDiariesRaw();
		dumpCombatAchievementsRaw();
		dumpClogMapped();
		dumpKcs();
		dumpVarps();
		dumpVarcs();
		dumpItemContainer(93, "inventory");
		dumpItemContainer(94, "equipment");
		chat("Full dump written to " + getDebugDir().getAbsolutePath());
	}

	/** The exact payload the SYNC button would send to the backend. */
	public void dumpSyncPayload() {
		writeDump("sync-payload", playerDataCollector.collectAllData());
	}

	// ─── Player / skills ───────────────────────────────────────────────

	/** Player state incl. things we don't currently sync (position, appearance, clan, energy...). */
	public void dumpPlayerRaw() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("managedSync", playerManager.sync());

		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("world", client.getWorld());
		extra.put("worldTypes", String.valueOf(client.getWorldType()));
		extra.put("gameState", String.valueOf(client.getGameState()));
		extra.put("tickCount", client.getTickCount());
		extra.put("accountHash", client.getAccountHash());
		extra.put("weight", client.getWeight());
		extra.put("runEnergy", client.getEnergy());
		extra.put("specialAttackPercent", safeVarp(300));
		extra.put("membershipDaysVarp1780", safeVarp(1780));
		extra.put("questPointsVarp101", safeVarp(101));
		extra.put("clogObtainedCountVarp2943", safeVarp(2943));

		Player local = client.getLocalPlayer();
		if (local != null) {
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("name", local.getName());
			p.put("combatLevel", local.getCombatLevel());
			WorldPoint wp = local.getWorldLocation();
			if (wp != null) {
				p.put("location", Map.of("x", wp.getX(), "y", wp.getY(), "plane", wp.getPlane()));
			}
			p.put("animation", local.getAnimation());
			p.put("poseAnimation", local.getPoseAnimation());
			try {
				if (local.getPlayerComposition() != null) {
					p.put("equipmentIds", local.getPlayerComposition().getEquipmentIds());
				}
			} catch (Exception ignored) {}
			extra.put("localPlayer", p);
		}

		try {
			if (client.getClanChannel() != null) {
				extra.put("clanChannel", Map.of(
					"name", String.valueOf(client.getClanChannel().getName()),
					"memberCount", client.getClanChannel().getMembers().size()
				));
			}
			if (client.getClanSettings() != null) {
				extra.put("clanSettingsName", String.valueOf(client.getClanSettings().getName()));
			}
		} catch (Exception ignored) {}

		Map<String, Object> skills = new LinkedHashMap<>();
		for (Skill skill : Skill.values()) {
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("realLevel", client.getRealSkillLevel(skill));
			s.put("boostedLevel", client.getBoostedSkillLevel(skill));
			s.put("experience", client.getSkillExperience(skill));
			skills.put(skill.getName().toLowerCase(), s);
		}
		extra.put("skills", skills);

		data.put("raw", extra);
		writeDump("player", data);
	}

	// ─── Quests / diaries / combat achievements ────────────────────────

	public void dumpQuestsRaw() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("managedSync", questManager.sync());

		List<Map<String, Object>> quests = new ArrayList<>();
		for (Quest quest : Quest.values()) {
			Map<String, Object> q = new LinkedHashMap<>();
			q.put("id", quest.getId());
			q.put("name", quest.getName());
			try {
				q.put("state", quest.getState(client).name());
			} catch (Exception e) {
				q.put("state", "ERROR: " + e.getMessage());
			}
			quests.add(q);
		}
		data.put("rawQuests", quests);
		data.put("questPointsVarp101", safeVarp(101));
		writeDump("quests", data);
	}

	public void dumpDiariesRaw() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("managedSync", achievementDiaryManager.sync());

		Map<String, Object> rawVarbits = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Integer>> region : achievementDiaryManager.getDiaryVarbits().entrySet()) {
			Map<String, Object> tiers = new LinkedHashMap<>();
			for (Map.Entry<String, Integer> tier : region.getValue().entrySet()) {
				tiers.put(tier.getKey(), Map.of(
					"varbitId", tier.getValue(),
					"value", safeVarbit(tier.getValue())
				));
			}
			rawVarbits.put(region.getKey(), tiers);
		}
		data.put("rawVarbits", rawVarbits);
		writeDump("diaries", data);
	}

	public void dumpCombatAchievementsRaw() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("managedSync", combatAchievementManager.sync());

		// Raw completion bitfields so the varp → task-bit mapping is inspectable
		int[] completionVarps = {3116, 3117, 3118, 3119, 3120, 3121, 3122, 3123, 3124, 3125,
			3126, 3127, 3128, 3387, 3718, 3773, 3774, 4204, 4496, 4721};
		Map<String, Object> rawVarps = new LinkedHashMap<>();
		for (int varp : completionVarps) {
			int value = safeVarp(varp);
			rawVarps.put(String.valueOf(varp), Map.of(
				"value", value,
				"bits", Integer.toBinaryString(value)
			));
		}
		data.put("completionVarps", rawVarps);

		// One fully-probed struct per tier to reveal params we don't map yet
		Map<String, Object> probedStructs = new LinkedHashMap<>();
		int[] tierEnums = {3981, 3982, 3983, 3984, 3985, 3986};
		for (int enumId : tierEnums) {
			try {
				EnumComposition tierEnum = client.getEnum(enumId);
				if (tierEnum == null) continue;
				int[] structIds = tierEnum.getIntVals();
				if (structIds.length > 0) {
					probedStructs.put("tierEnum" + enumId + "-firstStruct" + structIds[0],
						probeStruct(structIds[0]));
				}
			} catch (Exception ignored) {}
		}
		data.put("probedSampleStructs", probedStructs);
		writeDump("combat-achievements", data);
	}

	// ─── Collection log ────────────────────────────────────────────────

	/** The mapped view our sync sends (structure + obtained + KCs). */
	public void dumpClogMapped() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("managedSync", collectionLogManager.sync());
		data.put("obtainedItemsTracked", collectionLogManager.getObtainedItems().size());
		data.put("totalItemsInCache", collectionLogManager.getAllCollectionLogItems().size());
		data.put("clogObtainedCountVarp2943", safeVarp(2943));
		writeDump("clog-mapped", data);
	}

	/**
	 * Raw cache walk of the collection log enums/structs with param probing,
	 * independent of our mapping — shows everything the cache actually holds.
	 */
	public void dumpClogRawStructure() {
		Map<String, Object> data = new LinkedHashMap<>();
		try {
			EnumComposition replacements = client.getEnum(CLOG_ITEM_REPLACEMENT_ENUM);
			EnumComposition topLevel = client.getEnum(CLOG_TOP_LEVEL_TABS_ENUM);
			data.put("topLevelTabsEnum", CLOG_TOP_LEVEL_TABS_ENUM);

			List<Map<String, Object>> tabs = new ArrayList<>();
			for (int tabStructId : topLevel.getIntVals()) {
				Map<String, Object> tab = new LinkedHashMap<>();
				tab.put("structId", tabStructId);
				tab.put("probedParams", probeStruct(tabStructId));

				StructComposition tabStruct = client.getStructComposition(tabStructId);
				int subtabsEnumId = tabStruct.getIntValue(CLOG_PARAM_SUBTABS);
				List<Map<String, Object>> pages = new ArrayList<>();
				for (int pageStructId : client.getEnum(subtabsEnumId).getIntVals()) {
					StructComposition pageStruct = client.getStructComposition(pageStructId);
					Map<String, Object> page = new LinkedHashMap<>();
					page.put("structId", pageStructId);
					page.put("name", pageStruct.getStringValue(CLOG_PARAM_PAGE_NAME));
					page.put("probedParams", probeStruct(pageStructId));

					List<Map<String, Object>> items = new ArrayList<>();
					for (int itemId : client.getEnum(pageStruct.getIntValue(CLOG_PARAM_PAGE_ITEMS)).getIntVals()) {
						int replacementId = replacements.getIntValue(itemId);
						int effectiveId = replacementId == -1 ? itemId : replacementId;
						Map<String, Object> item = new LinkedHashMap<>();
						item.put("id", itemId);
						if (replacementId != -1) {
							item.put("replacedBy", replacementId);
						}
						try {
							item.put("name", client.getItemDefinition(effectiveId).getName());
						} catch (Exception e) {
							item.put("name", "?");
						}
						items.add(item);
					}
					page.put("items", items);
					pages.add(page);
				}
				tab.put("pages", pages);
				tabs.add(tab);
			}
			data.put("tabs", tabs);
		} catch (Exception e) {
			data.put("error", String.valueOf(e));
		}
		writeDump("clog-raw-cache", data);
	}

	/** Every KC source we track, with its raw varp/varbit id and current value. */
	public void dumpKcs() {
		writeDump("clog-kcs", collectionLogManager.debugKCSnapshot());
	}

	// ─── Low-level probes ──────────────────────────────────────────────

	/** Full client varp array — the raw substrate most game state derives from. */
	public void dumpVarps() {
		Map<String, Object> data = new LinkedHashMap<>();
		int[] varps = client.getVarps();
		data.put("totalVarps", varps.length);
		Map<String, Integer> nonZero = new LinkedHashMap<>();
		for (int i = 0; i < varps.length; i++) {
			if (varps[i] != 0) {
				nonZero.put(String.valueOf(i), varps[i]);
			}
		}
		data.put("nonZeroCount", nonZero.size());
		data.put("nonZeroVarps", nonZero);
		writeDump("varps", data);
	}

	/** Client-side varcs (chat input state, UI state, etc.). */
	public void dumpVarcs() {
		Map<String, Object> data = new LinkedHashMap<>();
		try {
			Map<Integer, Object> varcs = client.getVarcMap();
			Map<String, Object> mapped = new LinkedHashMap<>();
			for (Map.Entry<Integer, Object> e : varcs.entrySet()) {
				Object v = e.getValue();
				mapped.put(String.valueOf(e.getKey()),
					(v instanceof Number || v instanceof String || v instanceof Boolean) ? v : String.valueOf(v));
			}
			data.put("varcs", mapped);
		} catch (Exception e) {
			data.put("error", String.valueOf(e));
		}
		writeDump("varcs", data);
	}

	/** Raw item container contents. 93 = inventory, 94 = equipment, 95 = bank (bank must have been opened). */
	public void dumpItemContainer(int containerId, String label) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("containerId", containerId);
		ItemContainer container = client.getItemContainer(containerId);
		if (container == null) {
			data.put("loaded", false);
		} else {
			data.put("loaded", true);
			List<Map<String, Object>> items = new ArrayList<>();
			Item[] raw = container.getItems();
			for (int slot = 0; slot < raw.length; slot++) {
				Item item = raw[slot];
				if (item == null || item.getId() <= 0) continue;
				Map<String, Object> i = new LinkedHashMap<>();
				i.put("slot", slot);
				i.put("id", item.getId());
				i.put("quantity", item.getQuantity());
				try {
					i.put("name", client.getItemDefinition(item.getId()).getName());
				} catch (Exception ignored) {}
				items.add(i);
			}
			data.put("itemCount", items.size());
			data.put("items", items);
		}
		writeDump("container-" + label, data);
	}

	/** Explore an arbitrary cache enum: keys and values. */
	public void dumpEnumById(int enumId) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("enumId", enumId);
		try {
			EnumComposition e = client.getEnum(enumId);
			data.put("size", e.size());
			data.put("keys", e.getKeys());
			try { data.put("intVals", e.getIntVals()); } catch (Exception ignored) {}
			try { data.put("stringVals", e.getStringVals()); } catch (Exception ignored) {}
		} catch (Exception e) {
			data.put("error", String.valueOf(e));
		}
		writeDump("enum-" + enumId, data);
	}

	/** Explore an arbitrary cache struct by probing all param ids. */
	public void dumpStructById(int structId) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("structId", structId);
		data.put("probedParams", probeStruct(structId));
		writeDump("struct-" + structId, data);
	}

	/** Dump the widget tree of an interface group (e.g. 621 = collection log). */
	public void dumpWidgets(int groupId) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("groupId", groupId);
		List<Map<String, Object>> roots = new ArrayList<>();
		int nodeBudget = 3000;
		for (int child = 0; child < 512 && nodeBudget > 0; child++) {
			Widget w = client.getWidget(groupId, child);
			if (w == null) continue;
			roots.add(describeWidget(w, 2));
			nodeBudget -= 1 + (w.getChildren() != null ? w.getChildren().length : 0);
		}
		data.put("roots", roots);
		writeDump("widgets-" + groupId, data);
	}

	private Map<String, Object> describeWidget(Widget w, int depth) {
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("id", w.getId());
		node.put("type", w.getType());
		if (w.getText() != null && !w.getText().isEmpty()) node.put("text", w.getText());
		if (w.getItemId() > 0) {
			node.put("itemId", w.getItemId());
			node.put("itemQuantity", w.getItemQuantity());
		}
		if (w.getSpriteId() > 0) node.put("spriteId", w.getSpriteId());
		if (w.getName() != null && !w.getName().isEmpty()) node.put("name", w.getName());
		node.put("hidden", w.isHidden());
		if (w.getActions() != null) node.put("actions", w.getActions());

		if (depth > 0 && w.getChildren() != null && w.getChildren().length > 0) {
			List<Map<String, Object>> children = new ArrayList<>();
			int cap = Math.min(w.getChildren().length, 300);
			for (int i = 0; i < cap; i++) {
				Widget c = w.getChildren()[i];
				if (c == null) continue;
				children.add(describeWidget(c, depth - 1));
			}
			node.put("children", children);
		}
		return node;
	}

	/**
	 * Probe all param ids on a struct. Missing params return 0/-1/empty
	 * depending on type, so those values are omitted — a real param whose
	 * value happens to be 0 will not show up here.
	 */
	private Map<String, Object> probeStruct(int structId) {
		Map<String, Object> result = new LinkedHashMap<>();
		StructComposition struct;
		try {
			struct = client.getStructComposition(structId);
		} catch (Exception e) {
			result.put("error", String.valueOf(e));
			return result;
		}
		if (struct == null) {
			result.put("error", "null struct");
			return result;
		}

		Map<String, Integer> ints = new LinkedHashMap<>();
		Map<String, String> strings = new LinkedHashMap<>();
		for (int param = 0; param <= MAX_PROBED_PARAM; param++) {
			try {
				int v = struct.getIntValue(param);
				if (v != 0 && v != -1) {
					ints.put(String.valueOf(param), v);
				}
			} catch (Exception ignored) {}
			try {
				String s = struct.getStringValue(param);
				if (s != null && !s.isEmpty() && !"null".equals(s)) {
					strings.put(String.valueOf(param), s);
				}
			} catch (Exception ignored) {}
		}
		result.put("intParams", ints);
		result.put("stringParams", strings);
		result.put("note", "int params with value 0/-1 omitted (indistinguishable from absent)");
		return result;
	}

	private int safeVarp(int id) {
		try {
			return client.getVarpValue(id);
		} catch (Exception e) {
			return Integer.MIN_VALUE;
		}
	}

	private int safeVarbit(int id) {
		try {
			return client.getVarbitValue(id);
		} catch (Exception e) {
			return Integer.MIN_VALUE;
		}
	}
}

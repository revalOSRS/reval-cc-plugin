/*
 * Portions of this file are derived from or inspired by the TempleOSRS plugin
 * Copyright (c) 2022, SMaloney2017
 * Licensed under the BSD 2-Clause License
 * See LICENSES/templeOSRS-LICENSE.txt for full license text
 */
package com.revalclan.collectionlog;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Manages collection log data by reading directly from the game cache
 * Based on Temple OSRS implementation
 */
@Slf4j
@Singleton
public class CollectionLogManager {
	@Inject private Client client;

	/**
	 * Maps in-game category struct IDs to the list of items they contain
	 */
	@Getter private final Map<Integer, Set<Integer>> categoryItemMap = new HashMap<>();

	/**
	 * Maps slugified category names to their in-game struct ID
	 */
	@Getter private final Map<String, Integer> categoryStructIdMap = new HashMap<>();

	/**
	 * Maps top level tabs to their containing categories
	 */
	@Getter private final Map<Integer, Set<String>> categoryTabSlugs = new LinkedHashMap<>();

	/**
	 * List of all items in the collection log
	 */
	@Getter private final Set<Integer> allCollectionLogItems = new HashSet<>();

	/**
	 * Set of items the player has obtained (populated when collection log is opened)
	 */
	@Getter private final Set<ObtainedCollectionItem> obtainedItems = new HashSet<>();

	/**
	 * Maps category slugs to their KC varbit/varp IDs
	 */
	private final Map<String, KCSource> categoryKCMap = new HashMap<>();
	
	/**
	 * Maps category slugs to additional KC sources (for multi-KC tracking)
	 */
	private final Map<String, Map<String, KCSource>> additionalKCMap = new HashMap<>();
	
	/**
	 * Maps subcategories to their KC calculation rules (for derived KCs like original boss = combined - awakened)
	 * Format: subcategorySlug -> (resultKCName -> [combinedKCName, subtractKCName])
	 */
	private final Map<String, Map<String, String[]>> derivedKCMap = new HashMap<>();
	
	/**
	 * Maps subcategories to their summed KC rules (for derived KCs that are sums of multiple sources)
	 * Format: subcategorySlug -> (resultKCName -> [source1Name, source2Name, ...])
	 */
	private final Map<String, Map<String, String[]>> summedKCMap = new HashMap<>();

	/**
	 * Helper class to store KC source information
	 */
	private static class KCSource {
		final boolean isVarbit;
		final int id;

		KCSource(boolean isVarbit, int id) {
			this.isVarbit = isVarbit;
			this.id = id;
		}
	}
	
	/**
	 * Fluent builder for configuring KC tracking for a subcategory
	 */
	private class KCBuilder {
		private final String subcategory;
		
		KCBuilder(String subcategory) {
			this.subcategory = subcategory;
		}
		
		KCBuilder primaryKC(boolean isVarbit, int id) {
			categoryKCMap.put(subcategory, new KCSource(isVarbit, id));
			return this;
		}
		
		KCBuilder primaryKC(int varPlayerId) {
			return primaryKC(false, varPlayerId);
		}
		
		KCBuilder additionalKC(String kcName, boolean isVarbit, int id) {
			additionalKCMap.computeIfAbsent(subcategory, k -> new HashMap<>())
				.put(kcName, new KCSource(isVarbit, id));
			return this;
		}

		KCBuilder additionalKC(String kcName, int varPlayerId) {
			return additionalKC(kcName, false, varPlayerId);
		}
		

		KCBuilder derivedKC(String resultKCName, String combinedKCName, String subtractKCName) {
			derivedKCMap.computeIfAbsent(subcategory, k -> new HashMap<>())
				.put(resultKCName, new String[]{combinedKCName, subtractKCName});
			return this;
		}
		
		KCBuilder summedKC(String resultKCName, String... sourceKCNames) {
			summedKCMap.computeIfAbsent(subcategory, k -> new HashMap<>())
				.put(resultKCName, sourceKCNames);
			return this;
		}
		
		/**
		 * Convenience method for boss pairs (original + awakened)
		 * Sets up: primary KC, combined KC, awakened KC, and derived original KC
		 */
		KCBuilder bossPair(int combinedVarPlayerId, String originalName, int awakenedVarPlayerId, String awakenedName) {
			return primaryKC(combinedVarPlayerId)
				.additionalKC(originalName + "_combined_kc", combinedVarPlayerId)
				.additionalKC(awakenedName + "_kc", awakenedVarPlayerId)
				.derivedKC(originalName + "_kc", originalName + "_combined_kc", awakenedName + "_kc");
		}
	}
	
	/**
	 * Start building KC configuration for a subcategory
	 */
	private KCBuilder kc(String subcategory) {
		return new KCBuilder(subcategory);
	}

	/**
	 * Initialize the KC mapping for collection log categories
	 */
	private void initializeKCMap() {
		// ===== BOSSES =====
		kc("abyssal_sire").primaryKC(1526);
		kc("alchemical_hydra").primaryKC(2074);
		kc("amoxliatl").primaryKC(4403);
		kc("araxxor").primaryKC(4260);
		kc("barrows_chests").primaryKC(1502);
		kc("bryophyta").primaryKC(1733);
		
		kc("callisto_and_artio").bossPair(1510, "callisto", 3761, "artio");
		
		kc("venenatis_and_spindel")
			.primaryKC(1511)
			.additionalKC("venenatis_kc", 1511)
			.additionalKC("spindel_kc", 3762);
		
		kc("vetion_and_calvarion").bossPair(1512, "vetion", 3763, "calvarion");
		
		kc("cerberus").primaryKC(1525);
		kc("chaos_elemental").primaryKC(1513);
		kc("chaos_fanatic").primaryKC(1519);
		kc("commander_zilyana").primaryKC(1505);
		kc("corporeal_beast").primaryKC(1517);
		kc("crazy_archaeologist").primaryKC(1521);
		
		kc("dagannoth_kings")
			.primaryKC(1507)
			.additionalKC("dagannoth_prime_kc", 1507)
			.additionalKC("dagannoth_rex_kc", 1508)
			.additionalKC("dagannoth_supreme_kc", 1509);
		
		kc("deranged_archaeologist").primaryKC(1661);
		
		kc("doom_of_mokhaiotl")
			.primaryKC(4182)
			.additionalKC("level_1_completions", 4808)
			.additionalKC("level_2_completions", 4809)
			.additionalKC("level_3_completions", 4810)
			.additionalKC("level_4_completions", 4811)
			.additionalKC("level_5_completions", 4812)
			.additionalKC("level_6_completions", 4813)
			.additionalKC("level_7_completions", 4814)
			.additionalKC("level_8_completions", 4815)
			.additionalKC("level_8_plus_completions", 4816)
			.summedKC("total_completions", 
				"level_1_completions", "level_2_completions", "level_3_completions", "level_4_completions",
				"level_5_completions", "level_6_completions", "level_7_completions", "level_8_completions", "level_8_plus_completions")
			.additionalKC("deepest_delves", 4806);
		
		kc("duke_sucellus").primaryKC(3967);
		kc("the_fight_caves").primaryKC(1522);
		
		kc("fortis_colosseum")
			.primaryKC(4131)
			.additionalKC("colosseum_glory", 4132);
		
		kc("the_gauntlet")
			.primaryKC(2353)
			.additionalKC("corrupted_gauntlet_kc", 2354);
		
		kc("general_graardor").primaryKC(1504);
		kc("giant_mole").primaryKC(1515);
		kc("grotesque_guardians").primaryKC(1669);
		kc("hespori").primaryKC(2075);
		kc("the_hueycoatl").primaryKC(4404);
		kc("the_inferno").primaryKC(1585);
		kc("kalphite_queen").primaryKC(1516);
		kc("king_black_dragon").primaryKC(1514);
		kc("kraken").primaryKC(1523);
		kc("kreearra").primaryKC(1503);
		kc("kril_tsutsaroth").primaryKC(1506);
		kc("the_leviathan").primaryKC(3968);
		
		kc("moons_of_peril")
			.primaryKC(4186)
			.additionalKC("eclipse_moon_kc", 4148)
			.additionalKC("blue_moon_kc", 4149)
			.additionalKC("blood_moon_kc", 4150);
		
		kc("mimic").primaryKC(2221);
		kc("nex").primaryKC(3269);
		kc("the_nightmare").primaryKC(2664);
		kc("phosanis_nightmare").primaryKC(2671);
		kc("obor").primaryKC(1529);
		kc("phantom_muspah").primaryKC(3752);
		kc("royal_titans").primaryKC(4648);
		kc("sarachnis").primaryKC(2233);
		kc("scorpia").primaryKC(1520);
		kc("scurrius").primaryKC(4079);
		kc("skotizo").primaryKC(1527);
		
		kc("tempoross")
			.primaryKC(2934)
			.additionalKC("tempoross_rewards", true, 11936);
		
		kc("thermonuclear_smoke_devil").primaryKC(1524);
		kc("vardorvis").primaryKC(3970);
		kc("vorkath").primaryKC(1691);
		kc("the_whisperer").primaryKC(3969);
		
		kc("wintertodt")
			.primaryKC(1528)
			.additionalKC("wintertodt_rewards", 1941);
		
		kc("yama").primaryKC(4701);
		kc("zalcano").primaryKC(2352);
		kc("zulrah").primaryKC(1518);

		kc("chambers_of_xeric")
			.primaryKC(1532)
			.additionalKC("challenge_mode", 1735);
		
		kc("theatre_of_blood")
			.primaryKC(1748)
			.additionalKC("hard_mode", 3057);
		
		kc("tombs_of_amascut")
			.primaryKC(3646)
			.additionalKC("entry_mode", 3645)
			.additionalKC("expert_mode", 3647);

		// ===== CLUES =====
		kc("beginner_treasure_trails").primaryKC(true, 11996);
		kc("easy_treasure_trails").primaryKC(true, 11997);
		kc("medium_treasure_trails").primaryKC(true, 11998);
		kc("hard_treasure_trails").primaryKC(true, 11999);
		kc("elite_treasure_trails").primaryKC(true, 12000);
		kc("master_treasure_trails").primaryKC(true, 12001);
		kc("hard_treasure_trails_rare").primaryKC(true, 11999);
		kc("elite_treasure_trails_rare").primaryKC(true, 12000);
		kc("master_treasure_trails_rare").primaryKC(true, 12001);

		// ===== MINIGAMES =====
		kc("barbarian_assault").primaryKC(1605);

		kc("guardians_of_the_rift")
			.primaryKC(3397)
			.additionalKC("rifts_closed", 3397);
		
		kc("hallowed_sepulchre").primaryKC(2936);
		
		kc("last_man_standing")
			.primaryKC(2396)
			.additionalKC("lms_wins", 2397)
			.additionalKC("lms_kills", 2398);
		
		kc("mastering_mixology").primaryKC(4480);
		
		kc("soul_wars")
			.primaryKC(2871)
			.additionalKC("total_kills", 2872)
			.additionalKC("total_deaths", 2873)
			.additionalKC("total_games", 2874)
			.additionalKC("total_wins", 2875)
			.additionalKC("zeal_tokens", 2876);


		// ===== OTHER =====
		kc("gloughs_experiments")
			.primaryKC(1685)
			.additionalKC("demonic_gorillas_kc", 1685)
			.additionalKC("tortured_gorillas_kc", 4321);

		kc("tormented_demons").primaryKC(4240);
	}

	/**
	 * Parse the game cache to extract all collection log structure
	 */
	public void parseCacheForCollectionLog() {
		if (client.getIndexConfig() == null) {
			return;
		}

		initializeKCMap();
		categoryItemMap.clear();
		categoryStructIdMap.clear();
		categoryTabSlugs.clear();
		allCollectionLogItems.clear();

		try {
			Pattern specialCharPattern = Pattern.compile("['()]");
			EnumComposition replacements = client.getEnum(3721);
			int[] topLevelTabStructIds = client.getEnum(2102).getIntVals();

			for (int topLevelTabStructIndex : topLevelTabStructIds) {
				StructComposition topLevelTabStruct = client.getStructComposition(topLevelTabStructIndex);
				int[] subtabStructIndices = client.getEnum(topLevelTabStruct.getIntValue(683)).getIntVals();
				Set<String> categorySlugSet = new LinkedHashSet<>();

				for (int subtabStructIndex : subtabStructIndices) {
					StructComposition subtabStruct = client.getStructComposition(subtabStructIndex);
					int[] clogItems = client.getEnum(subtabStruct.getIntValue(690)).getIntVals();
					String categoryName = subtabStruct.getStringValue(689);

					String slug = specialCharPattern.matcher(categoryName.toLowerCase().replaceAll(" ", "_")).replaceAll("");
					Set<Integer> itemSet = new LinkedHashSet<>();

					for (int itemId : clogItems) {
						int replacementId = replacements.getIntValue(itemId);
						itemSet.add(replacementId == -1 ? itemId : replacementId);
					}

					allCollectionLogItems.addAll(itemSet);
					categoryItemMap.put(subtabStructIndex, itemSet);
					categoryStructIdMap.put(slug, subtabStructIndex);
					categorySlugSet.add(slug);
				}

				categoryTabSlugs.put(topLevelTabStructIndex, categorySlugSet);
			}
		} catch (Exception e) {
			log.error("Error parsing collection log cache", e);
		}
	}

	/**
	 * Called when collection log opens - tracks which items the player has obtained
	 */
	public void onCollectionLogItemObtained(int itemId, int itemCount, String itemName) {
		obtainedItems.add(new ObtainedCollectionItem(itemId, itemName, itemCount));
	}

	/**
	 * Sync and get collection log data grouped by category (Category > Subcategory > Items)
	 */
	public Map<String, Object> sync() {
		Map<String, Object> data = new HashMap<>();
		data.put("totalItems", allCollectionLogItems.size());
		
		// Get obtained items count
		if (!obtainedItems.isEmpty()) {
			data.put("obtainedItems", obtainedItems.size());
			data.put("dataSource", "collection_log_opened");
		} else {
			try {
				data.put("obtainedItems", client.getVarpValue(2943));
				data.put("dataSource", "varbit_2943");
			} catch (Exception e) {
				data.put("obtainedItems", 0);
				data.put("dataSource", "unavailable");
			}
		}

		// Map obtained items for quick lookup
		Map<Integer, ObtainedCollectionItem> obtainedItemsMap = new HashMap<>();
		for (ObtainedCollectionItem item : obtainedItems) {
			obtainedItemsMap.put(item.getId(), item);
		}

		// Build hierarchical structure: Category > Subcategory > Items
		Map<String, Map<String, Map<String, Object>>> categoriesData = new LinkedHashMap<>();

		for (Map.Entry<Integer, Set<String>> tabEntry : categoryTabSlugs.entrySet()) {
			String categoryName = CollectionLogCategoryGroup.getNameFromStructId(tabEntry.getKey());
			Map<String, Map<String, Object>> subcategoriesData = new LinkedHashMap<>();

			for (String subcategorySlug : tabEntry.getValue()) {
				Set<Integer> categoryItems = categoryItemMap.get(categoryStructIdMap.get(subcategorySlug));
				if (categoryItems == null) continue;

				Map<String, Object> subcategoryData = buildSubcategoryData(subcategorySlug, categoryItems, obtainedItemsMap);
				subcategoriesData.put(subcategorySlug, subcategoryData);
			}

			categoriesData.put(categoryName, subcategoriesData);
		}

		data.put("categories", categoriesData);
		return data;
	}

	/**
	 * Build subcategory data including items and KC tracking
	 */
	private Map<String, Object> buildSubcategoryData(String subcategorySlug, Set<Integer> categoryItems, 
			Map<Integer, ObtainedCollectionItem> obtainedItemsMap) {
		Map<String, Object> subcategoryData = new HashMap<>();
		List<Map<String, Object>> itemsList = new ArrayList<>();
		int obtainedCount = 0;

		// Build items list
		for (Integer itemId : categoryItems) {
			Map<String, Object> itemData = new HashMap<>();
			itemData.put("id", itemId);
			
			ObtainedCollectionItem obtainedItem = obtainedItemsMap.get(itemId);
			if (obtainedItem != null) {
				itemData.put("name", obtainedItem.getName());
				itemData.put("quantity", obtainedItem.getCount());
				itemData.put("obtained", true);
				obtainedCount++;
			} else {
				try {
					itemData.put("name", client.getItemDefinition(itemId).getName());
				} catch (Exception e) {
					itemData.put("name", "Unknown");
				}
				itemData.put("quantity", 0);
				itemData.put("obtained", false);
			}
			
			itemsList.add(itemData);
		}

		subcategoryData.put("total", categoryItems.size());
		subcategoryData.put("obtained", obtainedCount);
		subcategoryData.put("items", itemsList);

		// Add primary KC
		addPrimaryKC(subcategorySlug, subcategoryData);
		
		// Add additional KCs (including derived and summed)
		addAdditionalKCs(subcategorySlug, subcategoryData);

		return subcategoryData;
	}

	/**
	 * Add primary KC to subcategory data
	 */
	private void addPrimaryKC(String subcategorySlug, Map<String, Object> subcategoryData) {
		KCSource kcSource = categoryKCMap.get(subcategorySlug);
		if (kcSource != null) {
			try {
				int kc = kcSource.isVarbit ? client.getVarbitValue(kcSource.id) : client.getVarpValue(kcSource.id);
				subcategoryData.put("kc", kc);
			} catch (Exception e) {
				subcategoryData.put("kc", 0);
			}
		} else {
			subcategoryData.put("kc", 0);
		}
	}

	/**
	 * Add additional KCs including derived and summed calculations
	 */
	private void addAdditionalKCs(String subcategorySlug, Map<String, Object> subcategoryData) {
		Map<String, KCSource> additionalKCs = additionalKCMap.get(subcategorySlug);
		if (additionalKCs == null || additionalKCs.isEmpty()) return;

		Map<String, Integer> kcValues = new HashMap<>();
		
		// Fetch all KC values
		for (Map.Entry<String, KCSource> entry : additionalKCs.entrySet()) {
			try {
				KCSource source = entry.getValue();
				int value = source.isVarbit ? client.getVarbitValue(source.id) : client.getVarpValue(source.id);
				kcValues.put(entry.getKey(), value);
			} catch (Exception e) {
				kcValues.put(entry.getKey(), 0);
			}
		}
		
		// Process derived KCs (subtraction: original = combined - awakened)
		Map<String, String[]> derivedKCs = derivedKCMap.get(subcategorySlug);
		if (derivedKCs != null) {
			for (Map.Entry<String, String[]> entry : derivedKCs.entrySet()) {
				String[] calc = entry.getValue();
				int result = kcValues.getOrDefault(calc[0], 0) - kcValues.getOrDefault(calc[1], 0);
				kcValues.put(entry.getKey(), result);
			}
		}
		
		// Process summed KCs (addition: total = sum of all sources)
		Map<String, String[]> summedKCs = summedKCMap.get(subcategorySlug);
		if (summedKCs != null) {
			for (Map.Entry<String, String[]> entry : summedKCs.entrySet()) {
				int sum = 0;
				for (String sourceKCName : entry.getValue()) {
					sum += kcValues.getOrDefault(sourceKCName, 0);
				}
				kcValues.put(entry.getKey(), sum);
			}
		}
		
		// Add all KC values to subcategory data (exclude intermediate _combined_kc values)
		for (Map.Entry<String, Integer> entry : kcValues.entrySet()) {
			if (!entry.getKey().endsWith("_combined_kc")) {
				subcategoryData.put(entry.getKey(), entry.getValue());
			}
		}
	}

	/**
	 * Clear obtained items (for re-syncing)
	 */
	public void clearObtainedItems() {
		obtainedItems.clear();
	}
}


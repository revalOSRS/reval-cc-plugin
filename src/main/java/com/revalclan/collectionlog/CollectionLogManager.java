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
public class CollectionLogManager
{
	@Inject
	private Client client;

	/**
	 * Maps in-game category struct IDs to the list of items they contain
	 */
	@Getter
	private final Map<Integer, Set<Integer>> categoryItemMap = new HashMap<>();

	/**
	 * Maps slugified category names to their in-game struct ID
	 */
	@Getter
	private final Map<String, Integer> categoryStructIdMap = new HashMap<>();

	/**
	 * Maps top level tabs to their containing categories
	 */
	@Getter
	private final Map<Integer, Set<String>> categoryTabSlugs = new LinkedHashMap<>();

	/**
	 * List of all items in the collection log
	 */
	@Getter
	private final Set<Integer> allCollectionLogItems = new HashSet<>();

	/**
	 * Set of items the player has obtained (populated when collection log is opened)
	 */
	@Getter
	private final Set<ObtainedCollectionItem> obtainedItems = new HashSet<>();

	/**
	 * Maps category slugs to their KC varbit/varp IDs
	 */
	private final Map<String, KCSource> categoryKCMap = new HashMap<>();

	/**
	 * Helper class to store KC source information
	 */
	private static class KCSource
	{
		final boolean isVarbit;
		final int id;

		KCSource(boolean isVarbit, int id)
		{
			this.isVarbit = isVarbit;
			this.id = id;
		}
	}

	/**
	 * Initialize the KC mapping for collection log categories
	 * Using VarPlayerID constants (all varps, not varbits!)
	 */
	private void initializeKCMap()
	{
		categoryKCMap.put("barrows_chests", new KCSource(false, 1502)); // TOTAL_BARROWS_CHESTS
		categoryKCMap.put("kreearra", new KCSource(false, 1503)); // TOTAL_ARMADYL_KILLS
		categoryKCMap.put("general_graardor", new KCSource(false, 1504)); // TOTAL_BANDOS_KILLS
		categoryKCMap.put("commander_zilyana", new KCSource(false, 1505)); // TOTAL_SARADOMIN_KILLS
		categoryKCMap.put("kril_tsutsaroth", new KCSource(false, 1506)); // TOTAL_ZAMORAK_KILLS
		categoryKCMap.put("dagannoth_prime", new KCSource(false, 1507)); // TOTAL_PRIME_KILLS
		categoryKCMap.put("dagannoth_rex", new KCSource(false, 1508)); // TOTAL_REX_KILLS
		categoryKCMap.put("dagannoth_supreme", new KCSource(false, 1509)); // TOTAL_SUPREME_KILLS
		categoryKCMap.put("callisto_and_artio", new KCSource(false, 1510)); // TOTAL_CALLISTO_KILLS
		categoryKCMap.put("venenatis_and_spindel", new KCSource(false, 1511)); // TOTAL_VENENATIS_KILLS
		categoryKCMap.put("vetion_and_calvarion", new KCSource(false, 1512)); // TOTAL_VETION_KILLS
		categoryKCMap.put("chaos_elemental", new KCSource(false, 1513)); // TOTAL_CHAOSELE_KILLS
		categoryKCMap.put("king_black_dragon", new KCSource(false, 1514)); // TOTAL_KBD_KILLS
		categoryKCMap.put("giant_mole", new KCSource(false, 1515)); // TOTAL_MOLE_KILLS
		categoryKCMap.put("kalphite_queen", new KCSource(false, 1516)); // TOTAL_KALPHITE_KILLS
		categoryKCMap.put("corporeal_beast", new KCSource(false, 1517)); // TOTAL_CORP_KILLS
		categoryKCMap.put("zulrah", new KCSource(false, 1518)); // TOTAL_SNAKEBOSS_KILLS
		categoryKCMap.put("chaos_fanatic", new KCSource(false, 1519)); // TOTAL_CHAOSFANATIC_KILLS
		categoryKCMap.put("scorpia", new KCSource(false, 1520)); // TOTAL_SCORPIA_KILLS
		categoryKCMap.put("crazy_archaeologist", new KCSource(false, 1521)); // TOTAL_CRAZYARCHAEOLOGIST_KILLS
		categoryKCMap.put("tztok_jad", new KCSource(false, 1522)); // TOTAL_JAD_KILLS
		categoryKCMap.put("kraken", new KCSource(false, 1523)); // TOTAL_KRAKEN_BOSS_KILLS
		categoryKCMap.put("thermonuclear_smoke_devil", new KCSource(false, 1524)); // TOTAL_THERMY_KILLS
		categoryKCMap.put("cerberus", new KCSource(false, 1525)); // TOTAL_CERBERUS_KILLS
		categoryKCMap.put("abyssal_sire", new KCSource(false, 1526)); // TOTAL_ABYSSALSIRE_KILLS
		categoryKCMap.put("skotizo", new KCSource(false, 1527)); // TOTAL_CATA_BOSS_KILLS
		categoryKCMap.put("wintertodt", new KCSource(false, 1528)); // TOTAL_WINTERTODT_KILLS
		categoryKCMap.put("obor", new KCSource(false, 1529)); // TOTAL_HILLGIANT_BOSS_KILLS
		categoryKCMap.put("chambers_of_xeric", new KCSource(false, 1532)); // TOTAL_COMPLETED_XERICCHAMBERS
		categoryKCMap.put("tzkal_zuk", new KCSource(false, 1585)); // TOTAL_ZUK_KILLS (Inferno)
		categoryKCMap.put("deranged_archaeologist", new KCSource(false, 1661)); // TOTAL_DERANGEDARCHAEOLOGIST_KILLS
		categoryKCMap.put("grotesque_guardians", new KCSource(false, 1669)); // TOTAL_GARGBOSS_KILLS
		categoryKCMap.put("vorkath", new KCSource(false, 1691)); // TOTAL_VORKATH_KILLS
		categoryKCMap.put("bryophyta", new KCSource(false, 1733)); // TOTAL_BRYOPHYTA_KILLS
		categoryKCMap.put("theatre_of_blood", new KCSource(false, 1748)); // TOTAL_COMPLETED_THEATREOFBLOOD
		categoryKCMap.put("alchemical_hydra", new KCSource(false, 2074)); // TOTAL_HYDRABOSS_KILLS
		categoryKCMap.put("hespori", new KCSource(false, 2075)); // TOTAL_HESPORI_KILLS
		categoryKCMap.put("mimic", new KCSource(false, 2221)); // TOTAL_MIMIC_KILLS
		categoryKCMap.put("sarachnis", new KCSource(false, 2233)); // TOTAL_SARACHNIS_KILLS
		categoryKCMap.put("zalcano", new KCSource(false, 2352)); // TOTAL_ZALCANO_KILLS
		categoryKCMap.put("the_gauntlet", new KCSource(false, 2353)); // TOTAL_COMPLETED_GAUNTLET
		categoryKCMap.put("the_corrupted_gauntlet", new KCSource(false, 2354)); // TOTAL_COMPLETED_GAUNTLET_HM
		categoryKCMap.put("the_nightmare", new KCSource(false, 2664)); // TOTAL_NIGHTMARE_KILLS
		categoryKCMap.put("phosanis_nightmare", new KCSource(false, 2671)); // TOTAL_NIGHTMARE_CHALLENGE_KILLS
		categoryKCMap.put("tempoross", new KCSource(false, 2934)); // TOTAL_TEMPOROSS_KILLS
		categoryKCMap.put("nex", new KCSource(false, 3269)); // TOTAL_NEX_KILLS
		categoryKCMap.put("phantom_muspah", new KCSource(false, 3752)); // TOTAL_MUSPAH_KILLS
		categoryKCMap.put("artio", new KCSource(false, 3761)); // TOTAL_ARTIO_KILLS
		categoryKCMap.put("spindel", new KCSource(false, 3762)); // TOTAL_SPINDEL_KILLS
		categoryKCMap.put("calvarion", new KCSource(false, 3763)); // TOTAL_CALVARION_KILLS
		categoryKCMap.put("duke_sucellus", new KCSource(false, 3967)); // TOTAL_DUKE_SUCELLUS_KILLS
		categoryKCMap.put("the_leviathan", new KCSource(false, 3968)); // TOTAL_LEVIATHAN_KILLS
		categoryKCMap.put("the_whisperer", new KCSource(false, 3969)); // TOTAL_WHISPERER_KILLS
		categoryKCMap.put("vardorvis", new KCSource(false, 3970)); // TOTAL_VARDORVIS_KILLS
		categoryKCMap.put("scurrius", new KCSource(false, 4079)); // TOTAL_RAT_BOSS_KILLS
		categoryKCMap.put("sol_heredit", new KCSource(false, 4187)); // TOTAL_SOL_KILLS
		categoryKCMap.put("araxxor", new KCSource(false, 4260)); // TOTAL_ARAXXOR_KILLS
		categoryKCMap.put("lunar_chests", new KCSource(false, 4186)); // TOTAL_PMOON_CHESTS
		categoryKCMap.put("the_hueycoatl", new KCSource(false, 4404)); // TOTAL_HUEY_KILLS
		categoryKCMap.put("amoxliatl", new KCSource(false, 4403)); // TOTAL_AMOXLIATL_KILLS
		categoryKCMap.put("colosseum", new KCSource(false, 4131)); // TOTAL_COLOSSEUM_WAVES_COMPLETED
		categoryKCMap.put("fortis_colosseum", new KCSource(false, 4131)); // TOTAL_COLOSSEUM_WAVES_COMPLETED

		// Raids (also from VarPlayerID)
		categoryKCMap.put("chambers_of_xeric_challenge_mode", new KCSource(false, 1735)); // TOTAL_COMPLETED_XERICCHAMBERS_CHALLENGE
		categoryKCMap.put("theatre_of_blood_hard_mode", new KCSource(false, 3057)); // TOTAL_COMPLETED_THEATREOFBLOOD_HARD
		categoryKCMap.put("tombs_of_amascut", new KCSource(false, 3646)); // TOTAL_COMPLETED_TOMBSOFAMASCUT

		// Clue scrolls (varbits 11996-12002)
		categoryKCMap.put("beginner_treasure_trails", new KCSource(true, 11996));
		categoryKCMap.put("easy_treasure_trails", new KCSource(true, 11997));
		categoryKCMap.put("medium_treasure_trails", new KCSource(true, 11998));
		categoryKCMap.put("hard_treasure_trails", new KCSource(true, 11999));
		categoryKCMap.put("elite_treasure_trails", new KCSource(true, 12000));
		categoryKCMap.put("master_treasure_trails", new KCSource(true, 12001));
	}

	/**
	 * Parse the game cache to extract all collection log structure
	 */
	public void parseCacheForCollectionLog()
	{
		if (client.getIndexConfig() == null)
		{
			log.warn("Cannot parse collection log - index config is null");
			return;
		}

		initializeKCMap();

		categoryItemMap.clear();
		categoryStructIdMap.clear();
		categoryTabSlugs.clear();
		allCollectionLogItems.clear();

		final Pattern specialCharacterPattern = Pattern.compile("['()]", Pattern.CASE_INSENSITIVE);

		try
		{
			EnumComposition replacements = client.getEnum(3721);
			int[] topLevelTabStructIds = client.getEnum(2102).getIntVals();

			for (int topLevelTabStructIndex : topLevelTabStructIds)
			{
				StructComposition topLevelTabStruct = client.getStructComposition(topLevelTabStructIndex);
				Set<String> singleCategorySlugSet = new LinkedHashSet<>();

				int[] subtabStructIndices = client.getEnum(topLevelTabStruct.getIntValue(683)).getIntVals();

				for (int subtabStructIndex : subtabStructIndices)
				{
					StructComposition subtabStruct = client.getStructComposition(subtabStructIndex);
					int[] clogItems = client.getEnum(subtabStruct.getIntValue(690)).getIntVals();
					String categoryName = subtabStruct.getStringValue(689);

					String normalizedCategoryName = specialCharacterPattern
						.matcher(
							categoryName
								.toLowerCase()
								.replaceAll(" ", "_")
						)
						.replaceAll("");

					Set<Integer> itemSet = new LinkedHashSet<>();

					for (int clogItemId : clogItems)
					{
						final int replacementId = replacements.getIntValue(clogItemId);
						int finalItemId = replacementId == -1 ? clogItemId : replacementId;
						itemSet.add(finalItemId);
					}

					allCollectionLogItems.addAll(itemSet);
					categoryItemMap.put(subtabStructIndex, itemSet);
					categoryStructIdMap.put(normalizedCategoryName, subtabStructIndex);
					singleCategorySlugSet.add(normalizedCategoryName);
				}

				categoryTabSlugs.put(topLevelTabStructIndex, singleCategorySlugSet);
			}

			log.info("Successfully parsed collection log cache: {} total items across {} categories",
				allCollectionLogItems.size(), categoryStructIdMap.size());
		}
		catch (Exception e)
		{
			log.error("Error parsing collection log cache", e);
		}
	}

	/**
	 * Called when collection log opens - tracks which items the player has obtained
	 */
	public void onCollectionLogItemObtained(int itemId, int itemCount, String itemName)
	{
		obtainedItems.add(new ObtainedCollectionItem(itemId, itemName, itemCount));
	}

	/**
	 * Get collection log data grouped by category
	 */
	public Map<String, Object> getCollectionLogData()
	{
		Map<String, Object> data = new HashMap<>();

		data.put("totalItems", allCollectionLogItems.size());
		
		if (!obtainedItems.isEmpty())
		{
			data.put("obtainedItems", obtainedItems.size());
			data.put("dataSource", "collection_log_opened");
		}
		else
		{
			try
			{
				int uniqueItemsFromVarbit = client.getVarpValue(2943);
				data.put("obtainedItems", uniqueItemsFromVarbit);
				data.put("dataSource", "varbit_2943");
				data.put("note", "Click the Search button in collection log for detailed item data");
			}
			catch (Exception e)
			{
				data.put("obtainedItems", 0);
				data.put("dataSource", "unavailable");
				data.put("note", "Click the Search button in collection log for detailed item data");
			}
		}

		Map<String, Map<String, Object>> categoriesData = new LinkedHashMap<>();

		for (Map.Entry<String, Integer> entry : categoryStructIdMap.entrySet())
		{
			String categorySlug = entry.getKey();
			Integer structId = entry.getValue();
			Set<Integer> categoryItems = categoryItemMap.get(structId);

			if (categoryItems == null)
			{
				continue;
			}

			Map<Integer, ObtainedCollectionItem> obtainedItemsMap = new HashMap<>();
			for (ObtainedCollectionItem item : obtainedItems)
			{
				obtainedItemsMap.put(item.getId(), item);
			}

			Map<String, Object> categoryData = new HashMap<>();
			List<Map<String, Object>> itemsList = new ArrayList<>();
			int obtainedCount = 0;

			for (Integer itemId : categoryItems)
			{
				Map<String, Object> itemData = new HashMap<>();
				itemData.put("id", itemId);
				
				ObtainedCollectionItem obtainedItem = obtainedItemsMap.get(itemId);
				if (obtainedItem != null)
				{
					itemData.put("name", obtainedItem.getName());
					itemData.put("quantity", obtainedItem.getCount());
					itemData.put("obtained", true);
					obtainedCount++;
				}
				else
				{
					try
					{
						String itemName = client.getItemDefinition(itemId).getName();
						itemData.put("name", itemName);
						itemData.put("quantity", 0);
						itemData.put("obtained", false);
					}
					catch (Exception e)
					{
						itemData.put("name", "Unknown");
						itemData.put("quantity", 0);
						itemData.put("obtained", false);
					}
				}
				
				itemsList.add(itemData);
			}

			categoryData.put("total", categoryItems.size());
			categoryData.put("obtained", obtainedCount);
			categoryData.put("items", itemsList);

				KCSource kcSource = categoryKCMap.get(categorySlug);
				if (kcSource != null)
				{
					try
					{
						int kc = kcSource.isVarbit
							? client.getVarbitValue(kcSource.id)
							: client.getVarpValue(kcSource.id);
						categoryData.put("kc", kc);
					}
					catch (Exception e)
					{
						log.error("Error getting KC for category '{}': {}", categorySlug, e.getMessage());
						categoryData.put("kc", 0);
					}
				}
			else
			{
				categoryData.put("kc", 0);
			}

			categoriesData.put(categorySlug, categoryData);
		}

		data.put("categories", categoriesData);

		return data;
	}

	/**
	 * Clear obtained items (for re-syncing)
	 */
	public void clearObtainedItems()
	{
		obtainedItems.clear();
	}
}


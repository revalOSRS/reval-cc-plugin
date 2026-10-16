package com.revalclan.pbs;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.ScriptEvent;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Captures collection-log PB transmissions together with their page identity.
 * Never samples widgets later: resize/local redraws can combine another page's
 * name with the shared PB transmission registers.
 */
@Singleton
public class ClogPersonalBestCapture {
	// The old header-scraping cache is untrusted. Do not copy or upload it.
	private static final int DRAW_LIST = 2730;
	private static final int DRAW = 7797;
	private static final int TAB_LINK = 4900;
	private static final int TABS_ENUM = 2102;
	private static final int CATEGORIES_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;

	// Pages whose collection_category_count script renders primary PB slot 1.
	// Unknown pages fail closed; merely having a name does not imply a PB.
	private static final Set<String> PB_PAGES = Set.of(
		"alchemical hydra", "amoxliatl", "araxxor", "brutus", "doom of mokhaiotl",
		"duke sucellus", "fortis colosseum", "grotesque guardians", "hespori",
		"hueycoatl", "leviathan", "mad angel", "maggot king", "nex", "nightmare",
		"phantom muspah", "royal titans", "shellbane gryphon", "tempoross",
		"vardorvis", "vorkath", "whisperer", "yama", "zulrah"
	);

	@Inject private Client client;
	@Inject private ConfigManager configManager;

	public Map<String, Object> sync() {
		return PbStore.read(configManager, PbStore.VERIFIED_GROUP);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event) {
		int script = event.getScriptId();
		if (script != DRAW_LIST && script != DRAW && script != TAB_LINK) return;

		ScriptEvent input = event.getScriptEvent();
		// Only root page responses: nested procs have no ScriptEvent, widget
		// resize listeners have a source, and neither owns a PB transmission.
		if (input == null || input.getSource() != null
			|| client.getGameState() != GameState.LOGGED_IN
			|| configManager.getRSProfileKey() == null
			|| client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) != 0) return;

		Object[] args = input.getArguments();
		int expectedLength = script == TAB_LINK ? 4 : 8;
		if (args == null || args.length != expectedLength) return;
		for (Object arg : args) {
			if (!(arg instanceof Integer)) return;
		}
		if ((int) args[0] != script) return;

		int tab = (int) args[1];
		int category = (int) args[expectedLength - 1];
		if (tab < 0 || tab > 4 || category < 0
			|| tab != client.getVarbitValue(VarbitID.COLLECTION_LAST_TAB)
			|| category != client.getVarbitValue(VarbitID.COLLECTION_LAST_CATEGORY)) return;

		EnumComposition tabs = client.getEnum(TABS_ENUM);
		if (tabs == null) return;
		int tabStructId = tabs.getIntValue(tab);
		if (tabStructId <= 0) return;
		StructComposition tabStruct = client.getStructComposition(tabStructId);
		if (tabStruct == null) return;
		int categoryEnumId = tabStruct.getIntValue(CATEGORIES_PARAM);
		if (categoryEnumId <= 0) return;
		EnumComposition categories = client.getEnum(categoryEnumId);
		if (categories == null) return;
		int pageStructId = categories.getIntValue(category);
		if (pageStructId <= 0) return;

		// List/setup carries the top-level tab struct; search links carry the
		// page struct itself. Validate that it is the selected cache entry.
		int responseStructId = (int) args[expectedLength - 2];
		if (responseStructId != (script == TAB_LINK ? pageStructId : tabStructId)) return;
		StructComposition page = client.getStructComposition(pageStructId);
		if (page == null) return;
		String name = page.getStringValue(PAGE_NAME_PARAM);
		if (name == null) return;
		String key = name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
		if (key.startsWith("the ")) key = key.substring(4);

		if (key.equals("gauntlet")) {
			// The page's first counter is corrupted KC, second is regular KC.
			store("gauntlet", VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT,
				VarPlayerID.COLLECTION_CATEGORY_COUNT2);
			store("corrupted gauntlet", VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT_2,
				VarPlayerID.COLLECTION_CATEGORY_COUNT);
		} else if (PB_PAGES.contains(key)) {
			store(key.equals("fortis colosseum") ? "sol heredit" : key,
				VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT, VarPlayerID.COLLECTION_CATEGORY_COUNT);
		}
	}

	private void store(String key, int pbVarp, int kcVarp) {
		int ticks = client.getVarpValue(pbVarp);
		int completions = client.getVarpValue(kcVarp);
		// The game's time_convert[_ms] scripts use 0.6 seconds per tick.
		// Read raw ticks so the player's precise-timing setting cannot round PBs.
		PbStore.recordVerified(configManager, key, ticks, completions);
	}
}

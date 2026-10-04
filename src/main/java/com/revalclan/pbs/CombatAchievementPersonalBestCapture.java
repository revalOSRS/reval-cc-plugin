package com.revalclan.pbs;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.ScriptEvent;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import java.util.Map;

/** Captures a CA boss page's raw PB while its initialization script owns IF1. */
@Singleton
public class CombatAchievementPersonalBestCapture {
	private static final int BOSS_INIT = 4835;
	private static final int BOSSES_ENUM = 3987;
	private static final int NAME_PARAM = 1313;
	private static final int BOSS_ID_PARAM = 1315;

	// ca_specific_killcount (4778): stable per-boss counters, not collection-log
	// transmission registers. Unknown IDs fail closed. The aggregate Ket-Rak
	// challenges page has no single completion counter or PB and is excluded.
	private static final Map<Integer, Integer> KILLCOUNT_VARPS = Map.ofEntries(
		Map.entry(1, VarPlayerID.TOTAL_ABYSSALSIRE_KILLS),
		Map.entry(2, VarPlayerID.TOTAL_HYDRABOSS_KILLS),
		Map.entry(3, VarPlayerID.TOTAL_AMOXLIATL_KILLS),
		Map.entry(4, VarPlayerID.TOTAL_ARAXXOR_KILLS),
		Map.entry(5, VarPlayerID.TOTAL_BARROWS_CHESTS),
		Map.entry(7, VarPlayerID.TOTAL_BRYOPHYTA_KILLS),
		Map.entry(8, VarPlayerID.TOTAL_CALLISTO_KILLS),
		Map.entry(9, VarPlayerID.TOTAL_CERBERUS_KILLS),
		Map.entry(10, VarPlayerID.TOTAL_CHAOSELE_KILLS),
		Map.entry(11, VarPlayerID.TOTAL_CHAOSFANATIC_KILLS),
		Map.entry(12, VarPlayerID.TOTAL_CRAZYARCHAEOLOGIST_KILLS),
		Map.entry(13, VarPlayerID.TOTAL_COMPLETED_XERICCHAMBERS),
		Map.entry(14, VarPlayerID.TOTAL_COMPLETED_XERICCHAMBERS_CHALLENGE),
		Map.entry(15, VarPlayerID.TOTAL_CORP_KILLS),
		Map.entry(16, VarPlayerID.TOTAL_SARADOMIN_KILLS),
		Map.entry(17, VarPlayerID.TOTAL_COMPLETED_GAUNTLET),
		Map.entry(18, VarPlayerID.TOTAL_COMPLETED_GAUNTLET_HM),
		Map.entry(19, VarPlayerID.TOTAL_PRIME_KILLS),
		Map.entry(20, VarPlayerID.TOTAL_REX_KILLS),
		Map.entry(21, VarPlayerID.TOTAL_SUPREME_KILLS),
		Map.entry(22, VarPlayerID.TOTAL_DERANGEDARCHAEOLOGIST_KILLS),
		Map.entry(23, VarPlayerID.TOTAL_DOM_LEVELS),
		Map.entry(24, VarPlayerID.TOTAL_DUKE_SUCELLUS_KILLS),
		Map.entry(25, VarPlayerID.TOTAL_SOL_KILLS),
		Map.entry(26, VarPlayerID.TOTAL_BANDOS_KILLS),
		Map.entry(27, VarPlayerID.TOTAL_MOLE_KILLS),
		Map.entry(28, VarPlayerID.TOTAL_GARGBOSS_KILLS),
		Map.entry(29, VarPlayerID.TOTAL_HESPORI_KILLS),
		Map.entry(30, VarPlayerID.TOTAL_HUEY_KILLS),
		Map.entry(31, VarPlayerID.TOTAL_KALPHITE_KILLS),
		Map.entry(32, VarPlayerID.TOTAL_KBD_KILLS),
		Map.entry(33, VarPlayerID.TOTAL_KRAKEN_BOSS_KILLS),
		Map.entry(34, VarPlayerID.TOTAL_ARMADYL_KILLS),
		Map.entry(35, VarPlayerID.TOTAL_ZAMORAK_KILLS),
		Map.entry(36, VarPlayerID.TOTAL_LEVIATHAN_KILLS),
		Map.entry(39, VarPlayerID.TOTAL_MIMIC_KILLS),
		Map.entry(40, VarPlayerID.TOTAL_PMOON_CHESTS),
		Map.entry(41, VarPlayerID.TOTAL_NEX_KILLS),
		Map.entry(42, VarPlayerID.TOTAL_NIGHTMARE_KILLS),
		Map.entry(43, VarPlayerID.TOTAL_NIGHTMARE_CHALLENGE_KILLS),
		Map.entry(44, VarPlayerID.TOTAL_HILLGIANT_BOSS_KILLS),
		Map.entry(45, VarPlayerID.TOTAL_MUSPAH_KILLS),
		Map.entry(46, VarPlayerID.TOTAL_ROYAL_TITAN_KILLS),
		Map.entry(47, VarPlayerID.TOTAL_RAT_BOSS_KILLS),
		Map.entry(48, VarPlayerID.TOTAL_SARACHNIS_KILLS),
		Map.entry(49, VarPlayerID.TOTAL_SCORPIA_KILLS),
		Map.entry(50, VarPlayerID.TOTAL_GRYPHON_BOSS_KILLS),
		Map.entry(51, VarPlayerID.TOTAL_CATA_BOSS_KILLS),
		Map.entry(52, VarPlayerID.TOTAL_TEMPOROSS_KILLS),
		Map.entry(53, VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD_STORY),
		Map.entry(54, VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD),
		Map.entry(55, VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD_HARD),
		Map.entry(56, VarPlayerID.TOTAL_THERMY_KILLS),
		Map.entry(57, VarPlayerID.TOTAL_COMPLETED_TOMBSOFAMASCUT_ENTRY),
		Map.entry(58, VarPlayerID.TOTAL_COMPLETED_TOMBSOFAMASCUT),
		Map.entry(59, VarPlayerID.TOTAL_COMPLETED_TOMBSOFAMASCUT_EXPERT),
		Map.entry(61, VarPlayerID.TOTAL_ZUK_KILLS),
		Map.entry(62, VarPlayerID.TOTAL_JAD_KILLS),
		Map.entry(63, VarPlayerID.TOTAL_VARDORVIS_KILLS),
		Map.entry(64, VarPlayerID.TOTAL_VENENATIS_KILLS),
		Map.entry(65, VarPlayerID.TOTAL_VETION_KILLS),
		Map.entry(66, VarPlayerID.TOTAL_VORKATH_KILLS),
		Map.entry(67, VarPlayerID.TOTAL_WHISPERER_KILLS),
		Map.entry(68, VarPlayerID.TOTAL_WINTERTODT_KILLS),
		Map.entry(69, VarPlayerID.TOTAL_YAMA_KILLS),
		Map.entry(70, VarPlayerID.TOTAL_ZALCANO_KILLS),
		Map.entry(71, VarPlayerID.TOTAL_SNAKEBOSS_KILLS),
		Map.entry(6, VarPlayerID.TOTAL_COWBOSS_KILLS),
		Map.entry(38, VarPlayerID.TOTAL_MAGGOT_KING_KILLS),
		Map.entry(37, VarPlayerID.TOTAL_MAD_ANGEL_KILLS)
	);

	@Inject private Client client;
	@Inject private ConfigManager configManager;

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event) {
		if (event.getScriptId() != BOSS_INIT) return;
		ScriptEvent input = event.getScriptEvent();
		if (input == null
			|| client.getGameState() != GameState.LOGGED_IN
			|| configManager.getRSProfileKey() == null
			|| client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) != 0) return;
		// Unlike Collection Log's server draw, CA opens through an onload event
		// sourced from its universe widget. Other widget listeners are not page loads.
		if (input.getSource() != null && input.getSource().getId() != InterfaceID.CaBoss.UNIVERSE) return;

		// ca_boss_init has fourteen component arguments. Accept only the real
		// boss interface's root initialization, never local/nested redraws.
		Object[] args = input.getArguments();
		if (args == null || args.length != 15 || !Integer.valueOf(BOSS_INIT).equals(args[0])) return;
		for (int i = 1; i < args.length; i++) {
			if (!(args[i] instanceof Integer) || ((int) args[i] >>> 16) != InterfaceID.CA_BOSS) return;
		}
		if ((int) args[1] != InterfaceID.CaBoss.FRAME
			|| (int) args[10] != InterfaceID.CaBoss.CA_BOSS_STATS) return;

		// This script resolves its boss from the server's CA_BOSS_SELECTED,
		// rather than a queued category argument like Collection Log does.
		int bossId = client.getVarbitValue(VarbitID.CA_BOSS_SELECTED);
		Integer kcVarp = KILLCOUNT_VARPS.get(bossId);
		if (kcVarp == null) return;
		EnumComposition bosses = client.getEnum(BOSSES_ENUM);
		if (bosses == null) return;
		int structId = bosses.getIntValue(bossId);
		if (structId <= 0) return;
		StructComposition boss = client.getStructComposition(structId);
		if (boss == null || boss.getIntValue(BOSS_ID_PARAM) != bossId) return;
		String name = boss.getStringValue(NAME_PARAM);
		if (name == null || name.trim().isEmpty()) return;

		// ca_boss_init_stats (4843) reads IF1: -1 means no PB field, 0 means N/A.
		// IF1 is shared by other interfaces; do not sample it after this script.
		int ticks = client.getVarpValue(VarPlayerID.IF1);
		if (ticks < 0) return;
		PbStore.recordVerified(configManager, key(name), ticks, client.getVarpValue(kcVarp));
	}

	private static String key(String name) {
		String key = name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ").replaceAll(":\\s*", " ");
		if (key.startsWith("the ")) key = key.substring(4);
		switch (key) {
			case "crystalline hunllef": return "gauntlet";
			case "corrupted hunllef": return "corrupted gauntlet";
			case "fortis colosseum": return "sol heredit";
			case "phosanis nightmare": return "phosani's nightmare";
			default: return key;
		}
	}
}

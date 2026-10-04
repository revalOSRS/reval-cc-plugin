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
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CombatAchievementPersonalBestCaptureTest {
	@Mock private Client client;
	@Mock private ConfigManager config;
	@InjectMocks private CombatAchievementPersonalBestCapture capture;
	private final EventBus events = new EventBus();
	private final Map<String, String> saved = new HashMap<>();
	private final EnumComposition bosses = mock(EnumComposition.class);
	private final StructComposition boss = mock(StructComposition.class);

	@Before public void setUp() {
		MockitoAnnotations.openMocks(this);
		events.register(capture);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(config.getRSProfileKey()).thenReturn("profile-a");
		when(client.getEnum(3987)).thenReturn(bosses);
		when(bosses.getIntValue(anyInt())).thenReturn(3565);
		when(client.getStructComposition(3565)).thenReturn(boss);
		doAnswer(i -> { saved.put(config.getRSProfileKey() + ":" + i.getArgument(0) + ":" + i.getArgument(1), String.valueOf((Object) i.getArgument(2))); return null; })
			.when(config).setRSProfileConfiguration(anyString(), anyString(), any());
		doAnswer(i -> { saved.remove(config.getRSProfileKey() + ":" + i.getArgument(0) + ":" + i.getArgument(1)); return null; })
			.when(config).unsetRSProfileConfiguration(anyString(), anyString());
		when(config.getRSProfileConfigurationKeys(anyString(), anyString(), eq(""))).thenAnswer(i -> {
			String prefix = i.getArgument(1) + ":" + i.getArgument(0) + ":";
			return saved.keySet().stream().filter(k -> k.startsWith(prefix)).map(k -> k.substring(prefix.length())).collect(java.util.stream.Collectors.toList());
		});
		when(config.getRSProfileConfiguration(anyString(), anyString())).thenAnswer(i -> saved.get(config.getRSProfileKey() + ":" + i.getArgument(0) + ":" + i.getArgument(1)));
		select(62, "TzTok-Jad", VarPlayerID.TOTAL_JAD_KILLS, 2, 4955);
	}

	private void select(int id, String name, int countVarp, int count, int ticks) {
		when(client.getVarbitValue(VarbitID.CA_BOSS_SELECTED)).thenReturn(id);
		when(boss.getIntValue(1315)).thenReturn(id);
		when(boss.getStringValue(1313)).thenReturn(name);
		when(client.getVarpValue(countVarp)).thenReturn(count);
		when(client.getVarpValue(VarPlayerID.IF1)).thenReturn(ticks);
	}

	private ScriptPreFired init() {
		Object[] args = new Object[15];
		args[0] = 4835;
		for (int i = 1; i < args.length; i++) args[i] = InterfaceID.CaBoss.FRAME;
		args[10] = InterfaceID.CaBoss.CA_BOSS_STATS;
		ScriptEvent input = mock(ScriptEvent.class);
		when(input.getArguments()).thenReturn(args);
		ScriptPreFired event = new ScriptPreFired(4835);
		event.setScriptEvent(input);
		return event;
	}

	private Map<String, Object> sync() { return PbStore.read(config, PbStore.VERIFIED_GROUP); }

	@Test public void capturesJadRawTicksInTheExistingUploadDomain() {
		events.post(init());
		assertEquals(2973.0, (Double) sync().get("tztok-jad"), .00001);
		verify(client, never()).getWidget(anyInt());
	}

	@Test public void acceptsTheLiveBossInterfaceOnloadEvent() {
		ScriptPreFired event = init();
		Widget universe = mock(Widget.class);
		when(universe.getId()).thenReturn(46727169);
		when(event.getScriptEvent().getSource()).thenReturn(universe);
		when(event.getScriptEvent().getArguments()).thenReturn(new Object[] {
			4835, 46727170, 46727171, 46727173, 46727174, 46727177, 46727178,
			46727181, 46727182, 46727189, 46727190, 46727191, 46727193, 46727195, 46727194
		});
		events.post(event);
		assertTrue("The live CA onload must populate the upload cache", sync().containsKey("tztok-jad"));
		assertEquals("The CA onload source is legitimate, unlike collection-log resize listeners",
			2973.0, (Double) sync().get("tztok-jad"), .00001);
	}

	@Test public void toaNormalAndExpertRemainSeparateWithoutInventingTeamSize() {
		select(58, "Tombs of Amascut", VarPlayerID.TOTAL_COMPLETED_TOMBSOFAMASCUT, 13, 1390);
		events.post(init());
		select(59, "Tombs of Amascut: Expert Mode", VarPlayerID.TOTAL_COMPLETED_TOMBSOFAMASCUT_EXPERT, 104, 1791);
		events.post(init());
		assertEquals(834.0, (Double) sync().get("tombs of amascut"), .00001);
		assertEquals(1074.6, (Double) sync().get("tombs of amascut expert mode"), .00001);
		assertEquals(2, sync().size());
	}

	@Test public void raidModesUseTheirOwnCompletionCounters() {
		int[] ids = {13, 14, 53, 54, 55, 57};
		int[] counters = {VarPlayerID.TOTAL_COMPLETED_XERICCHAMBERS, VarPlayerID.TOTAL_COMPLETED_XERICCHAMBERS_CHALLENGE,
			VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD_STORY, VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD,
			VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD_HARD, VarPlayerID.TOTAL_COMPLETED_TOMBSOFAMASCUT_ENTRY};
		String[] names = {"Chambers of Xeric", "Chambers of Xeric: Challenge Mode", "Theatre of Blood: Entry Mode",
			"Theatre of Blood", "Theatre of Blood: Hard Mode", "Tombs of Amascut: Entry Mode"};
		for (int i = 0; i < ids.length; i++) {
			select(ids[i], names[i], counters[i], 1, 1000 + i);
			events.post(init());
			assertEquals((1000 + i) * .6, (Double) sync().get(names[i].toLowerCase(java.util.Locale.ROOT).replace(":", "")), .00001);
			select(ids[i], names[i], counters[i], 0, 1000 + i);
			events.post(init());
			assertTrue("A different mode's KC cannot preserve this mode's PB", sync().isEmpty());
		}
	}

	@Test public void matchesExistingGauntletColosseumAndNightmareKeys() {
		int[] ids = {17, 18, 25, 43};
		String[] names = {"Crystalline Hunllef", "Corrupted Hunllef", "Fortis Colosseum", "Phosanis Nightmare"};
		String[] keys = {"gauntlet", "corrupted gauntlet", "sol heredit", "phosani's nightmare"};
		int[] counters = {VarPlayerID.TOTAL_COMPLETED_GAUNTLET, VarPlayerID.TOTAL_COMPLETED_GAUNTLET_HM,
			VarPlayerID.TOTAL_SOL_KILLS, VarPlayerID.TOTAL_NIGHTMARE_CHALLENGE_KILLS};
		for (int i = 0; i < ids.length; i++) {
			select(ids[i], names[i], counters[i], 1, 500);
			events.post(init());
			assertEquals(300.0, (Double) sync().get(keys[i]), .00001);
		}
	}

	@Test public void resolvesCurrentBossOnEachRootInitAfterRapidPageSwitches() {
		ScriptPreFired queued = init();
		select(3, "Amoxliatl", VarPlayerID.TOTAL_AMOXLIATL_KILLS, 92, 46);
		events.post(queued);
		assertFalse(sync().containsKey("tztok-jad"));
		assertEquals(27.6, (Double) sync().get("amoxliatl"), .00001);
	}

	@Test public void negativeIf1MeansUnsupportedPbAndDoesNotEraseVerifiedData() {
		events.post(init());
		when(client.getVarpValue(VarPlayerID.IF1)).thenReturn(-1);
		events.post(init());
		assertEquals(2973.0, (Double) sync().get("tztok-jad"), .00001);
	}

	@Test public void authoritativeNaOrZeroCompletionsClearSavedCopy() {
		events.post(init());
		when(client.getVarpValue(VarPlayerID.IF1)).thenReturn(0);
		events.post(init());
		assertTrue(sync().isEmpty());
		select(62, "TzTok-Jad", VarPlayerID.TOTAL_JAD_KILLS, 2, 4955);
		events.post(init());
		when(client.getVarpValue(VarPlayerID.TOTAL_JAD_KILLS)).thenReturn(0);
		events.post(init());
		assertTrue(sync().isEmpty());
	}

	@Test public void otherInterfacesAndNestedOrWidgetScriptsCannotReadIf1() {
		events.post(new ScriptPreFired(4843));
		ScriptPreFired event = init();
		Widget listener = mock(Widget.class);
		when(listener.getId()).thenReturn(InterfaceID.CaBoss.CA_BOSS_STATS);
		when(event.getScriptEvent().getSource()).thenReturn(listener);
		events.post(event);
		event = init();
		event.getScriptEvent().getArguments()[10] = 621 << 16;
		events.post(event);
		assertTrue(sync().isEmpty());
	}

	@Test public void missingProfilesGuestLogsAndLoggedOutStatesCannotSave() {
		when(config.getRSProfileKey()).thenReturn(null);
		events.post(init());
		when(config.getRSProfileKey()).thenReturn("profile-a");
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(1);
		events.post(init());
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(0);
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		events.post(init());
		assertTrue(sync().isEmpty());
	}

	@Test public void missingOrMismatchedBossDefinitionsFailClosed() {
		when(boss.getIntValue(1315)).thenReturn(59);
		events.post(init());
		when(boss.getIntValue(1315)).thenReturn(62);
		when(client.getEnum(3987)).thenReturn(null);
		events.post(init());
		assertTrue(sync().isEmpty());
	}

	@Test public void unknownBossAndMalformedArgumentsFailClosed() {
		when(client.getVarbitValue(VarbitID.CA_BOSS_SELECTED)).thenReturn(255);
		events.post(init());
		when(client.getVarbitValue(VarbitID.CA_BOSS_SELECTED)).thenReturn(62);
		ScriptPreFired event = init();
		when(event.getScriptEvent().getArguments()).thenReturn(new Object[] {4835});
		events.post(event);
		event = init();
		event.getScriptEvent().getArguments()[1] = "not a component";
		events.post(event);
		assertTrue(sync().isEmpty());
	}

	@Test public void cacheRemainsIsolatedAcrossAccountSwitches() {
		events.post(init());
		when(config.getRSProfileKey()).thenReturn("profile-b");
		assertTrue(sync().isEmpty());
		select(62, "TzTok-Jad", VarPlayerID.TOTAL_JAD_KILLS, 1, 6000);
		events.post(init());
		assertEquals(3600.0, (Double) sync().get("tztok-jad"), .00001);
		when(config.getRSProfileKey()).thenReturn("profile-a");
		assertEquals(2973.0, (Double) sync().get("tztok-jad"), .00001);
	}
}

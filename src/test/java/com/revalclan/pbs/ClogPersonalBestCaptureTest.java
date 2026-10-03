package com.revalclan.pbs;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClogPersonalBestCaptureTest {
	@Mock private Client client;
	@Mock private ClientThread clientThread;
	@Mock private ConfigManager config;
	@InjectMocks private ClogPersonalBestCapture capture;
	private final EventBus events = new EventBus();
	private final Map<String, String> saved = new HashMap<>();
	private final EnumComposition tabs = mock(EnumComposition.class);
	private final EnumComposition categories = mock(EnumComposition.class);
	private final StructComposition tab = mock(StructComposition.class);
	private final StructComposition page = mock(StructComposition.class);

	@Before public void setUp() {
		MockitoAnnotations.openMocks(this);
		events.register(capture);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(config.getRSProfileKey()).thenReturn("profile-a");
		when(client.getEnum(2102)).thenReturn(tabs);
		when(tabs.getIntValue(0)).thenReturn(471);
		when(client.getStructComposition(471)).thenReturn(tab);
		when(tab.getIntValue(683)).thenReturn(2103);
		when(client.getEnum(2103)).thenReturn(categories);
		when(categories.getIntValue(0)).thenReturn(539);
		when(client.getStructComposition(539)).thenReturn(page);
		when(page.getStringValue(689)).thenReturn("Alchemical Hydra");
		when(client.getVarpValue(VarPlayerID.COLLECTION_CATEGORY_COUNT)).thenReturn(4851);
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(111);
		doAnswer(i -> { saved.put(config.getRSProfileKey() + ":" + i.getArgument(0) + ":" + i.getArgument(1), String.valueOf((Object) i.getArgument(2))); return null; })
			.when(config).setRSProfileConfiguration(anyString(), anyString(), any());
		doAnswer(i -> { saved.remove(config.getRSProfileKey() + ":" + i.getArgument(0) + ":" + i.getArgument(1)); return null; })
			.when(config).unsetRSProfileConfiguration(anyString(), anyString());
		when(config.getRSProfileConfigurationKeys(anyString(), anyString(), eq(""))).thenAnswer(i -> {
			String prefix = i.getArgument(1) + ":" + i.getArgument(0) + ":";
			return saved.keySet().stream().filter(k -> k.startsWith(prefix)).map(k -> k.substring(prefix.length())).collect(java.util.stream.Collectors.toList());
		});
		when(config.getRSProfileConfiguration(anyString(), anyString())).thenAnswer(i -> saved.get(config.getRSProfileKey() + ":" + i.getArgument(0) + ":" + i.getArgument(1)));
		// Executes the old asynchronous callback too, so the old implementation
		// actually reproduces the header pairing bug rather than being a no-op.
		doAnswer(i -> { ((Runnable) i.getArgument(0)).run(); return null; }).when(clientThread).invokeLater(any(Runnable.class));
	}

	private ScriptPreFired root(int script, Object... arguments) {
		ScriptEvent event = mock(ScriptEvent.class);
		Object[] args = new Object[arguments.length + 1];
		args[0] = script;
		System.arraycopy(arguments, 0, args, 1, arguments.length);
		when(event.getArguments()).thenReturn(args);
		ScriptPreFired pre = new ScriptPreFired(script);
		pre.setScriptEvent(event);
		return pre;
	}

	private ScriptPreFired draw(int script) { return root(script, 0, -1, -1, -1, -1, 471, 0); }
	private void receive() { events.post(draw(7797)); }

	@Test public void ignoresDeferredHeaderPairingWithoutPageResponse() {
		Widget header = mock(Widget.class), title = mock(Widget.class), pb = mock(Widget.class);
		when(title.getText()).thenReturn("Alchemical Hydra");
		when(pb.getText()).thenReturn("Personal Best: 0:19.80");
		when(header.getDynamicChildren()).thenReturn(new Widget[] { title, pb });
		when(client.getWidget(621, 20)).thenReturn(header);
		events.post(new ScriptPostFired(ScriptID.COLLECTION_DRAW_LIST));
		assertTrue("An unbound widget redraw must not record a PB", saved.isEmpty());
	}

	@Test public void receivesRawHydraTicksFromBothPageDrawScripts() {
		for (int script : new int[] {7797, 2730}) {
			events.post(draw(script));
			assertEquals(66.6, (Double) capture.sync().get("alchemical hydra"), .00001);
		}
		verify(client, never()).getWidget(anyInt(), anyInt());
	}

	@Test public void searchPageResponseResolvesItsExplicitStruct() {
		events.post(root(4900, 0, 539, 0));
		assertEquals(66.6, (Double) capture.sync().get("alchemical hydra"), .00001);
	}

	@Test public void doesNotRequireTicksToChangeBetweenBosses() {
		receive();
		when(page.getStringValue(689)).thenReturn("Amoxliatl");
		receive();
		assertEquals(capture.sync().get("alchemical hydra"), capture.sync().get("amoxliatl"));
	}

	@Test public void rejectsClientResizeAndNestedDraws() {
		ScriptPreFired resize = draw(7797);
		when(resize.getScriptEvent().getSource()).thenReturn(mock(Widget.class));
		events.post(resize);
		events.post(new ScriptPreFired(2731));
		assertTrue(saved.isEmpty());
	}

	@Test public void rejectsCategoryAndTabMismatches() {
		when(client.getVarbitValue(VarbitID.COLLECTION_LAST_CATEGORY)).thenReturn(1);
		receive();
		when(client.getVarbitValue(VarbitID.COLLECTION_LAST_CATEGORY)).thenReturn(0);
		when(client.getVarbitValue(VarbitID.COLLECTION_LAST_TAB)).thenReturn(1);
		receive();
		assertTrue(saved.isEmpty());
	}

	@Test public void rejectsUnrelatedStructsAndUnsupportedPages() {
		events.post(root(4900, 0, 4652, 0));
		when(page.getStringValue(689)).thenReturn("Barrows Chests");
		receive();
		assertTrue(saved.isEmpty());
	}

	@Test public void doomWithNoDelvesClearsPreviouslySavedTime() {
		when(page.getStringValue(689)).thenReturn("Doom of Mokhaiotl");
		receive();
		assertFalse(saved.isEmpty());
		when(client.getVarpValue(VarPlayerID.COLLECTION_CATEGORY_COUNT)).thenReturn(0);
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(68);
		receive();
		assertTrue(capture.sync().isEmpty());
	}

	@Test public void missingPbClearsPreviouslySavedTime() {
		receive();
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(0);
		receive();
		assertTrue(capture.sync().isEmpty());
	}

	@Test public void gauntletTimesUseTheirOwnCountersAndPbSlots() {
		when(page.getStringValue(689)).thenReturn("The Gauntlet");
		when(client.getVarpValue(VarPlayerID.COLLECTION_CATEGORY_COUNT)).thenReturn(100);
		when(client.getVarpValue(VarPlayerID.COLLECTION_CATEGORY_COUNT2)).thenReturn(10);
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(400);
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT_2)).thenReturn(600);
		receive();
		assertEquals(240.0, (Double) capture.sync().get("gauntlet"), .00001);
		assertEquals(360.0, (Double) capture.sync().get("corrupted gauntlet"), .00001);
		when(client.getVarpValue(VarPlayerID.COLLECTION_CATEGORY_COUNT2)).thenReturn(0);
		receive();
		assertFalse(capture.sync().containsKey("gauntlet"));
		assertTrue(capture.sync().containsKey("corrupted gauntlet"));
	}

	@Test public void ignoresOtherPlayersPohLogsAndMissingProfiles() {
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(1);
		receive();
		when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(0);
		when(config.getRSProfileKey()).thenReturn(null);
		receive();
		assertTrue(saved.isEmpty());
	}

	@Test public void ignoresMalformedScriptArgumentsAndLoggedOutState() {
		events.post(root(7797, 0));
		events.post(root(4900, "wrong", 539, 0));
		events.post(root(4900, 0, 539, -1));
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		receive();
		assertTrue(saved.isEmpty());
	}

	@Test public void doesNotUploadLegacyUnverifiedCache() {
		saved.put("profile-a:revalclanclogpb:alchemical hydra", "19.8");
		saved.put("profile-a:revalclanclogpb:doom of mokhaiotl", "41.0");
		assertEquals(Collections.emptyMap(), capture.sync());
		receive();
		assertEquals(Collections.singletonMap("alchemical hydra", 66.6), capture.sync());
	}

	@Test public void pageSwitchCannotAssignAmoxliatlTicksToHydra() {
		StructComposition amoxliatl = mock(StructComposition.class);
		when(amoxliatl.getStringValue(689)).thenReturn("Amoxliatl");
		when(client.getStructComposition(1016)).thenReturn(amoxliatl);
		when(categories.getIntValue(1)).thenReturn(1016);
		when(client.getVarbitValue(VarbitID.COLLECTION_LAST_CATEGORY)).thenReturn(1);
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(33);
		events.post(root(2730, 0, -1, -1, -1, -1, 471, 1));
		// A Hydra redraw while selected-page state/PB still belong to Amoxliatl.
		receive();
		assertFalse(capture.sync().containsKey("alchemical hydra"));
		// The actual Hydra response supplies Hydra's identity and raw PB.
		when(client.getVarbitValue(VarbitID.COLLECTION_LAST_CATEGORY)).thenReturn(0);
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(111);
		receive();
		assertEquals(19.8, (Double) capture.sync().get("amoxliatl"), .00001);
		assertEquals(66.6, (Double) capture.sync().get("alchemical hydra"), .00001);
	}

	@Test public void cacheIsIsolatedAcrossAccountSwitches() {
		receive();
		when(config.getRSProfileKey()).thenReturn("profile-b");
		assertTrue(capture.sync().isEmpty());
		when(client.getVarpValue(VarPlayerID.COLLECTION_PERSONAL_BEST_TRANSMIT)).thenReturn(230);
		receive();
		assertEquals(138.0, (Double) capture.sync().get("alchemical hydra"), .00001);
		when(config.getRSProfileKey()).thenReturn("profile-a");
		assertEquals(66.6, (Double) capture.sync().get("alchemical hydra"), .00001);
	}

	@Test public void missingCacheEntriesFailClosed() {
		when(client.getEnum(2102)).thenReturn(null);
		receive();
		when(client.getEnum(2102)).thenReturn(tabs);
		when(categories.getIntValue(0)).thenReturn(-1);
		receive();
		assertTrue(saved.isEmpty());
	}

	@Test public void mapsColosseumPageToSolHeredit() {
		when(page.getStringValue(689)).thenReturn("Fortis Colosseum");
		receive();
		assertTrue(capture.sync().containsKey("sol heredit"));
	}
}

package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static com.revalclan.util.RaidPartyTracker.*;

@RunWith(MockitoJUnitRunner.class)
public class RaidRunTrackerTest {
	@Mock private Client client;
	@Mock private RaidDeathTracker deaths;
	@InjectMocks private RaidRunTracker tracker;
	private int tick = 100;

	@Before public void setup() {
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getTickCount()).thenAnswer(invocation -> tick);
	}

	private void tick() { tick++; tracker.onGameTick(); }
	private Map<?, ?> data(String source) {
		Map<String, Object> data = new HashMap<>();
		tracker.forSource(source).addTo(data);
		return (Map<?, ?>) data.get("raid");
	}
	private void complete(String source, int count) {
		tracker.onChatMessage("Your completed " + source + " count is: " + count + ".");
	}

	@Test public void toaKeepsStartingSizeAndLevelAndFreezesTheFinalServerTickForRewards() {
		when(deaths.isInside(TOMBS_OF_AMASCUT)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0)).thenReturn(2);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0 + 1)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(500);
		tracker.onChatMessage("You enter the Tombs of Amascut...");
		tick();
		tick += 20;
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0 + 1)).thenReturn(0);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(460);
		complete("Tombs of Amascut: Expert Mode", 42);
		RaidRunTracker.Run reward = tracker.forSource(TOMBS_OF_AMASCUT);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(450);
		tick();
		Map<?, ?> raid = data(TOMBS_OF_AMASCUT);
		assertEquals(2, raid.get("partySizeAtStart"));
		assertEquals(1, raid.get("partySize"));
		assertEquals(500, raid.get("invocationLevelAtStart"));
		assertEquals(450, raid.get("invocationLevel"));
		assertEquals("expert", raid.get("mode"));
		assertEquals(42, raid.get("completionCount"));
		when(deaths.isInside(TOMBS_OF_AMASCUT)).thenReturn(false);
		lenient().when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(0);
		tick();
		assertEquals(450, data(TOMBS_OF_AMASCUT).get("invocationLevel"));
		tracker.onChatMessage("You enter the Tombs of Amascut...");
		assertNotSame(reward, tracker.forSource(TOMBS_OF_AMASCUT));
		Map<String, Object> old = new HashMap<>();
		reward.addTo(old);
		assertEquals(42, ((Map<?, ?>) old.get("raid")).get("completionCount"));
	}

	@Test public void coxReadsActualPlayersSeparatelyFromItsTotalScale() {
		when(deaths.isInside(CHAMBERS_OF_XERIC)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE)).thenReturn(3);
		when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED)).thenReturn(5);
		when(client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE)).thenReturn(1);
		tracker.onChatMessage("The raid has begun!");
		tick();
		complete("Chambers of Xeric Challenge Mode", 12);
		assertEquals(3, data(CHAMBERS_OF_XERIC).get("partySize"));
		assertEquals(5, data(CHAMBERS_OF_XERIC).get("scale"));
		assertEquals("challenge", data(CHAMBERS_OF_XERIC).get("mode"));
	}

	@Test public void zeroInvocationIsKnownButAMidRaidStartIsUnknown() {
		when(deaths.isInside(TOMBS_OF_AMASCUT)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0)).thenReturn(1);
		tracker.onChatMessage("You enter the Tombs of Amascut...");
		tick();
		assertEquals(0, data(TOMBS_OF_AMASCUT).get("invocationLevelAtStart"));
		tracker.reset();
		tick();
		assertFalse(data(TOMBS_OF_AMASCUT).containsKey("invocationLevelAtStart"));
		assertFalse(data(TOMBS_OF_AMASCUT).containsKey("partySizeAtStart"));
		assertEquals(0, data(TOMBS_OF_AMASCUT).get("invocationLevel"));
	}

	@Test public void nativeStartupValuesCanPopulateBeforeTheStartingWindowCloses() {
		when(deaths.isInside(TOMBS_OF_AMASCUT)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0)).thenReturn(1);
		tracker.onChatMessage("You enter the Tombs of Amascut...");
		tick();
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(300);
		tick();
		assertEquals(300, data(TOMBS_OF_AMASCUT).get("invocationLevelAtStart"));
	}

	@Test public void tobEntryAndNormalKeepTheActualSoloCount() {
		when(deaths.isInside(THEATRE_OF_BLOOD)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_P0)).thenReturn(3);
		tracker.onChatMessage("You enter the Theatre of Blood...");
		tick();
		complete(THEATRE_OF_BLOOD, 8);
		assertEquals(1, data(THEATRE_OF_BLOOD).get("partySize"));
		assertEquals(3, data(THEATRE_OF_BLOOD).get("scale"));
		tracker.onChatMessage("You enter the Theatre of Blood...");
		tick();
		complete("Theatre of Blood: Entry Mode", 1);
		assertEquals(1, data(THEATRE_OF_BLOOD).get("scale"));
	}

	@Test public void archiveCountsDeadMembersButRejectsExternalSpectators() {
		when(deaths.isInside(FRACTURED_ARCHIVE)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P0)).thenReturn(3);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_SPECTATE_MODE)).thenReturn(1);
		tracker.onChatMessage(RaidDeathTracker.ARCHIVE_START_MESSAGE);
		tick();
		complete(FRACTURED_ARCHIVE, 9);
		assertEquals(1, data(FRACTURED_ARCHIVE).get("partySize"));
		assertEquals(2, data(FRACTURED_ARCHIVE).get("scale"));
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_SPECTATE_MODE)).thenReturn(2);
		assertNull(tracker.forSource(FRACTURED_ARCHIVE));
		tracker.reset();
		tick();
		assertNull(tracker.forSource(FRACTURED_ARCHIVE));
	}

	@Test public void resetForgetsAnOutsideChestAndAnAbandonedRun() {
		when(deaths.isInside(THEATRE_OF_BLOOD)).thenReturn(true);
		tick();
		when(deaths.isInside(THEATRE_OF_BLOOD)).thenReturn(false);
		for (int i = 0; i < 4; i++) tick();
		assertNull(tracker.forSource(THEATRE_OF_BLOOD));
		complete(THEATRE_OF_BLOOD, 9);
		assertNotNull(tracker.forSource(THEATRE_OF_BLOOD));
		assertFalse(data(THEATRE_OF_BLOOD).containsKey("partySize"));
		tracker.reset();
		assertNull(tracker.forSource(THEATRE_OF_BLOOD));
	}
}

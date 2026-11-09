package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static com.revalclan.util.RaidPartyTracker.*;

@RunWith(MockitoJUnitRunner.class)
public class RaidSnapshotTest {
	@Mock private Client client;
	@Mock private RaidDeathTracker deaths;
	private RaidPartyTracker tracker;
	private int tick = 100;

	@Before public void setup() {
		tracker = com.google.inject.Guice.createInjector(binder -> {
			binder.bind(Client.class).toInstance(client);
			binder.bind(RaidDeathTracker.class).toInstance(deaths);
		}).getInstance(RaidPartyTracker.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getTickCount()).thenAnswer(invocation -> tick);
	}

	private void chat(String message) { tracker.onChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, message); }
	private void tick() { tick++; tracker.onGameTick(); }
	private Map<?, ?> data(String source) {
		Map<String, Object> data = new HashMap<>();
		tracker.snapshotFor(source).addTo(data);
		return (Map<?, ?>) data.get("raid");
	}
	private void complete(String source, int count) {
		chat("Your completed " + source + " count is: " + count + ".");
	}

	@Test public void toaKeepsStartingSizeAndLevelAndFreezesTheFinalServerTickForRewards() {
		when(deaths.isInside(TOMBS_OF_AMASCUT)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0)).thenReturn(2);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0 + 1)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(500);
		chat("You enter the Tombs of Amascut...");
		tick();
		tick += 20;
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0 + 1)).thenReturn(0);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(460);
		complete("Tombs of Amascut: Expert Mode", 42);
		RaidPartyTracker.Snapshot reward = tracker.snapshotFor(TOMBS_OF_AMASCUT);
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
		chat("You enter the Tombs of Amascut...");
		assertNotSame(reward, tracker.snapshotFor(TOMBS_OF_AMASCUT));
		Map<String, Object> old = new HashMap<>();
		reward.addTo(old);
		assertEquals(42, ((Map<?, ?>) old.get("raid")).get("completionCount"));
	}

	@Test public void coxReadsActualPlayersSeparatelyFromItsTotalScale() {
		when(deaths.isInside(CHAMBERS_OF_XERIC)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE)).thenReturn(3);
		when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED)).thenReturn(5);
		when(client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE)).thenReturn(1);
		chat("The raid has begun!");
		tick();
		complete("Chambers of Xeric Challenge Mode", 12);
		assertEquals(3, data(CHAMBERS_OF_XERIC).get("partySize"));
		assertEquals(5, data(CHAMBERS_OF_XERIC).get("scale"));
		assertEquals("challenge", data(CHAMBERS_OF_XERIC).get("mode"));
	}

	@Test public void zeroInvocationIsKnownButAMidRaidStartIsUnknown() {
		when(deaths.isInside(TOMBS_OF_AMASCUT)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0)).thenReturn(1);
		chat("You enter the Tombs of Amascut...");
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
		chat("You enter the Tombs of Amascut...");
		tick();
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(300);
		tick();
		assertEquals(300, data(TOMBS_OF_AMASCUT).get("invocationLevelAtStart"));
	}

	@Test public void tobEntryAndNormalKeepTheActualSoloCount() {
		when(deaths.isInside(THEATRE_OF_BLOOD)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_P0)).thenReturn(3);
		chat("You enter the Theatre of Blood...");
		tick();
		complete(THEATRE_OF_BLOOD, 8);
		assertEquals(1, data(THEATRE_OF_BLOOD).get("partySize"));
		assertEquals(3, data(THEATRE_OF_BLOOD).get("scale"));
		chat("You enter the Theatre of Blood...");
		tick();
		complete("Theatre of Blood: Entry Mode", 1);
		assertEquals(1, data(THEATRE_OF_BLOOD).get("scale"));
	}

	@Test public void archiveCountsDeadMembersButRejectsExternalSpectators() {
		when(deaths.isInside(FRACTURED_ARCHIVE)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P0)).thenReturn(3);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_SPECTATE_MODE)).thenReturn(1);
		chat(RaidDeathTracker.ARCHIVE_START_MESSAGE);
		tick();
		complete(FRACTURED_ARCHIVE, 9);
		assertEquals(1, data(FRACTURED_ARCHIVE).get("partySize"));
		assertEquals(2, data(FRACTURED_ARCHIVE).get("scale"));
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_SPECTATE_MODE)).thenReturn(2);
		assertNull(tracker.snapshotFor(FRACTURED_ARCHIVE));
		tracker.reset();
		tick();
		assertNull(tracker.snapshotFor(FRACTURED_ARCHIVE));
	}

	@Test public void resetForgetsAnOutsideChestAndAnAbandonedRun() {
		when(deaths.isInside(THEATRE_OF_BLOOD)).thenReturn(true);
		tick();
		when(deaths.isInside(THEATRE_OF_BLOOD)).thenReturn(false);
		for (int i = 0; i < 4; i++) tick();
		assertNull(tracker.snapshotFor(THEATRE_OF_BLOOD));
		complete(THEATRE_OF_BLOOD, 9);
		assertNotNull(tracker.snapshotFor(THEATRE_OF_BLOOD));
		assertFalse(data(THEATRE_OF_BLOOD).containsKey("partySize"));
		tracker.reset();
		assertNull(tracker.snapshotFor(THEATRE_OF_BLOOD));
	}

	@Test public void wardensAndCountMessagesEnrichTheSameRetainedSnapshot() {
		when(deaths.isInside(TOMBS_OF_AMASCUT)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(500);
		chat("You enter the Tombs of Amascut...");
		tick();
		tick += 20;
		chat("Challenge complete: The Wardens.");
		RaidPartyTracker.Snapshot snapshot = tracker.snapshotFor(TOMBS_OF_AMASCUT);
		assertFalse(data(TOMBS_OF_AMASCUT).containsKey("completionCount"));
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(450);
		complete("Tombs of Amascut: Expert Mode", 42);
		tick();
		assertSame(snapshot, tracker.snapshotFor(TOMBS_OF_AMASCUT));
		assertEquals(42, data(TOMBS_OF_AMASCUT).get("completionCount"));
		assertEquals(450, data(TOMBS_OF_AMASCUT).get("invocationLevel"));
	}

	@Test public void leavingACompletedRaidAllowsPartialObservationOfAnotherRaid() {
		when(deaths.isInside(CHAMBERS_OF_XERIC)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE)).thenReturn(3);
		chat("The raid has begun!");
		tick();
		complete(CHAMBERS_OF_XERIC, 100);
		tick();
		RaidPartyTracker.Snapshot first = tracker.snapshotFor(CHAMBERS_OF_XERIC);
		when(deaths.isInside(CHAMBERS_OF_XERIC)).thenReturn(false);
		for (int i = 0; i < 4; i++) tick();
		when(deaths.isInside(THEATRE_OF_BLOOD)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_P0)).thenReturn(1);
		tick();
		assertEquals(1, data(THEATRE_OF_BLOOD).get("partySize"));
		assertFalse(data(THEATRE_OF_BLOOD).containsKey("partySizeAtStart"));
		assertSame(first, tracker.snapshotFor(CHAMBERS_OF_XERIC));
	}

	@Test public void realDeathReaderKeepsFinalTickDeathsOnTheSharedSnapshot() {
		tracker = com.google.inject.Guice.createInjector(binder -> binder.bind(Client.class).toInstance(client))
			.getInstance(RaidPartyTracker.class);
		net.runelite.api.Player local = mock(net.runelite.api.Player.class);
		when(client.getLocalPlayer()).thenReturn(local);
		when(local.getName()).thenReturn("Raider");
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(2);
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_P0)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_P1)).thenReturn(1);
		when(client.getVarcStrValue(330)).thenReturn("Raider");
		when(client.getVarcStrValue(331)).thenReturn("Guest");
		chat("You enter the Theatre of Blood...");
		tick();
		chat("Guest has died. Death count: 1.");
		complete(THEATRE_OF_BLOOD, 42);
		RaidPartyTracker.Snapshot snapshot = tracker.snapshotFor(THEATRE_OF_BLOOD);
		chat("You have died. Death count: 2.");
		tick();
		Map<String, Object> data = new HashMap<>();
		snapshot.addTo(data);
		Map<?, ?> party = (Map<?, ?>) data.get("party");
		java.util.List<?> members = (java.util.List<?>) party.get("members");
		assertEquals(1, ((Map<?, ?>) members.get(0)).get("deaths"));
		assertEquals(1, ((Map<?, ?>) members.get(1)).get("deaths"));
		chat("Guest has died. Death count: 3.");
		tick();
		Map<String, Object> later = new HashMap<>();
		snapshot.addTo(later);
		assertEquals(data, later);
		tracker.onGameStateChanged(GameState.CONNECTION_LOST);
		assertNull(tracker.snapshotFor(THEATRE_OF_BLOOD));
	}

	@Test public void archiveRosterAndNumbersShareCompletionAndObservationGapReset() {
		net.runelite.api.WorldView world = mock(net.runelite.api.WorldView.class);
		net.runelite.api.Player local = mock(net.runelite.api.Player.class);
		when(client.getTopLevelWorldView()).thenReturn(world);
		when(world.isInstance()).thenReturn(true);
		when(client.getLocalPlayer()).thenReturn(local);
		when(local.getName()).thenReturn("Raider");
		when(deaths.isInside(FRACTURED_ARCHIVE)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_PARTYSTATUS)).thenReturn(2);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P0)).thenReturn(3);
		when(client.getVarcStrValue(1573)).thenReturn("Raider");
		chat(RaidDeathTracker.ARCHIVE_START_MESSAGE);
		tick();
		complete(FRACTURED_ARCHIVE, 9);
		tick();
		RaidPartyTracker.Snapshot snapshot = tracker.snapshotFor(FRACTURED_ARCHIVE);
		Map<String, Object> data = new HashMap<>();
		snapshot.addTo(data);
		Map<?, ?> party = (Map<?, ?>) data.get("party");
		assertEquals("completion", party.get("origin"));
		assertEquals(true, party.get("rosterComplete"));
		assertEquals(1, party.get("size"));
		assertEquals(1, ((Map<?, ?>) data.get("raid")).get("partySize"));
		tracker.onGameStateChanged(GameState.HOPPING);
		assertNull(tracker.snapshotFor(FRACTURED_ARCHIVE));
	}

	@Test public void archiveRewardExcludesNamesLeftInEmptySlotsFromThePreviousParty() {
		tracker = com.google.inject.Guice.createInjector(binder -> binder.bind(Client.class).toInstance(client))
			.getInstance(RaidPartyTracker.class);
		net.runelite.api.WorldView world = mock(net.runelite.api.WorldView.class);
		net.runelite.api.Player local = mock(net.runelite.api.Player.class);
		when(client.getTopLevelWorldView()).thenReturn(world);
		when(world.isInstance()).thenReturn(true);
		when(client.getLocalPlayer()).thenReturn(local);
		when(local.getName()).thenReturn("Raider");
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_PARTYSTATUS)).thenReturn(2);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P0)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P0 + 1)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P0 + 2)).thenReturn(1);
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0)).thenReturn("Raider");
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0 + 1)).thenReturn("Guest");
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0 + 2)).thenReturn("Old raider");
		chat(RaidDeathTracker.ARCHIVE_START_MESSAGE);
		tick();
		complete(FRACTURED_ARCHIVE, 8);
		tick();

		// The next run is smaller, but its empty slot still holds an old name.
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P0 + 2)).thenReturn(0);
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0 + 1)).thenReturn("");
		chat(RaidDeathTracker.ARCHIVE_START_MESSAGE);
		tick();
		Map<String, Object> partial = new HashMap<>();
		tracker.snapshotFor(FRACTURED_ARCHIVE).addTo(partial);
		assertEquals(java.util.List.of("Raider"), partial.get("partyMembers"));
		assertEquals(false, ((Map<?, ?>) partial.get("party")).get("rosterComplete"));
		when(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0 + 1)).thenReturn("New guest");
		tick();
		// A real member who leaves after being observed must still be retained.
		when(client.getVarbitValue(VarbitID.TFA_CLIENT_P0 + 1)).thenReturn(0);
		tick();
		complete(FRACTURED_ARCHIVE, 9);
		tick();
		Map<String, Object> reward = new HashMap<>();
		tracker.snapshotFor(FRACTURED_ARCHIVE).addTo(reward);
		assertEquals(java.util.List.of("Raider", "New guest"), reward.get("partyMembers"));
		Map<?, ?> party = (Map<?, ?>) reward.get("party");
		assertEquals(2, party.get("size"));
		assertEquals(true, party.get("rosterComplete"));
		java.util.List<?> members = (java.util.List<?>) party.get("members");
		assertEquals(2, members.size());
		assertEquals("New guest", ((Map<?, ?>) members.get(1)).get("name"));
		assertEquals(0, ((Map<?, ?>) members.get(1)).get("deaths"));
	}

	@Test public void equalCountsInDifferentModesAreDifferentCompletions() {
		when(deaths.isInside(THEATRE_OF_BLOOD)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_P0)).thenReturn(1);
		chat("You enter the Theatre of Blood...");
		tick();
		complete(THEATRE_OF_BLOOD, 42);
		tick();
		RaidPartyTracker.Snapshot normal = tracker.snapshotFor(THEATRE_OF_BLOOD);
		complete("Theatre of Blood: Hard Mode", 42);
		tick();
		assertNotSame(normal, tracker.snapshotFor(THEATRE_OF_BLOOD));
		assertEquals("hard", data(THEATRE_OF_BLOOD).get("mode"));
		assertFalse(data(THEATRE_OF_BLOOD).containsKey("partySizeAtStart"));
	}
}

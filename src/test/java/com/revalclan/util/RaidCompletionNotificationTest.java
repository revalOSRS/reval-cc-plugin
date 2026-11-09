package com.revalclan.util;

import com.revalclan.notifiers.KillCountNotifier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RaidCompletionNotificationTest {
	@Mock private Client client;
	@Mock private RaidDeathTracker deaths;
	@Mock private RaidPartyTracker parties;
	@Mock private RaidRunTracker raidRuns;
	@InjectMocks private RaidRunTracker runs;
	private final List<Map<String, Object>> sent = new ArrayList<>();
	@InjectMocks private KillCountNotifier notifier = new KillCountNotifier() {
		@Override public boolean isEnabled() { return true; }
		@Override protected void sendNotification(Map<String, Object> data) { sent.add(data); }
	};

	@Test public void completionWithoutLootOrNamesCarriesTheRunAndLogoutClearsItsBuffer() {
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(deaths.isInside(RaidPartyTracker.CHAMBERS_OF_XERIC)).thenReturn(true);
		when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE)).thenReturn(3);
		when(client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED)).thenReturn(5);
		when(raidRuns.forSource(anyString())).thenAnswer(invocation -> runs.forSource(invocation.getArgument(0)));
		runs.onChatMessage("The raid has begun!");
		runs.onGameTick();
		String completion = "Your completed Chambers of Xeric count is: 100.";
		runs.onChatMessage(completion);
		notifier.onChatMessage(completion);
		runs.onGameTick();
		notifier.onTick();
		assertEquals(1, sent.size());
		Map<?, ?> raid = (Map<?, ?>) sent.get(0).get("raid");
		assertEquals(3, raid.get("partySize"));
		assertEquals(5, raid.get("scale"));
		assertEquals(100, raid.get("completionCount"));
		assertFalse(sent.get(0).containsKey("party"));
		notifier.onChatMessage(completion);
		notifier.onChatMessage("Your Zulrah kill count is: 100.");
		notifier.onTick();
		assertEquals("Zulrah", sent.get(1).get("boss"));
		assertFalse(sent.get(1).containsKey("raid"));
		notifier.onChatMessage(completion);
		notifier.reset();
		notifier.onTick();
		assertEquals(2, sent.size());
	}
}

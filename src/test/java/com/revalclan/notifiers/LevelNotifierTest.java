package com.revalclan.notifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LevelNotifierTest {
	private final List<Map<String, Object>> sent = new ArrayList<>();
	private final LevelNotifier notifier = new LevelNotifier() {
		@Override public boolean isEnabled() { return true; }
		@Override protected void sendNotification(Map<String, Object> data) { sent.add(data); }
	};

	private void stat(int level, int xp) {
		notifier.onStatChanged(new StatChanged(Skill.ATTACK, xp, level, level));
	}

	@Test public void loadingArenaStatsDoesNotEmitALevelUp() {
		notifier.client = mock(Client.class);
		when(notifier.client.getGameState()).thenReturn(GameState.LOGGED_IN);
		stat(90, 5_346_332);
		when(notifier.client.getGameState()).thenReturn(GameState.HOPPING);
		stat(99, 13_034_431);
		assertTrue("World loading is not a level up", sent.isEmpty());
	}

	@Test public void changingWorldDoesNotCompareStatsWithThePreviousWorld() {
		notifier.client = mock(Client.class);
		when(notifier.client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(notifier.client.getWorld()).thenReturn(625);
		stat(90, 5_346_332);
		when(notifier.client.getWorld()).thenReturn(578);
		stat(99, 13_034_431);
		assertTrue("The first stat on a new world is a baseline", sent.isEmpty());
		when(notifier.client.getWorld()).thenReturn(625);
		stat(90, 5_346_332);
		stat(91, 5_902_831);
		assertEquals(1, sent.size());
		assertEquals(91, sent.get(0).get("level"));
	}
}

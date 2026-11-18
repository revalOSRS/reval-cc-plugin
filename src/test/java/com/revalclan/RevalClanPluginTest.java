package com.revalclan;

import com.revalclan.session.SessionTracker;
import com.revalclan.notifiers.DiaryNotifier;
import com.revalclan.notifiers.LevelNotifier;
import com.revalclan.util.RaidPartyTracker;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import java.util.concurrent.CompletableFuture;
import net.runelite.client.events.ClientShutdown;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RevalClanPluginTest {
	@Mock private SessionTracker sessions;
	@Mock private DiaryNotifier diaryNotifier;
	@Mock private LevelNotifier levelNotifier;
	@Mock private RaidPartyTracker raidPartyTracker;
	@InjectMocks private RevalClanPlugin plugin;

	@Test public void gracefulExitRegistersThePersistenceBarrier() {
		CompletableFuture<Void> flushed = new CompletableFuture<>();
		when(sessions.flush()).thenReturn(flushed);
		ClientShutdown event = new ClientShutdown();
		plugin.onClientShutdown(event);
		assertTrue(event.getTasks().contains(flushed));
	}

	@Test public void pluginHttpPolicySharesRuneLiteResources() {
		OkHttpClient shared = new OkHttpClient();
		OkHttpClient derived = plugin.provideHttpClient(shared);
		assertSame(shared.dispatcher(), derived.dispatcher());
		assertSame(shared.connectionPool(), derived.connectionPool());
		assertEquals(30_000, derived.callTimeoutMillis());
	}

	@Test public void hoppingClearsLevelBaselinesBeforeNewWorldStatsArrive() {
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.HOPPING);
		plugin.onGameStateChanged(event);
		verify(levelNotifier).reset();
	}
}

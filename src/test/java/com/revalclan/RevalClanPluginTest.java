package com.revalclan;

import com.revalclan.session.SessionTracker;
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
}

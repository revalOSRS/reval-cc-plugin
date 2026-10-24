package com.revalclan.notifiers;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.revalclan.RevalClanConfig;
import com.revalclan.session.SessionState;
import com.revalclan.util.*;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SessionStateTest {
	private static class NewBufferedNotifier extends BaseNotifier {
		private Object buffer = new Object();
		@Override public boolean isEnabled() { return true; }
		@Override protected String getEventType() { return "TEST"; }
		@Override public void reset() { buffer = null; }
	}

	@Test public void anInjectedNotifierJoinsLogoutCleanupWithoutEditingThePlugin() {
		SessionState sessions = new SessionState();
		com.google.inject.Injector injector = Guice.createInjector(new AbstractModule() {
			@Override protected void configure() {
				bind(SessionState.class).toInstance(sessions);
				bind(Client.class).toInstance(mock(Client.class));
				bind(WebhookService.class).toInstance(mock(WebhookService.class));
				bind(RevalClanConfig.class).toInstance(mock(RevalClanConfig.class));
				bind(EventFilterManager.class).toInstance(new EventFilterManager(mock(okhttp3.OkHttpClient.class), new Gson()));
				bind(ClanMembership.class).toInstance(mock(ClanMembership.class));
				bind(ItemManager.class).toInstance(mock(ItemManager.class));
				bind(ScreenshotService.class).toProvider(() -> mock(ScreenshotService.class));
			}
		});
		NewBufferedNotifier notifier = injector.getInstance(NewBufferedNotifier.class);
		assertNotNull(notifier.buffer);
		sessions.reset();
		assertNull(notifier.buffer);
	}
}

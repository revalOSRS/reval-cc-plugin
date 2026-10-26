package com.revalclan.clanbrowser;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.revalclan.auth.LinkTokenManager;
import com.revalclan.util.WebhookService;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

/** RuneLite builds the plugin with Guice: the clan browser has to get the very same link manager the rest of the plugin uses. */
public class BrowserApiInjectionTest {
	private static Object field(Object owner, String name) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	@Test
	public void theBrowserApiAndTheWebhookShareOneLinkManager() throws Exception {
		Injector injector = Guice.createInjector(binder -> {
			binder.bind(OkHttpClient.class).toInstance(new OkHttpClient());
			binder.bind(Gson.class).toInstance(new Gson());
			binder.bind(ConfigManager.class).toInstance(mock(ConfigManager.class));
			binder.bind(ScheduledExecutorService.class).toInstance(mock(ScheduledExecutorService.class));
		});

		LinkTokenManager tokens = injector.getInstance(LinkTokenManager.class);

		assertSame(tokens, field(injector.getInstance(BrowserApi.class), "tokens"));
		assertSame(tokens, field(injector.getInstance(WebhookService.class), "tokens"));
	}
}

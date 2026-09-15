package com.revalclan.util;

import com.google.gson.Gson;
import com.revalclan.RevalClanConfig;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.announcements.AnnouncementsResponse;
import com.revalclan.api.notifications.NotificationsResponse;
import net.runelite.api.Client;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class AnnouncementPollingTest {
	@Test public void pollsEveryFifteenMinutesAndIgnoresPreviousLoginResponses() throws Exception {
		AnnouncementService service = new AnnouncementService();
		FakeApi api = new FakeApi();
		inject(service, "revalApiService", api);
		inject(service, "client", Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) -> 42L));
		inject(service, "config", Proxy.newProxyInstance(RevalClanConfig.class.getClassLoader(), new Class<?>[]{RevalClanConfig.class},
			(proxy, method, args) -> true));
		ticks(service, 4); assertEquals(0, api.responses.size());
		ticks(service, 1); assertEquals(1, api.responses.size());
		NotificationsResponse empty = new Gson().fromJson("{\"status\":\"success\",\"data\":{\"notifications\":[]}}", NotificationsResponse.class);
		api.responses.get(0).accept(empty);
		ticks(service, 1499); assertEquals(1, api.responses.size());
		ticks(service, 1); assertEquals(2, api.responses.size());
		ticks(service, 2000); assertEquals(2, api.responses.size()); // No overlapping request.
		service.reset(); ticks(service, 5); assertEquals(3, api.responses.size());
		api.responses.get(1).accept(empty); // Old callback must not complete the new request.
		ticks(service, 2000); assertEquals(3, api.responses.size());
		api.errors.get(2).accept(new Exception("Unavailable"));
		ticks(service, 1499); assertEquals(3, api.responses.size());
		ticks(service, 1); assertEquals(4, api.responses.size());
	}
	private static void ticks(AnnouncementService service, int count) { for (int i = 0; i < count; i++) service.onGameTick(); }
	private static void inject(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true); field.set(target, value);
	}
	private static class FakeApi extends RevalApiService {
		final List<Consumer<NotificationsResponse>> responses = new ArrayList<>();
		final List<Consumer<Exception>> errors = new ArrayList<>();
		FakeApi() { super(null, new Gson()); }
		@Override public void fetchAnnouncements(Consumer<AnnouncementsResponse> ok, Consumer<Exception> error) {}
		@Override public void fetchNotifications(long account, Consumer<NotificationsResponse> ok, Consumer<Exception> error) {
			responses.add(ok); errors.add(error);
		}
	}
}

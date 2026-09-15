package com.revalclan.util;

import com.google.gson.Gson;
import okhttp3.*;
import org.junit.Test;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class EventFilterRefreshTest {
	@Test public void versionsDailyFallbackRetriesAndRelogUseOnlySuccessfullyAppliedFilters() throws Exception {
		BlockingQueue<Pending> requests = new LinkedBlockingQueue<>();
		OkHttpClient http = new OkHttpClient() {
			@Override public Call newCall(Request request) {
				Pending pending = new Pending(request);
				return (Call) java.lang.reflect.Proxy.newProxyInstance(Call.class.getClassLoader(), new Class<?>[]{Call.class},
					(proxy, method, args) -> {
						if (method.getName().equals("enqueue")) { pending.callback = (Callback) args[0]; requests.add(pending); }
						return null;
					});
			}
		};
		try {
			EventFilterManager filters = new EventFilterManager();
			inject(filters, "httpClient", http); inject(filters, "gson", new Gson());
			filters.resetSession(); filters.fetchFiltersAsync();
			Pending first = take(requests);
			filters.onServerVersion("v1"); assertTrue(requests.isEmpty());
			first.complete(200, "v1", 111);
			assertTrue(filters.getFilters().getLootWhitelist().contains(111));
			filters.onServerVersion("v1");
			for (int i = 0; i < 143999; i++) filters.onGameTick();
			assertTrue(requests.isEmpty());
			filters.onGameTick();
			take(requests).complete(500, null, 0);
			for (int i = 0; i < 99; i++) filters.onGameTick();
			assertTrue(requests.isEmpty());
			filters.onGameTick();
			Pending old = take(requests);
			filters.resetSession(); filters.fetchFiltersAsync();
			Pending current = take(requests);
			old.complete(200, "stale", 999);
			assertFalse(filters.getFilters().getLootWhitelist().contains(999));
			current.complete(200, "v2", 222);
			filters.onServerVersion("v2"); assertTrue(requests.isEmpty());
			filters.onServerVersion("v3");
			take(requests).complete(200, "v3", 333);
			assertTrue(filters.getFilters().getLootWhitelist().contains(333));
		} finally {
			http.dispatcher().executorService().shutdownNow();
			http.connectionPool().evictAll();
		}
	}
	private static <T> T take(BlockingQueue<T> queue) throws Exception {
		T value = queue.poll(5, TimeUnit.SECONDS);
		assertNotNull("Expected HTTP activity", value);
		return value;
	}
	private static void inject(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true); field.set(target, value);
	}
	private static class Pending {
		final Request request;
		Callback callback;
		Pending(Request request) { this.request = request; }
		void complete(int status, String version, int item) throws IOException {
			Response.Builder builder = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
				.code(status).message("test").body(ResponseBody.create(MediaType.parse("application/json"),
					"{\"loot\":{\"whitelist\":[" + item + "]}}"));
			if (version != null) builder.header("X-Reval-Filters-Version", version);
			callback.onResponse(null, builder.build());
		}
	}
}

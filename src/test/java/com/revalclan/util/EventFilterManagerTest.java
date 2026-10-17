package com.revalclan.util;

import com.google.gson.Gson;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EventFilterManagerTest {
	@Test public void aSessionResetCancelsTheOldFetchAndAllowsANewOne() throws Exception {
		OkHttpClient http = mock(OkHttpClient.class);
		Call call = mock(Call.class);
		when(http.newCall(any(Request.class))).thenReturn(call);
		when(call.timeout()).thenReturn(new okio.Timeout());
		EventFilterManager filters = new EventFilterManager(http, new Gson());
		filters.fetchFiltersAsync();
		filters.resetSession();
		verify(call).cancel();
		filters.fetchFiltersAsync();
		verify(http, times(2)).newCall(any(Request.class));
	}
}

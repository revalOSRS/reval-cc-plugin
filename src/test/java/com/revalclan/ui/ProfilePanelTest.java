package com.revalclan.ui;

import com.google.gson.Gson;
import com.revalclan.RevalClanConfig;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.UIAssetLoader;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ProfilePanelTest {
	@Test public void aLateAccountResponseCannotRestoreALoggedOutProfile() throws Exception {
		RevalApiService api = mock(RevalApiService.class);
		ProfilePanel[] holder = new ProfilePanel[1];
		Consumer<AccountResponse.AccountData> loaded = mock(Consumer.class);
		SwingUtilities.invokeAndWait(() -> {
			holder[0] = new ProfilePanel();
			holder[0].init(api, mock(Client.class), mock(UIAssetLoader.class), mock(RevalClanConfig.class),
				mock(ItemManager.class), mock(ClanRankIconResolver.class));
			holder[0].setOnAccountLoaded(loaded);
			holder[0].loadAccount(123);
		});
		ArgumentCaptor<Consumer<AccountResponse>> callback = ArgumentCaptor.forClass(Consumer.class);
		verify(api).fetchAccount(eq(123L), callback.capture(), any());
		SwingUtilities.invokeAndWait(holder[0]::onLoggedOut);
		AccountResponse response = new AccountResponse();
		AccountResponse.AccountData data = new AccountResponse.AccountData();
		data.setOsrsAccount(new AccountResponse.OsrsAccount());
		response.setData(data);
		callback.getValue().accept(response);
		SwingUtilities.invokeAndWait(() -> assertFalse(holder[0].isAccountLoaded()));
		verifyNoInteractions(loaded);
	}

	@Test public void disposedPanelsCannotStartNewRequests() throws Exception {
		RevalApiService api = mock(RevalApiService.class);
		SwingUtilities.invokeAndWait(() -> {
			ProfilePanel panel = new ProfilePanel();
			panel.init(api, mock(Client.class), mock(UIAssetLoader.class), mock(RevalClanConfig.class),
				mock(ItemManager.class), mock(ClanRankIconResolver.class));
			panel.shutDown();
			panel.refresh();
			panel.loadCurrentAccount();
			panel.loadAccount(123);
			panel.loadAccountById(456);
		});
		verifyNoInteractions(api);
	}

	@Test public void refreshingTwiceKeepsThePendingAccountRequestAndLoadsItsResponse() throws Exception {
		OkHttpClient http = mock(OkHttpClient.class);
		Call account = mock(Call.class);
		Call points = mock(Call.class);
		when(http.newCall(any(Request.class))).thenAnswer(invocation -> {
			Request request = invocation.getArgument(0);
			return request.url().queryParameter("accountHash") == null ? points : account;
		});
		RevalApiService api = new RevalApiService(http, new Gson());
		Client client = mock(Client.class);
		when(client.getAccountHash()).thenReturn(123L);
		ProfilePanel[] holder = new ProfilePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			holder[0] = new ProfilePanel();
			holder[0].init(api, client, mock(UIAssetLoader.class), mock(RevalClanConfig.class),
				mock(ItemManager.class), mock(ClanRankIconResolver.class));
			holder[0].refresh();
			holder[0].refresh();
		});

		verify(account, never()).cancel();
		ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
		verify(account).enqueue(callback.capture());
		callback.getValue().onResponse(account, new Response.Builder()
			.request(new Request.Builder().url("https://example.invalid").build())
			.protocol(Protocol.HTTP_1_1).code(200).message("OK")
			.body(ResponseBody.create(null, "{\"status\":\"success\",\"data\":{\"osrsAccount\":{}}}")).build());
		SwingUtilities.invokeAndWait(() -> assertTrue(holder[0].isAccountLoaded()));
		verify(http, times(2)).newCall(any(Request.class));
		SwingUtilities.invokeAndWait(holder[0]::shutDown);
		api.shutDown();
	}
}

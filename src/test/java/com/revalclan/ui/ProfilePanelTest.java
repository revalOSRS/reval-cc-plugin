package com.revalclan.ui;

import com.revalclan.RevalClanConfig;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.UIAssetLoader;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
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
			panel.loadCurrentAccount();
			panel.loadAccount(123);
			panel.loadAccountById(456);
		});
		verifyNoInteractions(api);
	}
}

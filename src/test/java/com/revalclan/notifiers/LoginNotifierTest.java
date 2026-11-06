package com.revalclan.notifiers;

import com.google.gson.JsonObject;
import com.revalclan.PlayerDataCollector;
import com.revalclan.util.SyncStateManager;
import com.revalclan.util.WebhookService;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LoginNotifierTest {
	@Mock private Client client;
	@Mock private com.revalclan.util.ClanMembership membership;
	@Mock private PlayerDataCollector collector;
	@Mock private SyncStateManager sync;
	@Mock private WebhookService webhooks;
	@InjectMocks private LoginNotifier login;

	@Test public void failedLoginDoesNotTriggerChangeFetches() {
		when(membership.isMember()).thenReturn(true);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		when(collector.collectBoundaryData()).thenReturn(new HashMap<>());
		when(sync.ackHandler(anyLong())).thenReturn(response -> {});
		when(webhooks.sendDataAsync(anyMap())).thenReturn(CompletableFuture.failedFuture(new IOException("offline")));
		Consumer<JsonObject> changes = mock(Consumer.class);
		login.onLogin(changes);
		verifyNoInteractions(changes);
	}
}

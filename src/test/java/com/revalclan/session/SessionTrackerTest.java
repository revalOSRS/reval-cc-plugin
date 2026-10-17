package com.revalclan.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.util.ClanMembership;
import com.revalclan.util.WebhookService;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SessionTrackerTest {
	private final SessionStore store = mock(SessionStore.class);
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final WebhookService webhooks = mock(WebhookService.class);
	private final Queue<Runnable> tasks = new ArrayDeque<>();
	private final CompletableFuture<List<SessionStore.PersistedSession>> read = new CompletableFuture<>();
	private final SessionTracker tracker = new SessionTracker(client, clientThread, new Gson(), webhooks, store, mock(ClanMembership.class));

	@Before public void setUp() {
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		when(store.readAll()).thenReturn(read);
		when(webhooks.sendDataAsync(anyMap())).thenReturn(new CompletableFuture<>());
		doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(clientThread).invokeLater(any(Runnable.class));
	}

	@Test public void aHopDoesNotCancelLoginRecovery() {
		tracker.startSession();
		tracker.recoverPersistedSessions(123);
		tracker.cutForHop();
		read.complete(List.of(previous()));
		drain();
		verify(webhooks).sendDataAsync(argThat(payload -> "SESSION_SUMMARY".equals(payload.get("eventType"))));
	}

	@Test public void shutdownCancelsRecoveryEvenIfItIsAlreadyQueuedOnTheClientThread() {
		tracker.recoverPersistedSessions(123);
		read.complete(List.of(previous()));
		tracker.shutDown();
		drain();
		verifyNoInteractions(webhooks);
	}

	@Test public void aFailedSummaryCanBeRecoveredAgain() {
		CompletableFuture<JsonObject> delivery = new CompletableFuture<>();
		when(webhooks.sendDataAsync(anyMap())).thenReturn(delivery);
		read.complete(List.of(previous()));
		tracker.recoverPersistedSessions(0);
		drain();
		delivery.completeExceptionally(new IOException("offline"));
		drain();
		tracker.recoverPersistedSessions(0);
		drain();
		verify(webhooks, times(2)).sendDataAsync(anyMap());
		verify(store, never()).delete(anyString());
	}

	@Test public void onlyDefinitiveAcknowledgementsDeleteTheLocalCopy() {
		JsonObject response = new JsonObject();
		response.addProperty("sessionStored", "duplicate");
		tracker.confirmDelivered("acked", response);
		drain();
		verify(store).delete("acked");
	}

	private SessionStore.PersistedSession previous() {
		SessionStore.PersistedSession session = SessionStoreTest.session("previous");
		session.member = true;
		return session;
	}
	private void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
}

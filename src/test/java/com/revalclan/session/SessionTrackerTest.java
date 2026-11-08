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
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SessionTrackerTest {
	private final SessionStore store = mock(SessionStore.class);
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final WebhookService webhooks = mock(WebhookService.class);
	private final ClanMembership membership = mock(ClanMembership.class);
	private final Queue<Runnable> tasks = new ArrayDeque<>();
	private final CompletableFuture<List<SessionStore.PersistedSession>> read = new CompletableFuture<>();
	private final SessionTracker tracker = new SessionTracker(client, clientThread, new Gson(), webhooks, store, membership);

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

	@Test public void heartbeatAndLaterPersistenceUseTheCurrentPlayerName() {
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getName()).thenReturn("Before rename");
		when(membership.isMember()).thenReturn(true);
		tracker.startSession();
		when(player.getName()).thenReturn("After rename");

		for (int tick = 0; tick < 1000; tick++) tracker.onGameTick();
		verify(webhooks).sendDataAsync(argThat(payload ->
			"SESSION_HEARTBEAT".equals(payload.get("eventType"))
				&& "After rename".equals(payload.get("username"))));
		tracker.finalizeSession();
		org.mockito.ArgumentCaptor<SessionStore.PersistedSession> saved =
			org.mockito.ArgumentCaptor.forClass(SessionStore.PersistedSession.class);
		verify(store, atLeastOnce()).write(saved.capture());
		assertEquals("After rename", saved.getValue().username);
	}

	@Test public void failedHeartbeatDoesNotTriggerChangeFetches() {
		when(membership.isMember()).thenReturn(true);
		when(webhooks.sendDataAsync(anyMap())).thenReturn(CompletableFuture.failedFuture(new IOException("offline")));
		Consumer<JsonObject> changes = mock(Consumer.class);
		tracker.setOnHeartbeatResponse(changes);
		tracker.startSession();

		for (int tick = 0; tick < 1000; tick++) tracker.onGameTick();
		drain();
		verify(webhooks).sendDataAsync(anyMap());
		verifyNoInteractions(changes);
	}

	@Test public void aQueuedHeartbeatReplyCannotRefreshTheNextLogin() {
		when(membership.isMember()).thenReturn(true);
		CompletableFuture<JsonObject> delivery = new CompletableFuture<>();
		when(webhooks.sendDataAsync(anyMap())).thenReturn(delivery);
		Consumer<JsonObject> changes = mock(Consumer.class);
		tracker.setOnHeartbeatResponse(changes);
		tracker.startSession();
		for (int tick = 0; tick < 1000; tick++) tracker.onGameTick();
		delivery.complete(new JsonObject());
		tracker.reset();
		tracker.startSession();

		drain();
		verifyNoInteractions(changes);
	}

	private SessionStore.PersistedSession previous() {
		SessionStore.PersistedSession session = SessionStoreTest.session("previous");
		session.member = true;
		return session;
	}
	private void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
}

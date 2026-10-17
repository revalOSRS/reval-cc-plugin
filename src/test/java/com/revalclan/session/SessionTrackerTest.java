package com.revalclan.session;

import com.revalclan.TestSupport;
import com.revalclan.util.WebhookService;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import java.util.Queue;
import java.util.function.Consumer;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SessionTrackerTest {
	@Test public void recoveryAfterResetDoesNotSendStaleSnapshots() throws Exception {
		SessionTracker tracker = new SessionTracker();
		SessionStore store = mock(SessionStore.class);
		ClientThread clientThread = mock(ClientThread.class);
		WebhookService webhooks = mock(WebhookService.class);
		Queue<Runnable> tasks = new ArrayDeque<>();
		doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(clientThread).invokeLater(any(Runnable.class));
		TestSupport.inject(tracker, "store", store);
		TestSupport.inject(tracker, "clientThread", clientThread);
		TestSupport.inject(tracker, "webhookService", webhooks);
		tracker.recoverPersistedSessions(123);
		ArgumentCaptor<Consumer<List<SessionStore.PersistedSession>>> recovered = ArgumentCaptor.forClass(Consumer.class);
		verify(store).readAll(recovered.capture());
		tracker.reset();
		SessionStore.PersistedSession session = SessionStoreTest.session("old");
		session.member = true;
		recovered.getValue().accept(List.of(session));
		while (!tasks.isEmpty()) tasks.remove().run();
		verifyNoInteractions(webhooks);
	}

	@Test public void aFailedSummaryCanBeRecoveredAgain() throws Exception {
		SessionTracker tracker = new SessionTracker();
		SessionStore store = mock(SessionStore.class);
		ClientThread clientThread = mock(ClientThread.class);
		WebhookService webhooks = mock(WebhookService.class);
		Queue<Runnable> tasks = new ArrayDeque<>();
		doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(clientThread).invokeLater(any(Runnable.class));
		SessionStore.PersistedSession session = SessionStoreTest.session("retry");
		session.member = true;
		doAnswer(invocation -> {
			invocation.<Consumer<List<SessionStore.PersistedSession>>>getArgument(0).accept(List.of(session));
			return null;
		}).when(store).readAll(any());
		TestSupport.inject(tracker, "store", store);
		TestSupport.inject(tracker, "clientThread", clientThread);
		TestSupport.inject(tracker, "webhookService", webhooks);
		tracker.recoverPersistedSessions(0);
		while (!tasks.isEmpty()) tasks.remove().run();
		ArgumentCaptor<Consumer<JsonObject>> result = ArgumentCaptor.forClass(Consumer.class);
		verify(webhooks).sendDataAsync(anyMap(), result.capture());
		result.getValue().accept(null);
		while (!tasks.isEmpty()) tasks.remove().run();
		tracker.recoverPersistedSessions(0);
		while (!tasks.isEmpty()) tasks.remove().run();
		verify(webhooks, times(2)).sendDataAsync(anyMap(), any());
		verify(store, never()).delete(anyString());
	}
}

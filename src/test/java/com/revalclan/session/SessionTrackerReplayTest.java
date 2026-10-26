package com.revalclan.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.util.ClanMembership;
import com.revalclan.util.WebhookService;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A recorded session is replayed under the account it was recorded for, and only counts as sent once it really went out. */
public class SessionTrackerReplayTest {
	private static final long RECORDED_FOR = 1_111_111_111_111L;

	private SessionTracker tracker;
	private WebhookService webhook;
	private SessionStore store;

	@Before
	public void setUp() throws Exception {
		webhook = mock(WebhookService.class);
		store = mock(SessionStore.class);
		JsonObject summary = new JsonObject();
		summary.addProperty("sessionId", "session-1");
		summary.addProperty("startedAt", System.currentTimeMillis());
		summary.addProperty("endReason", "logout");
		SessionStore.PersistedSession persisted = new SessionStore.PersistedSession();
		persisted.accountHash = RECORDED_FOR;
		persisted.username = "Zezima";
		persisted.world = 301;
		persisted.member = true;
		persisted.summary = summary;
		when(store.readAll()).thenReturn(Collections.singletonList(persisted));

		// the real one hands work to the game thread; here that thread is the caller
		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(call -> {
			((Runnable) call.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));

		tracker = new SessionTracker();
		inject("client", mock(Client.class));
		inject("clientThread", clientThread);
		inject("gson", new Gson());
		inject("webhookService", webhook);
		inject("store", store);
		inject("membership", mock(ClanMembership.class));
	}

	private void inject(String name, Object value) throws Exception {
		Field field = SessionTracker.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(tracker, value);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void theReplayCarriesTheAccountTheSessionWasRecordedFor() {
		when(webhook.sendDataTracked(anyMap(), any(), any())).thenReturn(true);

		tracker.recoverPersistedSessions(0);

		ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
		verify(webhook).sendDataTracked(payload.capture(), any(), any());
		assertEquals("SESSION_SUMMARY", payload.getValue().get("eventType"));
		assertEquals("the webhook picks the token from this, not from whoever is logged in",
			RECORDED_FOR, payload.getValue().get("accountHash"));
	}

	private static SessionStore.PersistedSession session(String id, long accountHash, boolean member) {
		JsonObject summary = new JsonObject();
		summary.addProperty("sessionId", id);
		summary.addProperty("startedAt", System.currentTimeMillis());
		SessionStore.PersistedSession persisted = new SessionStore.PersistedSession();
		persisted.accountHash = accountHash;
		persisted.member = member;
		persisted.summary = summary;
		return persisted;
	}

	@Test
	public void aSessionThatNeverLearnedItsAccountIsNeverSentForAnyone() {
		when(store.readAll()).thenReturn(java.util.Arrays.asList(session("none-0", 0, true), session("none-1", -1, true),
			session("none-0-proven", 0, false)));
		when(webhook.sendDataTracked(anyMap(), any(), any())).thenReturn(true);

		tracker.recoverPersistedSessions(0);
		tracker.recoverPersistedSessions(-1);
		tracker.recoverPersistedSessions(RECORDED_FOR);

		verify(webhook, times(0)).sendDataTracked(anyMap(), any(), any());
	}

	@Test
	public void aSessionOfAnAccountWithANegativeHashIsReplayedLikeAnyOther() {
		long negative = -4_611_686_018_427_387_904L;
		when(store.readAll()).thenReturn(Collections.singletonList(session("negative", negative, true)));
		when(webhook.sendDataTracked(anyMap(), any(), any())).thenReturn(true);

		tracker.recoverPersistedSessions(0);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
		verify(webhook).sendDataTracked(payload.capture(), any(), any());
		assertEquals(negative, payload.getValue().get("accountHash"));
	}

	@Test
	public void aNotYetProvenSessionWaitsForItsOwnAccountAndNobodyElses() {
		long negative = -4_611_686_018_427_387_904L;
		when(store.readAll()).thenReturn(Collections.singletonList(session("waiting", negative, false)));
		when(webhook.sendDataTracked(anyMap(), any(), any())).thenReturn(true);

		tracker.recoverPersistedSessions(RECORDED_FOR);
		verify(webhook, times(0)).sendDataTracked(anyMap(), any(), any());

		tracker.recoverPersistedSessions(negative);
		verify(webhook, times(1)).sendDataTracked(anyMap(), any(), any());
	}

	@Test
	public void aSessionThatCouldNotBeSentIsOfferedAgainNotMarkedInFlightForever() {
		when(webhook.sendDataTracked(anyMap(), any(), any())).thenReturn(false);

		tracker.recoverPersistedSessions(0);
		tracker.recoverPersistedSessions(0);

		verify(webhook, times(2)).sendDataTracked(anyMap(), any(), any());
	}

	@Test
	public void aSessionThatWasSentIsNotSentTwiceWhileItsAnswerIsPending() {
		when(webhook.sendDataTracked(anyMap(), any(), any())).thenReturn(true);

		tracker.recoverPersistedSessions(0);
		tracker.recoverPersistedSessions(0);

		verify(webhook, times(1)).sendDataTracked(anyMap(), any(), any());
	}

	// ------------------------------------------------------------------ how a send ends

	/** Starts the replay and returns what the webhook was handed to call back when the send ends. */
	@SuppressWarnings("unchecked")
	private Ends replayOnce() {
		ArgumentCaptor<Consumer<JsonObject>> answered = ArgumentCaptor.forClass(Consumer.class);
		ArgumentCaptor<Runnable> failed = ArgumentCaptor.forClass(Runnable.class);
		tracker.recoverPersistedSessions(0);
		verify(webhook, org.mockito.Mockito.atLeastOnce()).sendDataTracked(anyMap(), answered.capture(), failed.capture());
		return new Ends(answered.getValue(), failed.getValue());
	}

	private static final class Ends {
		final Consumer<JsonObject> answered;
		final Runnable failed;

		Ends(Consumer<JsonObject> answered, Runnable failed) {
			this.answered = answered;
			this.failed = failed;
		}
	}

	private static JsonObject stored(String outcome) {
		JsonObject response = new JsonObject();
		if (outcome != null) response.addProperty("sessionStored", outcome);
		return response;
	}

	@Test
	public void aSendThatEndedWithoutAnAnswerFreesTheSessionForTheNextReplayAndKeepsItsFile() {
		when(webhook.sendDataTracked(anyMap(), any(), any())).thenReturn(true);

		Ends first = replayOnce();
		tracker.recoverPersistedSessions(0);
		verify(webhook, times(1)).sendDataTracked(anyMap(), any(), any()); // still pending: no concurrent duplicate

		first.failed.run();
		tracker.recoverPersistedSessions(0);

		verify(webhook, times(2)).sendDataTracked(anyMap(), any(), any());
		verify(store, never()).delete(anyString());
	}

	@Test
	public void aDefinitiveAnswerDeletesTheFileAndAnythingElseKeepsItForAReplay() {
		when(webhook.sendDataTracked(anyMap(), any(), any())).thenReturn(true);

		Ends first = replayOnce();
		first.answered.accept(stored(null)); // an older backend that does not say
		verify(store, never()).delete(anyString());
		tracker.recoverPersistedSessions(0);
		verify(webhook, times(2)).sendDataTracked(anyMap(), any(), any());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Consumer<JsonObject>> answered = ArgumentCaptor.forClass(Consumer.class);
		verify(webhook, times(2)).sendDataTracked(anyMap(), answered.capture(), any());
		answered.getValue().accept(stored("stored"));

		verify(store).delete("session-1");
	}
}

package com.revalclan.session;

import com.google.gson.JsonObject;
import com.revalclan.util.ClanMembership;
import com.revalclan.util.WebhookService;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A recorded session through the real webhook path: it stays on disk and in flight exactly as long as it takes
 * to end, and a failed send, whatever the reason, can be replayed in the same client run.
 */
public class SessionDeliveryTest {
	private static final String STORED = "{\"status\":\"success\",\"sessionStored\":\"stored\"}";
	private static final String INVALID_CREDENTIAL = "{\"status\":\"error\",\"code\":\"invalid_plugin_credential\",\"message\":\"x\"}";
	private static final String OTHER_401 = "{\"status\":\"error\",\"code\":\"plugin_link_required\",\"message\":\"x\"}";

	private Rig rig;
	private SessionTracker tracker;
	private SessionStore store;
	private final List<Runnable> scheduled = new ArrayList<>();
	private final List<String> deleted = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		rig = new Rig();
		rig.link(ACCOUNT_A, TOKEN_A);
		rig.tokens.onAccount(ACCOUNT_A);

		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
			scheduled.add(call.getArgument(0));
			return null;
		});
		WebhookService webhook = new WebhookService(rig.client, rig.gson, rig.tokens, executor);

		JsonObject summary = new JsonObject();
		summary.addProperty("sessionId", "session-1");
		summary.addProperty("startedAt", System.currentTimeMillis());
		summary.addProperty("endReason", "logout");
		SessionStore.PersistedSession persisted = new SessionStore.PersistedSession();
		persisted.accountHash = ACCOUNT_A;
		persisted.username = "Zezima";
		persisted.world = 301;
		persisted.member = true;
		persisted.summary = summary;
		store = mock(SessionStore.class);
		when(store.readAll()).thenAnswer(call -> deleted.contains("session-1") ? new ArrayList<>() : java.util.Collections.singletonList(persisted));
		doAnswer(call -> {
			deleted.add(call.getArgument(0));
			return null;
		}).when(store).delete(any());

		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(call -> {
			((Runnable) call.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));

		tracker = new SessionTracker();
		inject("client", mock(Client.class));
		inject("clientThread", clientThread);
		inject("gson", rig.gson);
		inject("webhookService", webhook);
		inject("store", store);
		inject("membership", mock(ClanMembership.class));
	}

	@After
	public void tearDown() throws Exception {
		rig.close();
	}

	private void inject(String name, Object value) throws Exception {
		Field field = SessionTracker.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(tracker, value);
	}

	private void replay() {
		tracker.recoverPersistedSessions(0);
	}

	private void runScheduledRetry() {
		assertFalse("a retry is waiting", scheduled.isEmpty());
		scheduled.remove(0).run();
	}

	private static String eventId(Request request) {
		return request.header(WebhookService.EVENT_ID_HEADER);
	}

	@Test
	public void aServerErrorIsRetriedOnceAndThenTheSessionCanBeReplayedAgainAndIsStored() {
		rig.http.responder = request -> json(request, 503, "{}");

		replay();
		assertEquals(1, rig.http.requests.size());
		replay();
		assertEquals("the webhook's own retry is pending: no second replay on top of it", 1, rig.http.requests.size());

		runScheduledRetry();
		assertEquals(2, rig.http.requests.size());
		assertEquals("the retry keeps the event id", eventId(rig.http.requests.get(0)), eventId(rig.http.requests.get(1)));
		assertTrue(deleted.isEmpty());

		rig.http.responder = request -> json(request, 200, STORED);
		replay();
		assertEquals("the failed send is over, so the same run may replay it", 3, rig.http.requests.size());
		assertNotEquals("a replay is a new event", eventId(rig.http.requests.get(1)), eventId(rig.http.requests.get(2)));
		assertEquals(java.util.Collections.singletonList("session-1"), deleted);

		replay();
		assertEquals("stored, so nothing is left to replay", 3, rig.http.requests.size());
	}

	@Test
	public void aNetworkFailureEndsTheSendAfterItsRetryAndTheFileIsKept() {
		rig.http.responder = request -> {
			throw new IOException("offline");
		};

		replay();
		replay();
		assertEquals(1, rig.http.requests.size());
		runScheduledRetry();
		assertEquals(2, rig.http.requests.size());

		rig.http.responder = request -> json(request, 200, STORED);
		replay();
		assertEquals(3, rig.http.requests.size());
		assertEquals(java.util.Collections.singletonList("session-1"), deleted);
	}

	@Test
	public void aRevokedLinkEndsTheSendKeepsTheFileAndAfterRelinkingTheSameRunDeliversIt() {
		rig.http.responder = request -> json(request, 401, INVALID_CREDENTIAL);

		replay();
		assertEquals(1, rig.http.requests.size());
		assertFalse("the server revoked it, so it is forgotten", rig.hasCredential(ACCOUNT_A));
		assertTrue("the session itself is untouched", deleted.isEmpty());

		replay();
		assertEquals("without a link nothing is sent at all", 1, rig.http.requests.size());

		rig.link(ACCOUNT_A, TOKEN_NEW); // the player pastes a new Link code
		rig.http.responder = request -> json(request, 200, STORED);
		replay();

		assertEquals(2, rig.http.requests.size());
		assertEquals("Bearer " + TOKEN_NEW, rig.http.last().header("Authorization"));
		assertEquals(java.util.Collections.singletonList("session-1"), deleted);
	}

	@Test
	public void aRefusalThatDoesNotRevokeTheLinkAlsoLeavesTheSessionReplayable() {
		rig.http.responder = request -> json(request, 401, OTHER_401);

		replay();
		assertTrue("an unknown 401 keeps the link", rig.hasCredential(ACCOUNT_A));
		assertTrue(deleted.isEmpty());

		rig.http.responder = request -> json(request, 200, STORED);
		replay();

		assertEquals(2, rig.http.requests.size());
		assertEquals(java.util.Collections.singletonList("session-1"), deleted);
	}

	@Test
	public void aRefusedEnvelopeEndsTheSendAfterItsSingleRetry() {
		rig.http.responder = request -> json(request, 400, "{\"status\":\"error\",\"code\":\"invalid_event_envelope\",\"message\":\"x\"}");

		replay();
		runScheduledRetry();
		assertEquals(2, rig.http.requests.size());
		assertTrue("one retry only", scheduled.isEmpty());

		rig.http.responder = request -> json(request, 200, STORED);
		replay();
		assertEquals(3, rig.http.requests.size());
		assertEquals(java.util.Collections.singletonList("session-1"), deleted);
	}

	@Test
	public void anAnswerWithoutTheStoredReceiptKeepsTheFileForAReplay() {
		rig.http.responder = request -> json(request, 200, "{\"status\":\"success\"}");

		replay();
		assertTrue("no receipt, no delete", deleted.isEmpty());
		replay();

		assertEquals(2, rig.http.requests.size());
	}

	@Test
	public void aSuccessAnswerThatIsNotJsonIsAFailureNotAHang() {
		rig.http.responder = request -> json(request, 200, "<html>proxy</html>");

		replay();
		replay();

		assertEquals("ended without a usable answer, so it was offered again", 2, rig.http.requests.size());
		assertTrue(deleted.isEmpty());
	}
}

package com.revalclan.session;

import com.google.gson.Gson;
import com.revalclan.auth.LinkTokenManager;
import com.revalclan.util.ClanMembership;
import com.revalclan.util.WebhookService;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The account hash can be unset when LOGGED_IN fires. It is filled in on a later tick, only once, and only for the
 * player the session started as. Signed 64-bit hashes are routinely negative: only 0 and -1 mean "not known yet".
 */
public class SessionTrackerAccountHashTest {
	private static final long NEGATIVE = -4_611_686_018_427_387_904L;
	private static final long OTHER_NEGATIVE = -9_000_000_000_000_000_001L;
	private static final long POSITIVE = 1_111_111_111_111L;

	private SessionTracker tracker;
	private SessionStore store;
	private Client client;
	private long hash;
	private String name;
	private GameState state;

	@Before
	public void setUp() throws Exception {
		hash = -1;
		name = "Alpha";
		state = GameState.LOGGED_IN;

		store = mock(SessionStore.class);
		client = mock(Client.class);
		Player player = mock(Player.class);
		when(player.getName()).thenAnswer(call -> name);
		when(client.getLocalPlayer()).thenAnswer(call -> name == null ? null : player);
		when(client.getAccountHash()).thenAnswer(call -> hash);
		when(client.getGameState()).thenAnswer(call -> state);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));

		tracker = new SessionTracker();
		inject("client", client);
		inject("clientThread", mock(ClientThread.class));
		inject("gson", new Gson());
		inject("webhookService", mock(WebhookService.class));
		inject("store", store);
		inject("membership", mock(ClanMembership.class));
	}

	private void inject(String field, Object value) throws Exception {
		Field f = SessionTracker.class.getDeclaredField(field);
		f.setAccessible(true);
		f.set(tracker, value);
	}

	/** The account hash the session was persisted with when it ended just now. */
	private long persistedHashAfterEnding() {
		tracker.finalizeSession();
		ArgumentCaptor<SessionStore.PersistedSession> written = ArgumentCaptor.forClass(SessionStore.PersistedSession.class);
		verify(store, atLeastOnce()).write(written.capture());
		List<SessionStore.PersistedSession> all = written.getAllValues();
		return all.get(all.size() - 1).accountHash;
	}

	// ------------------------------------------------------------------ filling in an unready hash

	@Test
	public void aHashThatWasNotReadyAtLoginIsFilledInOnTheFirstTickThatHasIt() {
		tracker.startSession();
		tracker.onGameTick();
		tracker.onGameTick(); // still unknown: nothing to fill in

		hash = NEGATIVE; // the client has it now, and it is negative like many real ones
		tracker.onGameTick();

		assertEquals("persisted with the real account, not 0 / -1", NEGATIVE, persistedHashAfterEnding());
	}

	@Test
	public void zeroCountsAsNotReadyJustLikeMinusOne() {
		hash = 0;
		tracker.startSession();
		hash = POSITIVE;
		tracker.onGameTick();

		assertEquals(POSITIVE, persistedHashAfterEnding());
	}

	@Test
	public void aSessionThatNeverLearnsItsHashIsPersistedWithoutOne() {
		tracker.startSession();
		tracker.onGameTick();

		long persisted = persistedHashAfterEnding();

		assertFalse("still unknown, never somebody else's: " + persisted, LinkTokenManager.isValidHash(persisted));
	}

	@Test
	public void aNegativeHashAtLoginIsAnOrdinaryHashAndNeedsNoBackfill() {
		hash = NEGATIVE;
		tracker.startSession();
		tracker.onGameTick();

		assertEquals(NEGATIVE, persistedHashAfterEnding());
	}

	// ------------------------------------------------------------------ never replaced, never somebody else's

	@Test
	public void anAlreadySetHashIsNeverOverwrittenByAnotherAccount() {
		hash = NEGATIVE;
		tracker.startSession();

		// the client now reports a different account (and player) without the session having been closed
		hash = OTHER_NEGATIVE;
		name = "Bravo";
		tracker.onGameTick();
		tracker.onGameTick();

		assertEquals(NEGATIVE, persistedHashAfterEnding());
	}

	@Test
	public void anAlreadySetHashIsNeverReplacedEvenForTheSamePlayerName() {
		hash = NEGATIVE;
		tracker.startSession();

		hash = OTHER_NEGATIVE; // same name, different hash: whatever the client says later, the session keeps what it recorded
		tracker.onGameTick();
		tracker.onGameTick();

		assertEquals(NEGATIVE, persistedHashAfterEnding());
	}

	@Test
	public void aDifferentAccountThatAppearsBeforeTheHashIsKnownIsNotStamped() {
		tracker.startSession(); // Alpha, hash unknown
		tracker.onGameTick();

		name = "Bravo"; // somebody else is in the client by now
		hash = OTHER_NEGATIVE;
		tracker.onGameTick();
		tracker.onGameTick();

		assertFalse("their hash must not land on Alpha's session", LinkTokenManager.isValidHash(persistedHashAfterEnding()));
	}

	@Test
	public void theHashIsNotFilledInWhileNobodyIsLoggedIn() {
		tracker.startSession();
		hash = NEGATIVE;

		state = GameState.LOGIN_SCREEN;
		tracker.onGameTick();
		state = GameState.HOPPING;
		tracker.onGameTick();

		assertFalse(LinkTokenManager.isValidHash(persistedHashAfterEnding()));
	}

	@Test
	public void theHashIsNotFilledInUntilThePlayerIsKnownToBeTheSame() {
		tracker.startSession(); // Alpha
		name = null; // player object not there for a moment
		hash = NEGATIVE;
		tracker.onGameTick();
		tracker.finalizeSession();
		ArgumentCaptor<SessionStore.PersistedSession> written = ArgumentCaptor.forClass(SessionStore.PersistedSession.class);
		verify(store, atLeastOnce()).write(written.capture());

		assertFalse(LinkTokenManager.isValidHash(written.getValue().accountHash));
	}

	@Test
	public void aQuickLogoutAndLoginAsSomeoneElseGivesEachSessionItsOwnAccount() {
		tracker.startSession(); // Alpha, hash not ready
		tracker.onGameTick();
		tracker.finalizeSession(); // logged out before the hash ever arrived

		name = "Bravo";
		hash = OTHER_NEGATIVE;
		tracker.startSession();
		tracker.onGameTick();
		tracker.finalizeSession();

		ArgumentCaptor<SessionStore.PersistedSession> written = ArgumentCaptor.forClass(SessionStore.PersistedSession.class);
		verify(store, atLeastOnce()).write(written.capture());
		List<SessionStore.PersistedSession> all = written.getAllValues();
		assertFalse("Alpha's record stays without a hash", LinkTokenManager.isValidHash(all.get(0).accountHash));
		assertEquals("Alpha", all.get(0).username);
		assertEquals(OTHER_NEGATIVE, all.get(all.size() - 1).accountHash);
		assertEquals("Bravo", all.get(all.size() - 1).username);
	}

	@Test
	public void aNewSessionStartsFreshAfterTheOldOneEnded() {
		hash = NEGATIVE;
		tracker.startSession();
		tracker.finalizeSession();

		hash = -1;
		name = "Bravo";
		tracker.startSession();
		hash = OTHER_NEGATIVE;
		tracker.onGameTick();

		assertEquals("the new session does not inherit the previous hash, and learns its own", OTHER_NEGATIVE, persistedHashAfterEnding());
	}
}

package com.revalclan.api;

import com.revalclan.auth.ApiOrigin;
import com.revalclan.ui.ProfilePanel;
import net.runelite.api.Client;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real API + profile lifecycle; all callbacks and rendering use an isolated EDT. */
public class ProfileRequestLifecycleTest {
    private Rig rig;
    private ProfilePanel panel;

    @Before public void setUp() throws Exception {
        rig = new Rig();
        rig.link(ACCOUNT_A, TOKEN_A);
        rig.link(ACCOUNT_B, TOKEN_B);
        rig.tokens.onAccount(ACCOUNT_A);
        SwingUtilities.invokeAndWait(() -> {
            panel = new ProfilePanel();
            panel.init(new RevalApiService(rig.client, rig.gson, rig.tokens, directClientThread(), ApiOrigin.PRODUCTION),
                mock(Client.class), null, null, null, null);
        });
    }

    @After public void tearDown() throws Exception { rig.close(); }

    @Test public void unlinkedViewersCanOpenPublicLeaderboardProfiles() throws Exception {
        rig.tokens.onLoggedOut();
        rig.tokens.onAccount(ACCOUNT_C);
        rig.http.responder = request -> json(request, 200, request.url().encodedPath().endsWith("/account/456")
            ? "{\"status\":\"success\",\"data\":{\"osrsAccount\":{\"osrsNickname\":\"Member\"}}}"
            : "{\"status\":\"success\"}");
        SwingUtilities.invokeAndWait(() -> panel.loadAccountById(456));
        SwingUtilities.invokeAndWait(() -> {
            try {
                java.lang.reflect.Field current = ProfilePanel.class.getDeclaredField("currentAccount");
                current.setAccessible(true);
                com.revalclan.api.account.AccountResponse.AccountData data =
                    (com.revalclan.api.account.AccountResponse.AccountData) current.get(panel);
                assertNotNull("public profiles do not require a credential", data);
                assertEquals("Member", data.getOsrsAccount().getOsrsNickname());
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        assertTrue(rig.http.requests.stream().allMatch(request -> request.header("Authorization") == null));
    }

    @Test public void logoutDuringAnAccountRequestDoesNotWedgeTheNextLogin() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        rig.http.responder = request -> {
            if (request.url().encodedPath().endsWith("/account") && reads.incrementAndGet() == 1) {
                rig.tokens.onLoggedOut();
                panel.onLoggedOut();
            }
            return json(request, 200, "{\"status\":\"success\"}");
        };
        SwingUtilities.invokeAndWait(() -> panel.loadAccount(ACCOUNT_A));
        SwingUtilities.invokeAndWait(() -> {
            rig.tokens.onAccount(ACCOUNT_B);
            panel.loadAccount(ACCOUNT_B);
        });
        assertEquals("B must be able to load after A's response was dropped", 2, reads.get());
    }

    @Test public void logoutInvalidatesProfileResultsAlreadyQueuedForTheEdt() throws Exception {
        List<String> applied = new ArrayList<>();
        rig.http.responder = request -> json(request, 200, request.url().encodedPath().endsWith("/account")
            ? "{\"status\":\"success\",\"data\":{}}" : "{\"status\":\"success\"}");
        SwingUtilities.invokeAndWait(() -> {
            panel.setOnAccountLoaded(account -> applied.add("account"));
            panel.loadAccount(ACCOUNT_A);
            rig.tokens.onLoggedOut();
            panel.onLoggedOut();
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue("queued work from a logged-out load cannot restore account state", applied.isEmpty());
    }
    @Test public void credentialRejectionInvalidatesAProfileAlreadyQueuedOnTheEdt() throws Exception {
        assertQueuedCredentialInvalidated(() -> rig.tokens.rejected(rig.tokens.grantForEvent(
            okhttp3.HttpUrl.get("https://api.revalosrs.ee/reval-webhook"), ACCOUNT_A)));
    }

    @Test public void credentialExpiryInvalidatesAProfileAlreadyQueuedOnTheEdt() throws Exception {
        assertQueuedCredentialInvalidated(() -> rig.clock.advance(java.time.Duration.ofDays(91)));
    }

    @Test public void relinkingDuringTheSameLoginInvalidatesThePreviousQueuedProfile() throws Exception {
        assertQueuedCredentialInvalidated(() -> rig.relink(ACCOUNT_A, TOKEN_NEW));
    }

    private void assertQueuedCredentialInvalidated(Runnable invalidate) throws Exception {
        List<String> applied = new ArrayList<>();
        rig.http.responder = request -> json(request, 200, request.url().encodedPath().endsWith("/account")
            ? "{\"status\":\"success\",\"data\":{}}" : "{\"status\":\"success\"}");
        SwingUtilities.invokeAndWait(() -> {
            panel.setOnAccountLoaded(account -> applied.add("account"));
            panel.loadAccount(ACCOUNT_A);
            invalidate.run();
        });
        SwingUtilities.invokeAndWait(() -> assertTrue("a queued profile needs its original credential", applied.isEmpty()));
        rig.clock.set(NOW);
        rig.relink(ACCOUNT_A, TOKEN_NEW);
        SwingUtilities.invokeAndWait(() -> panel.loadAccount(ACCOUNT_A));
        SwingUtilities.invokeAndWait(() -> assertEquals("discarding old data must release the loading state", 1, applied.size()));
    }

}

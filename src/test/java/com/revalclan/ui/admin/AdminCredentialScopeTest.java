package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.auth.TestKit.Rig;
import okhttp3.HttpUrl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.time.Duration;
import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;

/** Admin state and delayed UI work retain the actual API credential lifetime. */
public class AdminCredentialScopeTest {
    private Rig rig;
    private AdminManager manager;

    @Before public void setUp() {
        rig = new Rig();
        rig.link(ACCOUNT_A, TOKEN_A);
        rig.tokens.onAccount(ACCOUNT_A);
        RevalApiService api = new RevalApiService(rig.client, rig.gson, rig.tokens, directClientThread());
        manager = new AdminManager(() -> ACCOUNT_A, api::accountScope);
    }

    @After public void close() throws Exception { rig.close(); }

    @Test public void rejectionInvalidatesBothAnAdminSessionAndItsQueuedLogin() {
        invalidationCannotRevive(() -> rig.tokens.rejected(rig.tokens.grantForEvent(
            HttpUrl.get("https://api.revalosrs.ee/reval-webhook"), ACCOUNT_A)));
    }

    @Test public void expiryInvalidatesBothAnAdminSessionAndItsQueuedLogin() {
        invalidationCannotRevive(() -> rig.clock.advance(Duration.ofDays(91)));
    }

    @Test public void sameLoginRelinkingInvalidatesBothAnAdminSessionAndItsQueuedLogin() {
        invalidationCannotRevive(() -> rig.relink(ACCOUNT_A, TOKEN_NEW));
    }

    private void invalidationCannotRevive(Runnable invalidate) {
        AdminManager.Session login = manager.begin();
        assertTrue(manager.complete(login, AdminManagerTest.accepted()));
        AdminManager.Session view = manager.session();
        AdminManager.Session pending = manager.begin();
        invalidate.run();
        assertFalse(view.isCurrent());
        assertFalse(manager.isLoggedIn());
        assertNull(manager.session());
        assertNull(manager.getAuthData());
        assertFalse(manager.complete(pending, AdminManagerTest.accepted()));
        rig.clock.set(NOW);
        rig.relink(ACCOUNT_A, TOKEN_NEW);
        assertFalse("replacement must not revive old admin authority", manager.isLoggedIn());
        assertTrue(manager.complete(manager.begin(), AdminManagerTest.accepted()));
        assertFalse("new authentication does not revive old UI callbacks", view.isCurrent());
    }
}

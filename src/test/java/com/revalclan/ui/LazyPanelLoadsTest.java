package com.revalclan.ui;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;

public class LazyPanelLoadsTest {
    @Test public void loginDoesNotFetchUnopenedPanelsAndOpeningTwiceFetchesOnce() {
        LazyPanelLoads loads = new LazyPanelLoads();
        AtomicInteger requests = new AtomicInteger();
        loads.open("DIARY", true, requests::incrementAndGet);
        loads.onLogin();
        assertEquals(0, requests.get());
        loads.open("DIARY", true, requests::incrementAndGet);
        loads.open("DIARY", true, requests::incrementAndGet);
        assertEquals(1, requests.get());
        loads.open("ACHIEVEMENTS", true, requests::incrementAndGet);
        assertEquals(2, requests.get());
    }

    @Test public void aNewLoginReloadsSelectedPanelsWithoutReusingAnotherAccountState() {
        LazyPanelLoads loads = new LazyPanelLoads();
        AtomicInteger requests = new AtomicInteger();
        loads.onLogin();
        loads.open("DIARY", true, requests::incrementAndGet);
        loads.onLogout();
        loads.open("DIARY", true, requests::incrementAndGet);
        assertEquals(1, requests.get());
        loads.onLogin();
        loads.open("DIARY", true, requests::incrementAndGet);
        assertEquals(2, requests.get());
    }

    @Test public void publicPanelsCanLoadWhileLoggedOut() {
        LazyPanelLoads loads = new LazyPanelLoads();
        AtomicInteger requests = new AtomicInteger();
        loads.open("RANKING", false, requests::incrementAndGet);
        assertEquals(1, requests.get());
    }
    @org.junit.Test
    public void explicitOpenReloadsEveryTimeButNeverWhileLoggedOut() {
        LazyPanelLoads loads = new LazyPanelLoads();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        loads.openEveryTime(true, calls::incrementAndGet);
        org.junit.Assert.assertEquals(0, calls.get());
        loads.onLogin();
        org.junit.Assert.assertEquals(0, calls.get());
        loads.openEveryTime(true, calls::incrementAndGet);
        loads.openEveryTime(true, calls::incrementAndGet);
        org.junit.Assert.assertEquals(2, calls.get());
    }
}

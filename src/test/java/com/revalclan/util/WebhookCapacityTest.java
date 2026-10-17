package com.revalclan.util;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WebhookCapacityTest {
	@Test public void repeatedSaturationWarnsOnceUntilAllWorkDrains() {
		Runnable warning = mock(Runnable.class);
		WebhookCapacity capacity = new WebhookCapacity(warning);
		for (int i = 0; i < 48; i++) assertTrue(capacity.acquire(false));
		for (int i = 0; i < 1000; i++) {
			assertFalse(capacity.acquire(false));
			capacity.release(false);
			assertTrue(capacity.acquire(false));
		}
		verify(warning, times(1)).run();
		for (int i = 0; i < 48; i++) capacity.release(false);
		for (int i = 0; i < 48; i++) assertTrue(capacity.acquire(false));
		assertFalse(capacity.acquire(false));
		verify(warning, times(2)).run();
	}

	@Test public void lifecycleAndSyncEventsUseReservedCapacity() {
		for (String event : new String[] {"LOGIN", "LOGOUT", "SYNC", "LEAGUES_SYNC", "SESSION_SUMMARY", "SESSION_HEARTBEAT"}) {
			assertTrue(event, WebhookCapacity.isCritical(event));
		}
		for (Object event : new Object[] {"CHAT", "EMOTE", "MUSIC", "LOOT", null}) assertFalse(WebhookCapacity.isCritical(event));
	}
}

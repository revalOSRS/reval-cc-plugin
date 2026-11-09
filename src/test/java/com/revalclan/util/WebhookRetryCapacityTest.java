package com.revalclan.util;

import com.revalclan.auth.TestKit.Rig;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static com.revalclan.auth.TestKit.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WebhookRetryCapacityTest {
    @Test public void delayedRetriesRetainTheirSlotsAcrossCancellationAndRestart() throws Exception {
        try (Rig rig = new Rig()) {
            rig.link(ACCOUNT_A, TOKEN_A);
            rig.http.responder = request -> json(request, 503, "{}");
            Queue<Runnable> retries = new ArrayDeque<>();
            ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
            doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
                .when(worker).execute(any(Runnable.class));
            when(worker.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(call -> { retries.add(call.getArgument(0)); return null; });
            WebhookService service = new WebhookService(rig.client, rig.gson, rig.tokens, worker);
            Map<String, Object> event = Map.of("eventType", "SESSION_SUMMARY", "accountHash", ACCOUNT_A);
            for (int i = 0; i < 64; i++) assertFalse(service.sendDataAsync(event).isDone());
            assertEquals(64, retries.size());
            service.shutDown();
            service.startUp();
            for (int i = 0; i < 1000; i++) assertTrue(service.sendDataAsync(event).isCompletedExceptionally());
            assertEquals("cancelled retries still retain their bodies", 64, retries.size());
            while (!retries.isEmpty()) retries.remove().run();
            assertEquals("shutdown must prevent delayed transports", 64, rig.http.requests.size());
            rig.http.responder = request -> json(request, 200, "{}");
            assertNotNull(service.sendDataAsync(event).join());
            assertEquals(65, rig.http.requests.size());
            service.shutDown();
        }
    }
}

package com.revalclan.clanbrowser;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserApiTest {
    @Test public void revisitsUseTheClientCacheAndRefreshExplicitlyBypassesTheServerCache() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        AtomicBoolean forced = new AtomicBoolean();
        server.createContext("/plugin/clan-browser/view", exchange -> {
            int version = calls.incrementAndGet(); forced.set(String.valueOf(exchange.getRequestURI().getQuery()).contains("refresh=true"));
            byte[] body = ("{\"status\":\"success\",\"data\":{\"kind\":\"clog\",\"title\":\"" + version + "\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        BrowserApi api = new BrowserApi(http, new Gson()); BrowserCommand command = BrowserCommand.parse("reval clog vorkath");
        assertEquals("1", read(api, command, -100, false).title);
        assertEquals("1", read(api, command, -100, false).title); assertEquals(1, calls.get());
        assertEquals("2", read(api, command, -100, true).title); assertTrue(forced.get());
        assertEquals("2", read(api, command, -100, false).title); assertEquals(2, calls.get());
        assertEquals("3", read(api, command, -200, false).title); assertFalse(forced.get());
        assertEquals("2", read(api, command, -100, false).title); assertEquals(3, calls.get());
    }
    @Test public void capitalizationAndDefaultFiltersShareOneCacheEntry() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/plugin/clan-browser/view", exchange -> {
            byte[] body = ("{\"status\":\"success\",\"data\":{\"title\":\"" + calls.incrementAndGet() + "\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        BrowserApi api = new BrowserApi(http, new Gson());
        BrowserCommand lower = BrowserCommand.parse("reval clog general-graardor --player valgusruum");
        BrowserCommand display = BrowserCommand.parse("reval clog General Graardor --player Valgusruum");
        assertEquals("1", read(api, lower, 0, false).title);
        assertEquals("1", read(api, display, 0, false).title);
        assertEquals("2", read(api, display, 0, true).title);
        assertEquals("2", read(api, lower, 0, false).title);
        assertEquals("3", read(api, BrowserCommand.parse("reval leaderboard clues"), 0, false).title);
        assertEquals("3", read(api, BrowserCommand.parse("reval leaderboard clues all"), 0, false).title);
        assertEquals("4", read(api, BrowserCommand.parse("reval leaderboard bosses"), 0, false).title);
        assertEquals("4", read(api, BrowserCommand.parse("reval leaderboard bosses all"), 0, false).title);
        assertEquals(4, calls.get());
    }
    private static BrowserModels.View read(BrowserApi api, BrowserCommand command, long account, boolean refresh) throws Exception {
        CountDownLatch done = new CountDownLatch(1); java.util.concurrent.atomic.AtomicReference<BrowserModels.View> result = new java.util.concurrent.atomic.AtomicReference<>();
        api.view(command, account, refresh, data -> { result.set(data); done.countDown(); }, error -> done.countDown());
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertNotNull(result.get()); return result.get();
    }
    private HttpServer server;
    private OkHttpClient http;
    private String previous;
    @Before public void start() throws Exception {
        previous = System.getProperty("reval.browserApi");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.start();
        System.setProperty("reval.browserApi", "http://127.0.0.1:" + server.getAddress().getPort() + "/plugin");
        http = new OkHttpClient();
    }
    @After public void stop() {
        server.stop(0); http.dispatcher().executorService().shutdownNow(); http.connectionPool().evictAll();
        if (previous == null) System.clearProperty("reval.browserApi"); else System.setProperty("reval.browserApi", previous);
    }
    @Test public void readsLargeScoresAndDeliversCallbacksOnTheEventThread() throws Exception {
        AtomicBoolean correctHeaders = new AtomicBoolean();
        server.createContext("/plugin/clan-browser/view", exchange -> {
            correctHeaders.set(exchange.getRequestHeaders().getFirst("User-Agent").startsWith("RuneLite-RevalClan-Plugin"));
            byte[] bytes = "{\"status\":\"success\",\"data\":{\"kind\":\"leaderboard\",\"panels\":[{\"label\":\"Mains\",\"rows\":[{\"player\":\"TwelveChars1\",\"rank\":1,\"value\":4800000000}]}]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        CountDownLatch done = new CountDownLatch(1); AtomicBoolean correct = new AtomicBoolean();
        new BrowserApi(http, new Gson()).view(BrowserCommand.parse("reval leaderboard xp"), 0, view -> {
            correct.set(SwingUtilities.isEventDispatchThread() && view.panels.get(0).rows.get(0).value == 4_800_000_000L); done.countDown();
        }, error -> done.countDown());
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertTrue(correct.get()); assertTrue(correctHeaders.get());
    }
    @Test public void cancellationPreventsLateUiUpdates() throws Exception {
        CountDownLatch received = new CountDownLatch(1), release = new CountDownLatch(1); AtomicBoolean delivered = new AtomicBoolean();
        server.createContext("/plugin/clan-browser/view", exchange -> {
            received.countDown(); try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            byte[] body = "{\"status\":\"success\",\"data\":{\"kind\":\"luck\"}}".getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); } catch (Exception ignored) {} finally { exchange.close(); }
        });
        Call call = new BrowserApi(http, new Gson()).view(BrowserCommand.parse("reval luck vorkath"), 0, view -> delivered.set(true), error -> delivered.set(true));
        assertTrue(received.await(3, TimeUnit.SECONDS)); call.cancel(); release.countDown();
        SwingUtilities.invokeAndWait(() -> {}); assertFalse(delivered.get()); assertTrue(call.isCanceled());
    }
    @Test public void encodesMultiwordSelectionsAsQueryParameters() {
        String url = BrowserApi.endpoint("view", BrowserCommand.parse("reval clog forestry --player \"Love Yourz\"").parameters(0)).toString();
        assertTrue(url.contains("player=Love%20Yourz")); assertTrue(url.contains("category=forestry")); assertFalse(url.contains("accountHash="));
    }
}

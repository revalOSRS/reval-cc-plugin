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

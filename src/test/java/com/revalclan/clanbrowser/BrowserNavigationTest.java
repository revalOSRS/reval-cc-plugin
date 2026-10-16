package com.revalclan.clanbrowser;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

/** Exercises the real window/API call path with a deliberately held item response. */
public class BrowserNavigationTest {
    private HttpServer server;
    private OkHttpClient http;
    private ClanBrowserWindow window;
    private String previous;
    private final CountDownLatch releaseBoss = new CountDownLatch(1);
    private final BlockingQueue<BrowserModels.View> loaded = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicBoolean failBoss = new AtomicBoolean();

    @Before public void start() throws Exception {
        assumeFalse("Native window checks need a desktop", GraphicsEnvironment.isHeadless());
        previous = System.getProperty("reval.browserApi");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/plugin/clan-browser/catalogue", exchange -> {
            String pages = page("forestry","Forestry","Other") + "," + page("vorkath","Vorkath","Bosses") + "," + page("zulrah","Zulrah","Bosses");
            byte[] body = ("{\"status\":\"success\",\"data\":{\"pages\":["+pages+"]}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/plugin/clan-browser/view", exchange -> {
            reads.incrementAndGet();
            boolean boss = exchange.getRequestURI().getQuery().contains("category=vorkath");
            if (boss) try { releaseBoss.await(5,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            String selected = boss ? page("vorkath","Vorkath","Bosses") : page("forestry","Forestry","Other");
            boolean failure = boss && failBoss.get();
            byte[] body = (failure ? "{\"status\":\"error\",\"message\":\"Temporarily unavailable\"}" :
                "{\"status\":\"success\",\"data\":{\"kind\":\"clog\",\"page\":"+selected+",\"account\":{\"osrs_nickname\":\"Valgusruum\",\"obtained\":141,\"total\":1717},\"groups\":[]}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(failure ? 500 : 200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        System.setProperty("reval.browserApi","http://127.0.0.1:"+server.getAddress().getPort()+"/plugin");
        http = new OkHttpClient();
        BrowserApi api = new BrowserApi(http,new Gson()) {
            @Override public Call view(BrowserCommand command,long hash,boolean refresh,Consumer<BrowserModels.View> success,Consumer<String> failure) {
                return super.view(command,hash,refresh,data -> { success.accept(data); loaded.offer(data); },error -> { failure.accept(error); errors.offer(error); });
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            window = new ClanBrowserWindow(null,api,id -> null);
            window.open(BrowserCommand.parse("reval clog forestry Valgusruum")); window.setVisible(false);
        });
        assertNotNull("Initial collection log did not load",loaded.poll(5,TimeUnit.SECONDS));
    }

    @After public void stop() throws Exception {
        releaseBoss.countDown();
        if (window != null) SwingUtilities.invokeAndWait(window::dispose);
        if (server != null) server.stop(0);
        if (http != null) { http.dispatcher().executorService().shutdownNow(); http.connectionPool().evictAll(); }
        if (previous == null) System.clearProperty("reval.browserApi"); else System.setProperty("reval.browserApi",previous);
    }

    @Test public void navigationStaysAvailableWhileOnlyTheSelectedItemsAreLoading() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton other = button(window,"Other");
            button(window,"Bosses").doClick(0);
            assertNotNull("Main collection tabs disappeared during an item fetch",button(window,"Other"));
            assertSame("Loading must retain the existing navigation components",other,button(window,"Other"));
            assertNotNull("Selected category list must appear before its items load",button(window,"Vorkath"));
            assertNotNull("Other categories must remain usable during an item fetch",button(window,"Zulrah"));
        });
    }

    @Test public void returningToACachedPageIsImmediateWithNoLoadingFrameOrHttpRequest() throws Exception {
        releaseBoss.countDown();
        SwingUtilities.invokeAndWait(() -> button(window,"Bosses").doClick(0));
        assertNotNull("Boss log did not load",loaded.poll(5,TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            JButton bosses = button(window,"Bosses");
            button(window,"Other").doClick(0);
            assertNotNull("A cached page was replaced by a loading frame",window.currentView());
            assertEquals("forestry",window.currentView().page.value);
            assertFalse("Cached navigation must not show a loading indicator",loading(window));
            assertSame("Cached navigation must retain the existing tabs",bosses,button(window,"Bosses"));
        });
        assertEquals("Cached navigation made another HTTP request",2,reads.get());
    }

    @Test public void aFailedItemLoadKeepsNavigationAndLetsTheUserOpenACachedCategory() throws Exception {
        failBoss.set(true); releaseBoss.countDown();
        SwingUtilities.invokeAndWait(() -> button(window,"Bosses").doClick(0));
        assertEquals("Temporarily unavailable",errors.poll(5,TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertNotNull("Error must leave category navigation available",button(window,"Zulrah"));
            assertNotNull("Failed item loads must remain retryable",button(window,"Try again"));
            assertFalse("A failed fetch must stop its loading indicator",loading(window));
            button(window,"Other").doClick(0);
            assertNotNull(window.currentView()); assertEquals("forestry",window.currentView().page.value);
        });
        assertEquals(2,reads.get());
    }

    private static String page(String value,String label,String category) {
        return "{\"value\":\""+value+"\",\"label\":\""+label+"\",\"category\":\""+category+"\"}";
    }
    private static JButton button(Container root,String label) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton && label.equals(((JButton)child).getText())) return (JButton)child;
            if (child instanceof Container) { JButton found=button((Container)child,label); if (found != null) return found; }
        }
        return null;
    }
    private static boolean loading(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JProgressBar && child.isVisible()) return true;
            if (child instanceof Container && loading((Container)child)) return true;
        }
        return false;
    }
}

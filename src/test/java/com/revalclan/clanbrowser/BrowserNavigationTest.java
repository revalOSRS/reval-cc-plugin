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
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.JLabel;
import javax.swing.JFrame;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.*;

/** Exercises the real window/API call path with a deliberately held item response. */
public class BrowserNavigationTest {
    private HttpServer server;
    private OkHttpClient http;
    private BrowserApi api;
    private ClanBrowserWindow window;
    private JFrame gameWindow;
    private String previous;
    private final CountDownLatch releaseBoss = new CountDownLatch(1);
    private final BlockingQueue<BrowserModels.View> loaded = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicBoolean failBoss = new AtomicBoolean();
    private final BlockingQueue<okhttp3.HttpUrl> queries = new LinkedBlockingQueue<>();

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
            okhttp3.HttpUrl url = okhttp3.HttpUrl.parse("http://localhost" + exchange.getRequestURI()); queries.offer(url);
            boolean boss = exchange.getRequestURI().getQuery().contains("category=vorkath");
            if (boss) try { releaseBoss.await(5,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            String query=exchange.getRequestURI().getQuery();
            String selected = boss ? page("vorkath","Vorkath","Bosses") : query.contains("category=zulrah") ? page("zulrah","Zulrah","Bosses") : page("forestry","Forestry","Other");
            String kind=url.queryParameter("command");
            if (kind.equals("leaderboard")) selected = "null";
            boolean failure = boss && failBoss.get();
            boolean loggedOut = kind.equals("clog") && url.queryParameter("player") == null && url.queryParameter("accountHash") == null;
            String account = "otherguy".equalsIgnoreCase(url.queryParameter("player")) ? "OtherGuy" : url.queryParameter("player") == null ? "My Account" : "Valgusruum";
            byte[] body = (loggedOut ? "{\"status\":\"error\",\"message\":\"Log in to view your log, or enter a clan member’s OSRS name.\"}" : failure ? "{\"status\":\"error\",\"message\":\"Temporarily unavailable\"}" :
                "{\"status\":\"success\",\"data\":{\"kind\":\""+kind+"\",\"title\":\"Clan Points Leaderboard\",\"completedPages\":[{\"category\":\"Other\",\"subcategory\":\"Forestry\"},{\"category\":\"Bosses\",\"subcategory\":\"Zulrah\"}],\"page\":"+selected+",\"account\":{\"osrs_nickname\":\""+account+"\",\"obtained\":141,\"total\":1717},\"groups\":[],\"rows\":[{\"player\":\"Clan Member\",\"rank\":1,\"value\":812,\"status\":\"complete\"}]}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(loggedOut ? 400 : failure ? 500 : 200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        System.setProperty("reval.browserApi","http://127.0.0.1:"+server.getAddress().getPort()+"/plugin");
        http = new OkHttpClient();
        api = new BrowserApi(http,new Gson()) {
            @Override public Call view(BrowserCommand command,long hash,boolean refresh,Consumer<BrowserModels.View> success,Consumer<String> failure) {
                return super.view(command,hash,refresh,data -> { success.accept(data); loaded.offer(data); },error -> { failure.accept(error); errors.offer(error); });
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            gameWindow = new JFrame("Game window");
            window = new ClanBrowserWindow(gameWindow,api,id -> null);
            window.open(BrowserCommand.parse("reval clog forestry Valgusruum")); window.setVisible(false);
        });
        assertNotNull("Initial collection log did not load",loaded.poll(5,TimeUnit.SECONDS));
        queries.clear();
    }

    @After public void stop() throws Exception {
        releaseBoss.countDown();
        if (window != null) SwingUtilities.invokeAndWait(window::dispose);
        if (gameWindow != null) SwingUtilities.invokeAndWait(gameWindow::dispose);
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

    @Test public void greenlogsIncludeUnvisitedCategoriesAndNeverLeakToAnotherAccount() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(BrowserTheme.GREEN,button(window,"Forestry").getForeground());
            button(window,"Bosses").doClick(0);
            assertEquals("An unopened completed page must already be green",BrowserTheme.GREEN,button(window,"Zulrah").getForeground());
            assertNotEquals("The unfinished page must keep its ordinary selected style",BrowserTheme.GREEN,button(window,"Vorkath").getForeground());
            window.open(BrowserCommand.parse("reval clog forestry OtherGuy")); window.setVisible(false);
            assertNotEquals("Another account must not inherit the previous account's greenlogs",BrowserTheme.GREEN,button(window,"Forestry").getForeground());
        });
    }

    @Test public void luckAndDeservedUseCategoryButtonsAndCacheTheirStandings() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            button(window,"Luck").doClick(0);
            assertNotNull(button(window,"Other")); assertNotNull(button(window,"Forestry"));
            assertFalse("Luck must not show a category dropdown",categoryDropdownVisible(window));
        });
        assertEquals("luck",loaded.poll(5,TimeUnit.SECONDS).kind);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue("Luck must show a leaderboard",containsTable(window));
            button(window,"Bosses").doClick(0);
            assertNotNull(button(window,"Zulrah"));
        });
        releaseBoss.countDown(); assertEquals("luck",loaded.poll(5,TimeUnit.SECONDS).kind);
        SwingUtilities.invokeAndWait(() -> button(window,"Deserved").doClick(0));
        assertEquals("deserved",loaded.poll(5,TimeUnit.SECONDS).kind);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(containsTable(window)); assertNotNull(button(window,"Vorkath"));
            assertFalse(categoryDropdownVisible(window));
            button(window,"Luck").doClick(0);
            assertEquals("luck",window.currentView().kind); assertFalse(loading(window));
        });
        assertEquals("Returning to Luck must reuse its cached standings",4,reads.get());
    }

    @Test public void collectionLogTabDefaultsToOwnAccountButExplicitPlayerNavigationStaysSelected() throws Exception {
        releaseBoss.countDown();
        SwingUtilities.invokeAndWait(() -> { window.identity(42L,"My Account"); button(window,"Collection log").doClick(0); });
        assertNotNull(loaded.poll(5,TimeUnit.SECONDS));
        okhttp3.HttpUrl own = queries.poll(5,TimeUnit.SECONDS);
        assertNotNull("Switching to your account must issue its own request",own);
        assertEquals("42",own.queryParameter("accountHash"));
        assertNull("The Collection log tab must discard the previously viewed account",own.queryParameter("player"));
        SwingUtilities.invokeAndWait(() -> assertEquals("My Account",window.currentView().account.name));
        SwingUtilities.invokeAndWait(() -> button(window,"Bosses").doClick(0));
        assertNotNull(loaded.poll(5,TimeUnit.SECONDS));
        assertNull("Category navigation must keep using the viewer's account",queries.poll(5,TimeUnit.SECONDS).queryParameter("player"));
        SwingUtilities.invokeAndWait(() -> { window.open(BrowserCommand.parse("reval clog forestry OtherGuy")); window.setVisible(false); });
        assertNotNull(loaded.poll(5,TimeUnit.SECONDS)); assertEquals("otherguy",queries.poll(5,TimeUnit.SECONDS).queryParameter("player"));
        SwingUtilities.invokeAndWait(() -> button(window,"Bosses").doClick(0));
        assertNotNull(loaded.poll(5,TimeUnit.SECONDS));
        assertEquals("OtherGuy",queries.poll(5,TimeUnit.SECONDS).queryParameter("player"));
    }

    @Test public void serverErrorTypographyIsReadableInTheRunescapeFont() throws Exception {
        SwingUtilities.invokeAndWait(() -> { window.open(BrowserCommand.parse("reval clog forestry")); window.setVisible(false); });
        assertNotNull("Expected the logged-out error",errors.poll(5,TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            JLabel message = messageLabel(window,"Log in to view your log"); assertNotNull(message);
            assertEquals("Log in to view your log, or enter a clan member's OSRS name.",message.getText());
            assertEquals("Every character in the displayed error must have a glyph",-1,message.getFont().canDisplayUpTo(message.getText()));
        });
    }

    @Test public void browserIsAnIndependentWindowThatCannotHoldTheGameBehindAnOwnedDialog() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertTrue("The browser must be an independent frame",JFrame.class.isInstance(window));
            assertNull("The game must not own a dialog that stays above it",window.getOwner());
            assertFalse(window.isAlwaysOnTop());
        });
    }

    @Test public void sidebarShortcutOpensTheSharedBrowserAtTotalPointsAndReusesIt() throws Exception {
        net.runelite.api.Client client = mock(net.runelite.api.Client.class);
        when(client.getCanvas()).thenReturn(new java.awt.Canvas());
        net.runelite.client.callback.ClientThread clientThread = mock(net.runelite.client.callback.ClientThread.class);
        doAnswer(invocation -> { ((Runnable)invocation.getArgument(0)).run(); return null; }).when(clientThread).invoke(any(Runnable.class));
        ClanBrowserManager manager = new ClanBrowserManager(client,clientThread,mock(net.runelite.client.game.ItemManager.class),api);
        com.revalclan.ui.LeaderboardPanel sidebar = new com.revalclan.ui.LeaderboardPanel();
        try {
            SwingUtilities.invokeAndWait(() -> {
                manager.startUp(); sidebar.setOnOpenBrowser(manager::openPointsLeaderboard);
                button(sidebar,"Open full leaderboard").doClick(0);
            });
            assertEquals("leaderboard",loaded.poll(5,TimeUnit.SECONDS).kind);
            okhttp3.HttpUrl query = queries.poll(5,TimeUnit.SECONDS);
            assertEquals("leaderboard",query.queryParameter("command")); assertEquals("points",query.queryParameter("metric"));
            final ClanBrowserWindow[] opened = new ClanBrowserWindow[1];
            SwingUtilities.invokeAndWait(() -> {
                for (java.awt.Window candidate : java.awt.Window.getWindows()) if (candidate instanceof ClanBrowserWindow && candidate != window && candidate.isDisplayable()) opened[0]=(ClanBrowserWindow)candidate;
                assertNotNull(opened[0]); assertEquals("Reval · Clan Points Leaderboard",opened[0].getTitle());
                assertEquals(BrowserTheme.GOLD,button(opened[0],"Points").getForeground());
                button(sidebar,"Open full leaderboard").doClick(0);
            });
            assertEquals("leaderboard",loaded.poll(5,TimeUnit.SECONDS).kind);
            SwingUtilities.invokeAndWait(() -> assertTrue("Repeated shortcut clicks must reuse the same window",opened[0].isDisplayable()));
            assertEquals("The second shortcut click must use the cached Points board",2,reads.get());
        } finally {
            manager.shutDown(); SwingUtilities.invokeAndWait(() -> {});
        }
    }

    private static JLabel messageLabel(Container root,String prefix) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel && ((JLabel)child).getText() != null && ((JLabel)child).getText().startsWith(prefix)) return (JLabel)child;
            if (child instanceof Container) { JLabel found=messageLabel((Container)child,prefix); if (found != null) return found; }
        }
        return null;
    }
    private static boolean categoryDropdownVisible(Container root) {
        for (Component child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JTextField && "Category".equals(((JTextField)child).getAccessibleContext().getAccessibleName())) return true;
            if (child instanceof Container && categoryDropdownVisible((Container)child)) return true;
        }
        return false;
    }
    private static boolean containsTable(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTable) return true;
            if (child instanceof Container && containsTable((Container)child)) return true;
        }
        return false;
    }

    private static String page(String value,String label,String category) {
        return "{\"value\":\""+value+"\",\"label\":\""+label+"\",\"category\":\""+category+"\",\"subcategory\":\""+label+"\"}";
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

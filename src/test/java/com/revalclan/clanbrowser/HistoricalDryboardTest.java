package com.revalclan.clanbrowser;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.JComboBox;
import javax.swing.JButton;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class HistoricalDryboardTest {
    @Test public void historicalCommandPreservesModeThroughAliasesCategoryChangesAndSharing() {
        BrowserModels.Page cox = new BrowserModels.Page(); cox.category = "Raids"; cox.value = "chambers-of-xeric";
        cox.label = "Chambers of Xeric"; cox.aliases.add("cox");
        BrowserModels.Catalogue catalogue = new BrowserModels.Catalogue(); catalogue.pages.add(cox);
        BrowserCommand command = BrowserCommand.parse("reval dryboard historical cox").resolve(catalogue);
        assertEquals("chambers-of-xeric", command.category);
        assertEquals("historical", command.parameters(123).get("mode"));
        assertFalse(command.parameters(123).containsKey("accountHash"));
        assertEquals("historical", command.withCategory("vorkath").mode);
        assertEquals("historical", command.resolveShared(catalogue.pages, "Member").mode);
        assertEquals("vorkath", BrowserCommand.parse("reval dryboard historical").category);
        assertFalse(BrowserCommand.parse("reval dryboard vorkath").parameters(123).containsKey("mode"));
    }

    @Test public void currentAndHistoricalViewsUseSeparateCachesAndRefreshTheSelectedMode() throws Exception {
        AtomicInteger reads = new AtomicInteger(); AtomicReference<String> sentMode = new AtomicReference<>();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            reads.incrementAndGet();
            String mode = chain.request().url().queryParameter("mode"); sentMode.set(mode);
            String payload = "{\"status\":\"success\",\"data\":{\"kind\":\"dryboard\",\"mode\":\"" + (mode == null ? "current" : mode) + "\","
                + "\"panels\":[{\"rows\":[{\"player\":\"Member\",\"value\":400,\"start\":100,\"end\":500,\"ongoing\":false,\"item\":\"Unique\"}]}]}}";
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), payload)).build();
        }).build();
        try {
            BrowserApi api = new BrowserApi(http, new Gson());
            BrowserCommand current = BrowserCommand.parse("reval dryboard vorkath"), historical = BrowserCommand.parse("reval dryboard historical vorkath");
            assertEquals("current", read(api, current, false).mode);
            BrowserModels.View view = read(api, historical, false);
            assertEquals("historical", view.mode); assertEquals("historical", sentMode.get());
            BrowserModels.Row row = view.panels.get(0).rows.get(0);
            assertEquals(Long.valueOf(100), row.start); assertEquals(Long.valueOf(500), row.end); assertFalse(row.ongoing);
            read(api, current, false); read(api, historical, false); assertEquals(2, reads.get());
            read(api, historical, true); assertEquals(3, reads.get()); assertEquals("historical", sentMode.get());
            read(api, current, false); assertEquals(3, reads.get());
        } finally { http.dispatcher().executorService().shutdownNow(); http.connectionPool().evictAll(); }
    }

    @Test public void selectorAndRefreshKeepTheChosenHistoricalView() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicReference<BrowserCommand> sent = new AtomicReference<>();
        AtomicReference<Boolean> refreshed = new AtomicReference<>(false);
        BrowserModels.Page page = new BrowserModels.Page(); page.category = "Bosses"; page.subcategory = "Vorkath"; page.value = "vorkath"; page.label = "Vorkath";
        BrowserModels.Catalogue catalogue = new BrowserModels.Catalogue(); catalogue.pages.add(page);
        BrowserApi api = new BrowserApi(new OkHttpClient(), new Gson()) {
            @Override public Call catalogue(Consumer<BrowserModels.Catalogue> success, Consumer<String> failure) { success.accept(catalogue); return null; }
            @Override public Call view(BrowserCommand command, long accountHash, boolean refresh, Consumer<BrowserModels.View> success, Consumer<String> failure) {
                sent.set(command); refreshed.set(refresh);
                BrowserModels.View view = new BrowserModels.View(); view.kind = "dryboard"; view.mode = command.mode; view.page = page;
                success.accept(view); return null;
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            ClanBrowserWindow window = new ClanBrowserWindow(null, api, id -> null);
            try {
                window.open(BrowserCommand.parse("reval dryboard vorkath"));
                JComboBox<?> mode = (JComboBox<?>) find(window, component -> component instanceof JComboBox
                    && "Dryboard mode".equals(component.getAccessibleContext().getAccessibleName()));
                assertNotNull(mode); mode.setSelectedItem("Historical");
                assertEquals("historical", sent.get().mode); assertEquals("historical", window.currentView().mode);
                JButton refresh = (JButton) find(window, component -> component instanceof JButton && "Refresh".equals(((JButton) component).getText()));
                assertNotNull(refresh); refresh.doClick();
                assertEquals("historical", sent.get().mode); assertTrue(refreshed.get());
                mode.setSelectedItem("Current"); assertEquals("current", window.currentView().mode);
            } finally { window.dispose(); }
        });
    }

    private static Component find(Container parent, java.util.function.Predicate<Component> match) {
        for (Component component : parent.getComponents()) {
            if (match.test(component)) return component;
            if (component instanceof Container) { Component found = find((Container) component, match); if (found != null) return found; }
        }
        return null;
    }

    private static BrowserModels.View read(BrowserApi api, BrowserCommand command, boolean refresh) throws Exception {
        CompletableFuture<BrowserModels.View> result = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> api.view(command, 123, refresh, result::complete,
            error -> result.completeExceptionally(new AssertionError(error))));
        return result.get(10, TimeUnit.SECONDS);
    }
}

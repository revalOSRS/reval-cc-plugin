package com.revalclan.clanbrowser;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserRenderingTest {
    @Test public void smallLogFitsTheViewportAndCentersTheVisibleSpriteRatherThanItsTransparentMargins() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserModels.Group group = new BrowserModels.Group(); group.subcategory = "Forestry";
            for (int i = 0; i < 23; i++) { BrowserModels.Item item = new BrowserModels.Item(); item.id = 1; item.name = "Sprite"; item.quantity = 1L; group.items.add(item); }
            BufferedImage icon = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
            for (int y = 2; y < 12; y++) for (int x = 3; x < 13; x++) icon.setRGB(x, y, java.awt.Color.MAGENTA.getRGB());
            CollectionGrid grid = new CollectionGrid(Arrays.asList(group), id -> icon);
            javax.swing.JViewport viewport = new javax.swing.JViewport(); viewport.setSize(1100, 330); viewport.setView(grid); grid.setSize(1100, 330);
            assertTrue("All 23 items should fit without clipping the last row", grid.getPreferredSize().height <= 330);
            BufferedImage full = new BufferedImage(1100, 330, BufferedImage.TYPE_INT_ARGB); Graphics2D fullGraphics = full.createGraphics(); grid.paint(fullGraphics); fullGraphics.dispose();
            int first = 1100, last = -1;
            for (int x = 0; x < 1100; x++) if (full.getRGB(x, 14) == BrowserTheme.RECEIVED.getRGB()) { first = Math.min(first, x); last = x; }
            assertEquals("Only the shared 12-pixel inset and cell border should remain", 13, first);
            assertEquals("The first row should use the available width", 13, 1100 - last - 1);
            group.items.subList(1, group.items.size()).clear(); viewport.setSize(200, 200); grid.filter("", "All items"); grid.setSize(200, 200);
            BufferedImage painted = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB); Graphics2D g = painted.createGraphics(); grid.paint(g); g.dispose();
            int left = 200, right = -1, top = 200, bottom = -1;
            for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) if (painted.getRGB(x, y) == java.awt.Color.MAGENTA.getRGB()) { left = Math.min(left,x); right = Math.max(right,x); top = Math.min(top,y); bottom = Math.max(bottom,y); }
            assertEquals(199, left + right); assertEquals(199, top + bottom);
        });
    }
    @Test public void largeClueGridPaintsOnlyTheVisibleCellsAndKeepsAllItemsScrollable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserModels.Group group = new BrowserModels.Group(); group.subcategory = "Clues";
            for (int i = 0; i < 674; i++) { BrowserModels.Item item = new BrowserModels.Item(); item.id = i + 1; item.name = "Item " + i; item.quantity = i % 2 == 0 ? 1L : 0L; group.items.add(item); }
            AtomicInteger loaded = new AtomicInteger();
            CollectionGrid grid = new CollectionGrid(Arrays.asList(group), id -> { loaded.incrementAndGet(); return new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB); });
            grid.setSize(900, grid.getPreferredSize().height); assertTrue(grid.getPreferredSize().height > 3000);
            BufferedImage image = new BufferedImage(900, 400, BufferedImage.TYPE_INT_ARGB); Graphics2D graphics = image.createGraphics(); graphics.setClip(new Rectangle(0, 0, 900, 400)); grid.paint(graphics); graphics.dispose();
            assertTrue(loaded.get() > 0); assertTrue(loaded.get() < 100);
            int green = 0, brown = 0;
            for (int y = 0; y < 400; y++) for (int x = 0; x < 900; x++) { if (image.getRGB(x, y) == BrowserTheme.RECEIVED.getRGB()) green++; if (image.getRGB(x, y) == BrowserTheme.MISSING.getRGB()) brown++; }
            assertTrue(green > 10000); assertTrue(brown > 10000);
            grid.filter("Item 673", "All items"); assertTrue(grid.getPreferredSize().height < 300);
            assertNotNull(grid.getAccessibleContext());
        });
    }
    @Test public void fullLeaderboardsKeepScoresAndOriginalRanksWhenFiltered() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserModels.View data = new BrowserModels.View(); data.kind = "leaderboard"; data.valueLabel = "XP";
            BrowserModels.Panel panel = new BrowserModels.Panel(); panel.label = "Mains";
            for (int i = 0; i < 150; i++) { BrowserModels.Row row = new BrowserModels.Row(); row.rank = i + 1; row.player = "Player " + i; row.value = 4_800_000_000L - i; panel.rows.add(row); }
            data.panels.add(panel); LeaderboardView view = new LeaderboardView(data, "Player 149", null); view.setSize(1000, 500); view.doLayout();
            assertEquals(150, tables(view).get(0).getRowCount());
            view.filter("Player 149"); assertEquals(1, tables(view).get(0).getRowCount());
            BrowserModels.Row row = (BrowserModels.Row) tables(view).get(0).getValueAt(0, 0);
            assertEquals(Integer.valueOf(150), row.rank); assertEquals(Long.valueOf(4_799_999_851L), row.value);
        });
    }
    private static java.util.List<javax.swing.JTable> tables(java.awt.Container parent) {
        java.util.List<javax.swing.JTable> result = new java.util.ArrayList<>();
        for (java.awt.Component child : parent.getComponents()) { if (child instanceof javax.swing.JTable) result.add((javax.swing.JTable) child); if (child instanceof java.awt.Container) result.addAll(tables((java.awt.Container) child)); }
        return result;
    }
}

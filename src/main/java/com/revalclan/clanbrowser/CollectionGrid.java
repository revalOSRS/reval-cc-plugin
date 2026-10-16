package com.revalclan.clanbrowser;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import javax.swing.JComponent;
import javax.swing.Scrollable;
import net.runelite.client.util.AsyncBufferedImage;

/** Responsive sprite grid. Paints visible cells only; large clue logs remain cheap to scroll. */
final class CollectionGrid extends JComponent implements Scrollable, javax.accessibility.Accessible {
    private final List<BrowserModels.Group> groups;
    private final IntFunction<BufferedImage> images;
    private final Map<Integer, BufferedImage> icons = new HashMap<>();
    private final Map<Integer, Rectangle> iconBounds = new HashMap<>();
    private final List<Cell> cells = new ArrayList<>();
    private final List<Heading> headings = new ArrayList<>();
    private int lastWidth = -1, lastViewportHeight = -1, layoutHeight;
    private static final class Cell { BrowserModels.Item item; int x, y, size; boolean complete; }
    private static final class Heading { String text, counter, rank; int y; boolean complete; }

    CollectionGrid(List<BrowserModels.Group> groups, IntFunction<BufferedImage> images) {
        this.groups = groups; this.images = images; setOpaque(true); setBackground(BrowserTheme.PANEL);
        setToolTipText(" "); getAccessibleContext().setAccessibleName("Collection log item grid");
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { lastWidth = -1; revalidate(); repaint(); }
        });
    }
    @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {};
        return accessibleContext;
    }

    private void layoutCells() {
        int width = Math.max(200, getWidth() > 0 ? getWidth() : 900);
        int viewportHeight = getParent() instanceof javax.swing.JViewport ? getParent().getHeight() : -1;
        if (width == lastWidth && viewportHeight == lastViewportHeight) return;
        lastWidth = width; lastViewportHeight = viewportHeight; cells.clear(); headings.clear();
        int count = groups.stream().mapToInt(group -> group.items.size()).sum();
        int target = count <= 6 ? 144 : count <= 30 ? 112 : count <= 80 ? 88 : 64;
        int gap = count > 80 ? 7 : 10, padding = 12;
        int columns = Math.max(1, Math.min(Math.max(1, count), (width - padding * 2 + gap) / (target + gap)));
        if (count > 0 && count <= 6 && (width - padding * 2 - (count - 1) * gap) / count >= 96) columns = count;
        int size = Math.min(200, (width - padding * 2 - (columns - 1) * gap) / columns);
        if (groups.size() == 1 && count <= 30 && count > 0 && viewportHeight > 0) {
            // Reflow into more columns before shrinking cells; this avoids the
            // wide side gutters left by centering a height-limited fixed grid.
            while (columns < count) {
                int rows = (count + columns - 1) / columns;
                if (rows * size + (rows - 1) * gap + padding * 2 <= viewportHeight) break;
                int next = (width - padding * 2 - columns * gap) / (columns + 1);
                if (next < 72) break;
                columns++; size = Math.min(200, next);
            }
            int rows = (count + columns - 1) / columns;
            int fitting = (viewportHeight - padding * 2 - (rows - 1) * gap) / rows;
            if (fitting >= 72) size = Math.min(size, fitting);
        }
        int gridWidth = columns * size + (columns - 1) * gap;
        int spare = width - padding * 2 - gridWidth;
        boolean fillWidth = columns > 1 && spare < columns;
        int top = padding;
        for (BrowserModels.Group group : groups) {
            if (group.items.isEmpty()) continue;
            if (groups.size() > 1) {
                Heading heading = new Heading(); heading.text = group.subcategory + "  (" + group.items.stream().filter(item -> item.quantity != null && item.quantity > 0).count() + " / " + group.items.size() + ")";
                heading.y = top; heading.complete = group.complete;
                if (group.complete) heading.text += " · GREENLOGGED";
                heading.counter = group.metric == null ? null : BrowserTheme.counter(group.metric);
                heading.rank = group.rank == null ? null : "Clan luck: #" + group.rank.rank + " / " + group.rank.total + (group.rank.estimated ? " (est.)" : "");
                headings.add(heading); top += heading.rank == null ? 42 : 66;
            }
            for (int i = 0; i < group.items.size(); i++) {
                int column = i % columns;
                Cell cell = new Cell(); cell.item = group.items.get(i);
                cell.x = padding + column * (size + gap) + (fillWidth ? column * spare / (columns - 1) : 0);
                cell.y = top + i / columns * (size + gap);
                cell.size = size; cell.complete = group.complete; cells.add(cell);
            }
            int rows = (group.items.size() + columns - 1) / columns;
            top += rows * size + Math.max(0, rows - 1) * gap + 12;
        }
        layoutHeight = Math.max(180, top);
    }
    @Override public Dimension getPreferredSize() { layoutCells(); return new Dimension(900, layoutHeight); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); layoutCells(); Graphics2D g = (Graphics2D) graphics.create(); BrowserTheme.pixel(g);
        Rectangle clip = g.getClipBounds();
        for (Heading heading : headings) {
            if (heading.y + 64 < clip.y || heading.y > clip.y + clip.height) continue;
            g.setColor(BrowserTheme.HEADER); g.fillRect(12, heading.y, getWidth() - 24, 34);
            g.setColor(heading.complete ? BrowserTheme.GREEN_BORDER : BrowserTheme.BORDER); g.drawRect(12, heading.y, getWidth() - 25, 34);
            BrowserTheme.text(g, heading.text, 24, heading.y, 34, heading.counter == null ? getWidth() - 48 : (int) (getWidth() * .62) - 40, 23, heading.complete ? BrowserTheme.GREEN : BrowserTheme.TEXT, true, false);
            if (heading.counter != null) BrowserTheme.text(g, heading.counter, getWidth() - 24, heading.y, 34, (int) (getWidth() * .36) - 16, 20, BrowserTheme.MUTED, false, true);
            if (heading.rank != null) BrowserTheme.text(g, heading.rank, 24, heading.y + 34, 24, getWidth() - 48, 20, BrowserTheme.GOLD, false, false);
        }
        for (Cell cell : cells) {
            if (cell.y + cell.size < clip.y || cell.y > clip.y + clip.height) continue;
            boolean received = cell.item.quantity != null && cell.item.quantity > 0;
            g.setColor(received ? BrowserTheme.RECEIVED : BrowserTheme.MISSING); g.fillRect(cell.x, cell.y, cell.size, cell.size);
            g.setColor(cell.complete ? BrowserTheme.GREEN_BORDER : received ? BrowserTheme.BORDER : BrowserTheme.DARK_BORDER); g.drawRect(cell.x, cell.y, cell.size - 1, cell.size - 1);
            BufferedImage icon = icon(cell.item.id);
            if (icon != null) {
                Rectangle bounds = bounds(cell.item.id, icon);
                float alpha = received ? 1f : .22f; g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                int scale = cell.size >= 100 ? 3 : cell.size >= 80 ? 2 : 1;
                int w = Math.min(bounds.width * scale, cell.size - 12), h = Math.min(bounds.height * scale, cell.size - 12);
                double factor = Math.min((double) w / bounds.width, (double) h / bounds.height);
                w = (int) (bounds.width * factor); h = (int) (bounds.height * factor);
                int x = cell.x + (cell.size - w) / 2, y = cell.y + (cell.size - h) / 2;
                g.drawImage(icon, x, y, x + w, y + h, bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, null);
                g.setComposite(AlphaComposite.SrcOver);
            }
            BrowserTheme.text(g, cell.item.quantity == null ? "?" : BrowserTheme.number(cell.item.quantity), cell.x + cell.size - 5, cell.y + 1, 26, cell.size - 8,
                cell.size >= 100 ? 28 : 23, received ? BrowserTheme.YELLOW : BrowserTheme.MUTED, true, true);
        }
        if (cells.isEmpty()) BrowserTheme.text(g, "No items in this collection log", 24, 30, 45, getWidth() - 48, 26, BrowserTheme.MUTED, false, false);
        g.dispose();
    }
    private BufferedImage icon(Integer id) {
        if (id == null || id <= 0) return null;
        if (icons.containsKey(id)) return icons.get(id);
        BufferedImage image = images.apply(id); icons.put(id, image);
        if (image instanceof AsyncBufferedImage) ((AsyncBufferedImage) image).onLoaded(() -> javax.swing.SwingUtilities.invokeLater(() -> { iconBounds.remove(id); repaint(); }));
        return image;
    }
    private Rectangle bounds(Integer id, BufferedImage icon) {
        Rectangle cached = iconBounds.get(id);
        if (cached != null) return cached;
        int left = icon.getWidth(), top = icon.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < icon.getHeight(); y++) for (int x = 0; x < icon.getWidth(); x++) if ((icon.getRGB(x, y) >>> 24) != 0) {
            left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
        }
        Rectangle result = right < left ? new Rectangle(0, 0, icon.getWidth(), icon.getHeight()) : new Rectangle(left, top, right - left + 1, bottom - top + 1);
        iconBounds.put(id, result); return result;
    }
    @Override public String getToolTipText(MouseEvent event) {
        layoutCells();
        for (Cell cell : cells) if (new Rectangle(cell.x, cell.y, cell.size, cell.size).contains(event.getPoint())) {
            return cell.item.name + " — " + (cell.item.quantity == null ? "Not synced" : cell.item.quantity > 0 ? BrowserTheme.number(cell.item.quantity) + " received" : "Missing");
        }
        return null;
    }
    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(900, 500); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 32; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(32, visible.height - 50); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}

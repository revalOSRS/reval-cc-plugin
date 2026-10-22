package com.revalclan.clanbrowser;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.Border;
import net.runelite.client.ui.FontManager;

/** A shared RuneScape palette and pixel-font drawing rules for every browser view. */
final class BrowserTheme {
    static final Color FRAME = new Color(0x27231e), BACKGROUND = new Color(0x514839), PANEL = new Color(0x393228);
    static final Color HEADER = new Color(0x332d24), BORDER = new Color(0x897655), DARK_BORDER = new Color(0x211c15);
    static final Color RECEIVED = new Color(0x3d4630), MISSING = new Color(0x433b2e), GREEN_BORDER = new Color(0x5d8544);
    static final Color TEXT = new Color(0xf1dfb4), MUTED = new Color(0xbdb09a), GREEN = new Color(0x79df54);
    static final Color ORANGE = new Color(0xff981f), GOLD = new Color(0xffcf63), YELLOW = new Color(0xffff00);
    static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private BrowserTheme() {}
    /** API prose can contain typography absent from the RuneScape pixel font. */
    static String plainText(String text) {
        return text == null ? null : text.replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201c', '"').replace('\u201d', '"').replace('\u2013', '-').replace('\u2014', '-')
            .replace("\u2026", "...").replace('\u00a0', ' ');
    }
    static String number(Number value) { return value == null ? "?" : NUMBERS.format(value); }
    static Font font(float size, boolean bold) { return (bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont()).deriveFont(size); }
    static String timestamp(String value) {
        try { return STAMP.format(Instant.parse(value)); } catch (Exception ignored) { return "Not synced yet"; }
    }
    static String counter(BrowserModels.Metric metric) {
        String key = metric.key == null ? "" : metric.key;
        String skill = key.startsWith("xp:") ? key.substring(3).replace('_', ' ') : "";
        if (!skill.isEmpty()) skill = capitalize(skill);
        String label = "xp".equals(metric.kind) ? skill + " XP" : metricName(metric.kind);
        String value = label + ": " + (metric.current == null ? "Unknown" : number(metric.current));
        return metric.completion == null ? value : value + " (" + (metric.completion.estimated ? "~" : "") + number(metric.completion.value) + ")";
    }
    /** Counter names shared by collection log headings and leaderboard columns. */
    static String metricName(String kind) { return "xp".equals(kind) ? "XP" : "clues".equals(kind) ? "Clues" : "completions".equals(kind) ? "Completions" : "Kills"; }
    static String luckRank(BrowserModels.LuckRank rank) { return "Clan luck: #" + rank.rank + " / " + rank.total + (rank.estimated ? " (est.)" : ""); }
    static String capitalize(String value) { return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1); }
    /** A copy of the graphics with the pixel font's rendering hints; the caller disposes it. */
    static Graphics2D pixel(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        return g;
    }
    static void text(Graphics2D g, String text, int x, int top, int height, int maxWidth, int size, Color color, boolean bold, boolean right) {
        if (text == null) return;
        Font selected = font(size, bold);
        while (g.getFontMetrics(selected).stringWidth(text) > maxWidth && selected.getSize() > 12) selected = selected.deriveFont((float) selected.getSize() - 1);
        g.setFont(selected);
        FontMetrics metrics = g.getFontMetrics();
        int left = right ? x - metrics.stringWidth(text) : x;
        java.awt.geom.Rectangle2D bounds = selected.createGlyphVector(g.getFontRenderContext(), text).getVisualBounds();
        int y = (int) Math.round(top + (height - bounds.getHeight()) / 2 - bounds.getY());
        g.setColor(Color.BLACK); g.drawString(text, left + 1, y + 1);
        g.setColor(color); g.drawString(text, left, y);
    }
    static void centered(Graphics2D g, String text, int center, int top, int height, int maxWidth, int size, Color color, boolean bold) {
        if (text == null) return;
        while (g.getFontMetrics(font(size, bold)).stringWidth(text) > maxWidth && size > 12) size--;
        text(g, text, center - g.getFontMetrics(font(size, bold)).stringWidth(text) / 2, top, height, maxWidth, size, color, bold, false);
    }
    static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(font(20, true)); button.setForeground(TEXT); button.setBackground(HEADER);
        button.setBorder(outline(BORDER, 5, 12));
        button.setFocusPainted(false); return button;
    }
    static void collectionCategory(JButton button, boolean selected, boolean complete) {
        button.setForeground(complete ? GREEN : selected ? GOLD : TEXT);
        button.setBackground(complete ? selected ? new Color(0x4e6039) : RECEIVED : selected ? new Color(0x6b5a3d) : HEADER);
        button.setBorder(outline(complete ? selected ? GREEN : GREEN_BORDER : BORDER, 5, 12));
    }
    static void field(JTextField field) {
        field.setBackground(HEADER); field.setForeground(TEXT); field.setCaretColor(GOLD); field.setFont(font(20, false));
        field.setBorder(outline(BORDER, 5, 8));
    }
    /** A one-pixel line in the given colour around symmetric padding. */
    static Border outline(Color color, int vertical, int horizontal) {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color), BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal));
    }
    static JScrollPane scroll(JComponent component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(BorderFactory.createLineBorder(BORDER)); pane.setBackground(PANEL); pane.getViewport().setBackground(PANEL);
        pane.getVerticalScrollBar().setUnitIncrement(30); pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return pane;
    }
}

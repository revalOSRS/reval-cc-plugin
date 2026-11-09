package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Small vector decorations for the seasonal panel; no emoji glyphs, external assets, or animation timer. */
public final class SeasonalDecoration {
    private SeasonalDecoration() {}

    /** The season's emblem: a gingerbread man. */
    public static Icon icon(int size) {
        return new ImageIcon(image(size));
    }

    public static BufferedImage image(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 30.0, size / 30.0);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        GingerbreadDecoration.paintEmblem(g);
        g.dispose();
        return image;
    }

    public static void paintHeader(Graphics graphics, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (UIConstants.isSeasonal()) {
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            GingerbreadDecoration.paintHeader(g, width, height);
        }
        g.dispose();
    }

    /** Reserve space for the full-width village; otherwise the gold separator. */
    public static JComponent separator() {
        if (!UIConstants.isSeasonal()) return new GradientSeparator();
        JPanel scene = new JPanel();
        scene.setOpaque(false);
        scene.setPreferredSize(new Dimension(0, GingerbreadDecoration.SCENE_HEIGHT));
        return scene;
    }

    /** Christmas icing is part of the component paint cycle, including repaints after scrolling. */
    public static void paintCard(Graphics graphics, int width) {
        paintIcing(graphics, 1.5, width - 3, 1.9, width * 31L, 2.8, true);
    }

    public static void paintTab(Graphics graphics, int width, boolean selected, String text) {
        paintIcing(graphics, 0, width, 1.7, text == null ? 0 : text.hashCode(), selected ? 3.2 : 2.4, false);
    }

    private static void paintIcing(Graphics graphics, double x, double width, double band,
                                   long seed, double maxDrip, boolean sprinkles) {
        if (!UIConstants.isSeasonal() || width <= 0) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        GingerbreadDecoration.icing(g, x, width, 0, band, seed, maxDrip, sprinkles);
        g.dispose();
    }
}

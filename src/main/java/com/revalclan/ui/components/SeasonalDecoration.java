package com.revalclan.ui.components;

import com.revalclan.ui.constants.Season;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Small vector decorations for the seasonal panel; no emoji glyphs, external assets, or animation timer. */
public final class SeasonalDecoration {
    private static final Color COBWEB = new Color(224, 203, 164, 50);
    private static final Color PUMPKIN_BODY = new Color(217, 119, 41);
    private static final Color PUMPKIN_LIGHT = new Color(255, 184, 88);
    private static final Color PUMPKIN_OUTLINE = new Color(81, 42, 22);
    private static final Color PUMPKIN_FACE = new Color(65, 36, 25);
    private static final Color PUMPKIN_GLOW = new Color(255, 208, 114);
    private static final Color PUMPKIN_STEM = new Color(126, 153, 97);

    private SeasonalDecoration() {}

    /** The season's emblem: a jack-o'-lantern or a gingerbread man. */
    public static Icon icon(int size) {
        return new ImageIcon(image(size));
    }

    public static BufferedImage image(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 30.0, size / 30.0);
        if (UIConstants.season() == Season.CHRISTMAS) {
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            GingerbreadDecoration.paintEmblem(g);
        } else {
            paintPumpkin(g);
        }
        g.dispose();
        return image;
    }

    private static void paintPumpkin(Graphics2D g) {
        g.setColor(PUMPKIN_STEM);
        fill(g, 14, 8, 16, 2, 20, 3, 17, 9);
        Path2D body = new Path2D.Double();
        body.moveTo(15, 7);
        body.curveTo(4, 1, -3, 23, 9, 27);
        body.curveTo(13, 29, 16, 27, 16, 27);
        body.curveTo(22, 29, 27, 24, 28, 19);
        body.curveTo(31, 10, 23, 2, 15, 7);
        g.setColor(PUMPKIN_BODY);
        g.fill(body);
        g.setStroke(new BasicStroke(1));
        g.setColor(PUMPKIN_OUTLINE);
        g.draw(body);

        Path2D ribs = new Path2D.Double();
        ribs.moveTo(13, 7);
        ribs.curveTo(7, 11, 8, 23, 12, 27);
        ribs.moveTo(17, 7);
        ribs.curveTo(23, 12, 22, 24, 18, 27);
        g.setColor(PUMPKIN_LIGHT);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(ribs);
        g.setColor(PUMPKIN_FACE);
        fill(g, 8, 12, 12, 17, 6, 17);
        fill(g, 21, 12, 24, 17, 18, 17);
        fill(g, 8, 21, 12, 22, 14, 20, 17, 22, 22, 20, 19, 25, 12, 25);
        g.setColor(PUMPKIN_GLOW);
        fill(g, 9, 14, 10, 15, 8, 15);
        fill(g, 21, 14, 22, 15, 20, 15);
        g.drawLine(12, 23, 17, 23);
    }

    public static void paintHeader(Graphics graphics, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (UIConstants.season() == Season.CHRISTMAS) {
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            GingerbreadDecoration.paintHeader(g, width, height);
        } else if (UIConstants.season() == Season.HALLOWEEN) {
            Color glow = Colors.withAlpha(UIConstants.SEASON_ACCENT, 18);
            g.setPaint(new RadialGradientPaint(width, height, Math.max(width, height),
                new float[] {0, 1}, new Color[] {glow, new Color(0, 0, 0, 0)}));
            g.fillRect(0, 0, width, height);
            paintCobweb(g, width);
        }
        g.dispose();
    }

    private static void paintCobweb(Graphics2D g, int width) {
        g.translate(width - 58, 0);
        g.setColor(COBWEB);
        g.setStroke(new BasicStroke(0.7f));
        double[][] ends = {{0, 0}, {0, 29}, {4, 54}, {29, 58}, {58, 58}};
        for (double[] end : ends) {
            g.drawLine(58, 0, (int) end[0], (int) end[1]);
        }
        for (double fraction : new double[] {0.2, 0.42, 0.68, 0.96}) {
            Path2D strand = new Path2D.Double();
            for (int i = 0; i < ends.length; i++) {
                double x = 58 + (ends[i][0] - 58) * fraction;
                double y = ends[i][1] * fraction;
                if (i == 0) strand.moveTo(x, y);
                else {
                    double previousX = 58 + (ends[i - 1][0] - 58) * fraction;
                    double previousY = ends[i - 1][1] * fraction;
                    strand.quadTo((previousX + x) / 2 + 2,
                        (previousY + y) / 2 - 2, x, y);
                }
            }
            g.draw(strand);
        }
    }

    /** Reserve space for the full-width village; other seasons keep the gold separator. */
    public static JComponent separator() {
        if (UIConstants.season() != Season.CHRISTMAS) return new GradientSeparator();
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
        if (UIConstants.season() != Season.CHRISTMAS || width <= 0) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        GingerbreadDecoration.icing(g, x, width, 0, band, seed, maxDrip, sprinkles);
        g.dispose();
    }

    private static void fill(Graphics2D g, double... points) {
        Path2D shape = new Path2D.Double();
        shape.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) shape.lineTo(points[i], points[i + 1]);
        shape.closePath();
        g.fill(shape);
    }
}

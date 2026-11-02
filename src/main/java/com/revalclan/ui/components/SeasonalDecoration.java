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
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;
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

    private static final Color HAT_RED = new Color(201, 42, 48);
    private static final Color HAT_SHADE = new Color(140, 24, 34);
    private static final Color HAT_OUTLINE = new Color(64, 12, 18);
    private static final Color FUR = new Color(246, 243, 236);
    private static final Color FUR_SHADE = new Color(190, 198, 204);
    private static final Color SNOW = new Color(226, 238, 246);
    private static final Color WIRE = new Color(52, 84, 62);
    private static final Color[] BULBS = {new Color(236, 76, 72), new Color(244, 196, 84),
        new Color(102, 196, 112), new Color(98, 166, 238)};

    private SeasonalDecoration() {}

    /** The season's emblem: a jack-o'-lantern or a Santa hat. */
    public static Icon icon(int size) {
        return new ImageIcon(image(size));
    }

    public static BufferedImage image(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 30.0, size / 30.0);
        if (UIConstants.season() == Season.CHRISTMAS) paintSantaHat(g);
        else paintPumpkin(g);
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

    private static void paintSantaHat(Graphics2D g) {
        // The cone rises from the brim and flops over to the right, ending in the bobble.
        Path2D hat = new Path2D.Double();
        hat.moveTo(4, 22);
        hat.curveTo(4, 12, 9, 5, 16, 4);
        hat.curveTo(22, 3, 26, 8, 26.5, 15);
        hat.lineTo(23.5, 15.5);
        hat.curveTo(23, 12, 21.5, 10, 19.5, 10);
        hat.curveTo(22, 14, 24, 18, 25, 22);
        hat.closePath();
        g.setColor(HAT_RED);
        g.fill(hat);

        Path2D fold = new Path2D.Double();
        fold.moveTo(19.5, 10);
        fold.curveTo(22, 14, 24, 18, 25, 22);
        fold.lineTo(19, 22);
        fold.curveTo(20, 17, 20, 13, 19.5, 10);
        g.setColor(HAT_SHADE);
        g.fill(fold);
        g.setStroke(new BasicStroke(1));
        g.setColor(HAT_OUTLINE);
        g.draw(hat);

        Ellipse2D bobble = new Ellipse2D.Double(22, 13.5, 7, 7);
        RoundRectangle2D brim = new RoundRectangle2D.Double(2, 20, 26, 7, 6, 6);
        for (Shape fur : new Shape[] {bobble, brim}) {
            g.setColor(FUR);
            g.fill(fur);
            g.setColor(FUR_SHADE);
            g.draw(fur);
        }
        g.setColor(FUR_SHADE);
        g.drawLine(7, 25, 23, 25);
    }

    public static void paintHeader(Graphics graphics, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean christmas = UIConstants.season() == Season.CHRISTMAS;
        Color glow = Colors.withAlpha(christmas ? SNOW : UIConstants.SEASON_ACCENT, christmas ? 10 : 18);
        g.setPaint(new RadialGradientPaint(width, height, Math.max(width, height),
            new float[] {0, 1}, new Color[] {glow, new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, width, height);
        if (christmas) paintSnowfall(g, width, height);
        else paintCobweb(g, width);
        g.dispose();
    }

    /** Still snowflakes from a fixed seed, so the header looks the same on every repaint. */
    private static void paintSnowfall(Graphics2D g, int width, int height) {
        Random flakes = new Random(1225);
        for (int i = 0; i < width * height / 260; i++) {
            double size = 1 + flakes.nextDouble() * 1.6;
            g.setColor(Colors.withAlpha(SNOW, 40 + flakes.nextInt(80)));
            g.fill(new Ellipse2D.Double(flakes.nextDouble() * width, flakes.nextDouble() * height, size, size));
        }
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

    /** The line under the header: a string of lights at Christmas, otherwise the gold separator. */
    public static JComponent separator() {
        if (UIConstants.season() != Season.CHRISTMAS) return new GradientSeparator();
        JPanel lights = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                paintLights((Graphics2D) graphics, getWidth());
            }
        };
        lights.setOpaque(false);
        lights.setPreferredSize(new Dimension(0, 20));
        return lights;
    }

    private static void paintLights(Graphics2D graphics, int width) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int swags = Math.max(2, Math.round(width / 72f)), perSwag = 4;
        double span = width / (double) swags;
        Path2D wire = new Path2D.Double();
        for (int s = 0; s < swags; s++) {
            wire.moveTo(s * span, 2);
            wire.quadTo((s + 0.5) * span, 12, (s + 1) * span, 2);
        }
        g.setColor(WIRE);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(wire);

        for (int s = 0, n = 0; s < swags; s++) {
            for (int b = 0; b < perSwag; b++, n++) {
                double t = (b + 0.5) / perSwag;
                double x = (s + t) * span;
                double y = 2 + 20 * t * (1 - t);
                Color colour = BULBS[n % BULBS.length];
                g.setPaint(new RadialGradientPaint((float) x, (float) y + 5, 7,
                    new float[] {0, 1}, new Color[] {Colors.withAlpha(colour, 90), Colors.withAlpha(colour, 0)}));
                g.fill(new Ellipse2D.Double(x - 7, y - 2, 14, 14));
                g.setColor(WIRE.darker());
                g.fill(new RoundRectangle2D.Double(x - 1.5, y - 0.5, 3, 3, 1, 1));
                g.setColor(colour);
                g.fill(new Ellipse2D.Double(x - 2, y + 2, 4, 6));
                g.setColor(Colors.withAlpha(Color.WHITE, 150));
                g.fill(new Ellipse2D.Double(x - 1, y + 3, 1.3, 2));
            }
        }
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

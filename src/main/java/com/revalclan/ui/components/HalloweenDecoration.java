package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Small vector decorations; no emoji glyphs, external assets, or animation timer. */
public final class HalloweenDecoration {
    private HalloweenDecoration() {}

    public static Icon pumpkinIcon(int size) {
        return new ImageIcon(pumpkinImage(size));
    }

    public static BufferedImage pumpkinImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 30.0, size / 30.0);

        g.setColor(UIConstants.PUMPKIN_STEM);
        fill(g, 14, 8, 16, 2, 20, 3, 17, 9);
        Path2D body = new Path2D.Double();
        body.moveTo(15, 7);
        body.curveTo(4, 1, -3, 23, 9, 27);
        body.curveTo(13, 29, 16, 27, 16, 27);
        body.curveTo(22, 29, 27, 24, 28, 19);
        body.curveTo(31, 10, 23, 2, 15, 7);
        g.setColor(UIConstants.PUMPKIN_BODY);
        g.fill(body);
        g.setStroke(new BasicStroke(1));
        g.setColor(UIConstants.PUMPKIN_OUTLINE);
        g.draw(body);

        Path2D ribs = new Path2D.Double();
        ribs.moveTo(13, 7);
        ribs.curveTo(7, 11, 8, 23, 12, 27);
        ribs.moveTo(17, 7);
        ribs.curveTo(23, 12, 22, 24, 18, 27);
        g.setColor(UIConstants.PUMPKIN_LIGHT);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(ribs);
        g.setColor(UIConstants.PUMPKIN_FACE);
        fill(g, 8, 12, 12, 17, 6, 17);
        fill(g, 21, 12, 24, 17, 18, 17);
        fill(g, 8, 21, 12, 22, 14, 20, 17, 22, 22, 20, 19, 25, 12, 25);
        g.setColor(UIConstants.PUMPKIN_GLOW);
        fill(g, 9, 14, 10, 15, 8, 15);
        fill(g, 21, 14, 22, 15, 20, 15);
        g.drawLine(12, 23, 17, 23);
        g.dispose();
        return image;
    }

    public static void paintHeader(Graphics graphics, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color glow = new Color(UIConstants.HALLOWEEN_ACCENT.getRed(),
            UIConstants.HALLOWEEN_ACCENT.getGreen(), UIConstants.HALLOWEEN_ACCENT.getBlue(), 18);
        g.setPaint(new RadialGradientPaint(width, height, Math.max(width, height),
            new float[] {0, 1}, new Color[] {glow, new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, width, height);

        g.translate(width - 58, 0);
        g.setColor(UIConstants.COBWEB);
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

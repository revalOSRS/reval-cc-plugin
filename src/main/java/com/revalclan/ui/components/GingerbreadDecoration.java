package com.revalclan.ui.components;

import com.revalclan.util.Colors;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/** Static Gingerbread Village artwork, drawn in logical pixels without external assets or timers. */
final class GingerbreadDecoration {
    static final int SCENE_HEIGHT = 52;
    private static final Color SNOW = new Color(0xfff7ee);
    private static final Color SHADOW = new Color(0xd8bfa6);

    private GingerbreadDecoration() {}

    /** Quiet zone behind the title and banner so falling snow never fights the lettering. */
    private static boolean titleZone(double x, double y, int w, int y0) {
        return x > 44 && x < w - 44 && y > 24 && y < y0 - 2;
    }

    private static void snowfall(Graphics2D g, int w, int top, int bottom, int y0, long seed, double per, double maxSize, int lo, int hi) {
        Random r = new Random(seed);
        int count = (int) (w * (bottom - top) / per);
        for (int i = 0; i < count; i++) {
            double x = r.nextDouble() * w, y = top + r.nextDouble() * (bottom - top);
            double s = 0.5 + r.nextDouble() * maxSize;
            int alpha = lo + r.nextInt(Math.max(1, hi - lo));
            if (titleZone(x, y, w, y0)) { s = Math.min(s, 1.0); alpha = alpha / 3; }
            g.setColor(Colors.withAlpha(SNOW, alpha));
            g.fill(new Ellipse2D.Double(x - s / 2, y - s / 2, s, s));
        }
    }

    /** Six-armed flake with little side barbs. */
    private static void flake(Graphics2D g, double x, double y, double radius, Color color) {
        Graphics2D f = (Graphics2D) g.create();
        f.setColor(color);
        f.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        f.translate(x, y);
        for (int k = 0; k < 6; k++) {
            f.draw(new Line2D.Double(0, 0, 0, -radius));
            f.draw(new Line2D.Double(0, -radius * .55, -radius * .3, -radius * .8));
            f.draw(new Line2D.Double(0, -radius * .55, radius * .3, -radius * .8));
            f.rotate(Math.PI / 3);
        }
        f.dispose();
    }

    static void paintHeader(Graphics2D g, int w, int h) {
        int y0 = h - SCENE_HEIGHT - 8;
        g.setPaint(new GradientPaint(0, y0 - 30, Colors.withAlpha(new Color(0x5b3320), 0), 0, y0 + 44, Colors.withAlpha(new Color(0x6a3c22), 95)));
        g.fillRect(0, y0 - 30, w, 104);
        // Royal-icing drips along the very top edge, above the info / lock buttons.
        icing(g, 0, w, 0, 2.2, 31, 3.2, false);
        snowfall(g, w, 4, h, y0, 1225, 175, 1.3, 70, 200);
        flake(g, 14, 44, 3.6, Colors.withAlpha(SNOW, 210)); flake(g, w - 15, 34, 3.2, Colors.withAlpha(SNOW, 190));
        flake(g, 26, 66, 2.4, Colors.withAlpha(SNOW, 150)); flake(g, w - 28, 62, 2.6, Colors.withAlpha(SNOW, 160));

        int gy = y0 + 47;
        // Distant cookie rooftops.
        Color far = new Color(0x3c2619);
        g.setColor(far);
        for (int x = -6; x < w; x += 27) {
            double hh = 10 + Math.floorMod(x * 7, 6);
            poly(g, x, gy - 2, x, gy - hh, x + 10, gy - hh - 8, x + 20, gy - hh, x + 20, gy - 2);
            g.setColor(Colors.withAlpha(SNOW, 120));
            poly(g, x - 1, gy - hh + 1, x + 10, gy - hh - 9, x + 21, gy - hh + 1, x + 10, gy - hh - 5);
            g.setColor(far);
        }
        icedTree(g, 13, gy + 1, 30);
        icedTree(g, w - 14, gy + 1, 28);
        house(g, 31, gy, 40, 17, 14, 4);
        house(g, 97, gy, 48, 22, 18, 9);
        house(g, 171, gy, 40, 17, 14, 2);
        gingerMan(g, 84, gy - 1, 0.42);
        candyCane(g, 157, gy + 1, 15);
        lollipop(g, 77, gy + 1);
        lollipop(g, 164.5, gy + 2);

        // Thick sugar-snow ground, lumpy crest with a soft shadow.
        Path2D ground = new Path2D.Double();
        ground.moveTo(0, h - 4);
        ground.lineTo(0, gy - 1);
        ground.curveTo(18, gy - 5, 32, gy + 1, 52, gy - 1);
        ground.curveTo(76, gy - 4, 92, gy + 2, 121, gy - 1);
        ground.curveTo(146, gy - 4, 166, gy + 2, 190, gy - 2);
        ground.curveTo(210, gy - 5, 228, gy, w, gy - 2);
        hem(ground, w, h - 4); ground.closePath();
        g.setColor(SHADOW); g.fill(ground);
        g.setColor(SNOW); g.fill(AffineTransform.getTranslateInstance(0, 1.6).createTransformedShape(ground));
        gumdrop(g, 44, gy + 4, new Color(0xe65468)); gumdrop(g, 128, gy + 5, new Color(0x62c487));
        gumdrop(g, 205, gy + 4, new Color(0xf4c64e)); gumdrop(g, 6, gy + 7, new Color(0x62c487)); gumdrop(g, 236, gy + 6, new Color(0xe65468));
        snowfall(g, w, y0, gy, y0, 77, 260, 1.1, 60, 170);
    }

    private static void house(Graphics2D g, double x, double gy, double width, double wallH, double roofH, long seed) {
        Color wall = new Color(0xb36c36), edge = new Color(0x70401f), window = new Color(0xf8c867);
        double top = gy - wallH;
        // Chimney first so the roof icing laps over it.
        g.setColor(new Color(0x8d5129));
        g.fill(new Rectangle2D.Double(x + width * .68, top - roofH * .78, 5, roofH * .6));
        g.setColor(SNOW);
        g.fill(new RoundRectangle2D.Double(x + width * .68 - 1, top - roofH * .82, 7, 2.6, 2.6, 2.6));
        g.setColor(wall);
        g.fill(new Rectangle2D.Double(x, top, width, wallH));
        g.setColor(edge); g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(x, top, width, wallH));
        // Icing corner zigzags.
        g.setColor(SNOW); g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D zig = new Path2D.Double(); zig.moveTo(x + 1.5, gy - 3);
        for (double zx = x + 1.5; zx < x + width - 2; zx += 3) zig.lineTo(zx + 1.5, gy - (((int) ((zx - x) / 3)) % 2 == 0 ? 1.5 : 3));
        g.draw(zig);
        // Windows with icing crosses, door with a candy arch.
        double ww = Math.min(8, width * .18);
        for (double wx : new double[]{x + width * .14, x + width * .86 - ww}) {
            g.setColor(Colors.withAlpha(window, 60)); g.fill(new Ellipse2D.Double(wx - 3, top + 2, ww + 6, ww + 6));
            g.setColor(window); g.fill(new Rectangle2D.Double(wx, top + 4, ww, ww));
            g.setColor(SNOW); g.setStroke(new BasicStroke(0.9f));
            g.draw(new Rectangle2D.Double(wx, top + 4, ww, ww));
            g.draw(new Line2D.Double(wx + ww / 2, top + 4, wx + ww / 2, top + 4 + ww));
            g.draw(new Line2D.Double(wx, top + 4 + ww / 2, wx + ww, top + 4 + ww / 2));
        }
        double dw = Math.max(6, width * .17), dx = x + width / 2 - dw / 2, dh = wallH * .62;
        g.setColor(new Color(0x5a3018));
        g.fill(new RoundRectangle2D.Double(dx, gy - dh, dw, dh + 2, dw, dw));
        g.setColor(SNOW); g.setStroke(new BasicStroke(0.9f));
        g.draw(new Arc2D.Double(dx - .8, gy - dh - .8, dw + 1.6, dw + 1.6, 0, 180, Arc2D.OPEN));
        g.setColor(new Color(0xf4c64e)); g.fill(new Ellipse2D.Double(dx + dw - 2.4, gy - dh * .48, 1.4, 1.4));
        // Roof entirely buried in icing, with drips over the eaves.
        double cx = x + width / 2, ex = 4;
        Path2D roof = new Path2D.Double();
        roof.moveTo(x - ex, top + 1.5);
        roof.quadTo(cx - width * .2, top - roofH * .55, cx, top - roofH);
        roof.quadTo(cx + width * .2, top - roofH * .55, x + width + ex, top + 1.5);
        roof.closePath();
        g.setColor(SHADOW); g.fill(AffineTransform.getTranslateInstance(0, 1.4).createTransformedShape(roof));
        g.setColor(SNOW); g.fill(roof);
        Random r = new Random(seed);
        for (double dxp = x - ex + 3; dxp < x + width + ex - 2; dxp += 3.2 + r.nextDouble() * 2.6) {
            double len = 1.5 + r.nextDouble() * 3.4;
            g.setColor(SNOW);
            g.fill(new RoundRectangle2D.Double(dxp - 1, top + 1, 2, len, 2, 2));
            g.fill(new Ellipse2D.Double(dxp - 1.25, top + len - .4, 2.5, 2.5));
        }
        // Gumdrops tucked along the roof.
        Color[] drops = {new Color(0xe65468), new Color(0x62c487), new Color(0xf4c64e), new Color(0x7fb6f0)};
        for (int i = 0; i < 4; i++) {
            double t = (i + .7) / 4.6;
            double px = x - ex + 2 + t * (width + ex * 2 - 4);
            double py = top + 1 - roofH * (1 - Math.abs(px - cx) / (width / 2 + ex)) + 2.2;
            gumdrop(g, px, py, drops[(int) ((i + seed) % 4)]);
        }
        gumdrop(g, cx, top - roofH + 0.6, new Color(0xe65468));
    }

    private static void gumdrop(Graphics2D g, double x, double y, Color color) {
        g.setColor(color.darker());
        g.fill(new Arc2D.Double(x - 2.1, y - 2.4, 4.2, 5, 0, 180, Arc2D.CHORD));
        g.setColor(color);
        g.fill(new Arc2D.Double(x - 1.8, y - 2.2, 3.6, 4.4, 0, 180, Arc2D.CHORD));
        g.setColor(Colors.withAlpha(Color.WHITE, 170));
        g.fill(new Ellipse2D.Double(x - 1, y - 1.7, 1, 1));
    }

    private static void icedTree(Graphics2D g, double x, double bottom, double height) {
        Color pine = new Color(0x3f7c5c);
        for (int level = 0; level < 3; level++) {
            double y = bottom - height + level * height * .24, half = height * (.19 + level * .1), tierH = height * .44;
            g.setColor(pine);
            poly(g, x, y, x - half, y + tierH, x + half, y + tierH);
            g.setColor(SNOW); g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D garland = new Path2D.Double();
            garland.moveTo(x - half + .8, y + tierH - .4);
            int bumps = 3 + level;
            for (int b = 0; b < bumps; b++) {
                double sx = x - half + .8 + (2 * half - 1.6) * b / bumps, ex = x - half + .8 + (2 * half - 1.6) * (b + 1) / bumps;
                garland.quadTo((sx + ex) / 2, y + tierH + 1.6, ex, y + tierH - .4);
            }
            g.draw(garland);
            g.fill(new Arc2D.Double(x - half * .45, y - .5, half * .9, tierH * .5, 180, 180, Arc2D.CHORD));
            poly(g, x, y - .5, x - half * .32, y + tierH * .3, x + half * .32, y + tierH * .3);
        }
        g.setColor(new Color(0xf4c64e));
        star(g, x, bottom - height - .5, 2.4);
    }

    private static void candyCane(Graphics2D g, double x, double bottom, double height) {
        Path2D cane = new Path2D.Double();
        cane.moveTo(x, bottom);
        cane.lineTo(x, bottom - height + 3);
        cane.curveTo(x, bottom - height - 1.5, x + 6, bottom - height - 1.5, x + 6, bottom - height + 2.5);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE); g.draw(cane);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{1.6f, 1.8f}, 0));
        g.setColor(new Color(0xdc3a4c)); g.draw(cane);
    }

    private static void lollipop(Graphics2D g, double x, double bottom) {
        g.setColor(new Color(0xf3e7da)); g.setStroke(new BasicStroke(0.9f));
        g.draw(new Line2D.Double(x, bottom, x, bottom - 10));
        g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(x - 3.4, bottom - 16.4, 6.8, 6.8));
        g.setColor(new Color(0xdc3a4c)); g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D swirl = new Path2D.Double();
        for (int i = 0; i <= 40; i++) {
            double t = i / 40.0 * Math.PI * 3.2, r = 0.25 + t * 0.29;
            double px = x + Math.cos(t) * r, py = bottom - 13 + Math.sin(t) * r;
            if (i == 0) swirl.moveTo(px, py); else swirl.lineTo(px, py);
        }
        g.draw(swirl);
    }

    private static Area gingerShape() {
        Area body = new Area(new Ellipse2D.Double(10, 1.5, 10, 10));
        body.add(new Area(new RoundRectangle2D.Double(9, 9.5, 12, 11, 6, 6)));
        body.add(new Area(new RoundRectangle2D.Double(2, 11, 26, 5.6, 5.6, 5.6)));
        Shape leg = new RoundRectangle2D.Double(9.3, 16, 5.6, 12.4, 5.6, 5.6);
        body.add(new Area(AffineTransform.getRotateInstance(Math.toRadians(14), 12, 18).createTransformedShape(leg)));
        Shape leg2 = new RoundRectangle2D.Double(15.1, 16, 5.6, 12.4, 5.6, 5.6);
        body.add(new Area(AffineTransform.getRotateInstance(Math.toRadians(-14), 18, 18).createTransformedShape(leg2)));
        return body;
    }

    /** Gingerbread man in a 30x30 box, scaled and placed with its feet at (x, bottom). */
    private static void gingerMan(Graphics2D graphics, double x, double bottom, double scale) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x - 15 * scale, bottom - 29 * scale);
        g.scale(scale, scale);
        paintGingerMan(g, scale < .6);
        g.dispose();
    }

    private static void paintGingerMan(Graphics2D g, boolean tiny) {
        Area shape = gingerShape();
        g.setColor(new Color(0xc47b3f)); g.fill(shape);
        g.setColor(new Color(0x6a3717)); g.setStroke(new BasicStroke(tiny ? 1.6f : 1.1f)); g.draw(shape);
        g.setColor(SNOW); g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[] z : new double[][]{{4.2, 11.6}, {25.8, 11.6}}) {
            Path2D p = new Path2D.Double(); p.moveTo(z[0] - .7, z[1]);
            p.lineTo(z[0] + .7, z[1] + 1.4); p.lineTo(z[0] - .7, z[1] + 2.8); p.lineTo(z[0] + .7, z[1] + 4.2);
            g.draw(p);
        }
        g.draw(new Line2D.Double(9.3, 26, 12.4, 26.8));
        g.draw(new Line2D.Double(20.7, 26, 17.6, 26.8));
        g.fill(new Ellipse2D.Double(12.2, 4.6, 2, 2));
        g.fill(new Ellipse2D.Double(15.8, 4.6, 2, 2));
        g.draw(new Arc2D.Double(12.6, 5.4, 4.8, 3.6, 200, 140, Arc2D.OPEN));
        g.setColor(new Color(0xe2465a)); g.fill(new Ellipse2D.Double(13.6, 12.2, 2.8, 2.8));
        g.setColor(new Color(0x4fb779)); g.fill(new Ellipse2D.Double(13.6, 16.4, 2.8, 2.8));
    }

    static void paintEmblem(Graphics2D g) {
        paintGingerMan(g, false);
    }

    /** Royal icing band with rounded drips; used on the header, tabs and cards. */
    static void icing(Graphics2D g, double x, double w, double y, double band, long seed, double maxDrip, boolean sprinkles) {
        Random r = new Random(seed);
        // Fill overlapping subpaths together; boolean unions of these curves stall UI repaints.
        Path2D icing = new Path2D.Double(Path2D.WIND_NON_ZERO);
        icing.append(new Rectangle2D.Double(x, y - 3, w, band + 3), false);
        double cx = x;
        while (cx < x + w) {
            double seg = 3.2 + r.nextDouble() * 3.2;
            icing.append(new Ellipse2D.Double(cx, y + band - 1.4, seg, 2.6), false);
            cx += seg;
        }
        for (double dx = x + 4 + r.nextDouble() * 4; dx < x + w - 4; dx += 5 + r.nextDouble() * 9) {
            double len = 1 + r.nextDouble() * maxDrip, dw = 1.7 + r.nextDouble() * .7;
            icing.append(new RoundRectangle2D.Double(dx - dw / 2, y + band - .5, dw, len, dw, dw), false);
            icing.append(new Ellipse2D.Double(dx - dw * .62, y + band + len - dw * .9, dw * 1.24, dw * 1.24), false);
        }
        g.setColor(Colors.withAlpha(SHADOW, 190));
        g.fill(AffineTransform.getTranslateInstance(0, .7).createTransformedShape(icing));
        g.setColor(SNOW);
        g.fill(icing);
        g.setColor(Colors.withAlpha(Color.WHITE, 200));
        g.fill(new Rectangle2D.Double(x, y, w, .6));
        if (sprinkles) {
            Color[] cs = {new Color(0xe65468), new Color(0x62c487), new Color(0xf4c64e), new Color(0x7fb6f0)};
            for (int i = 0; i < Math.max(2, w / 26); i++) {
                double sx = x + 4 + r.nextDouble() * (w - 8), sy = y + .4 + r.nextDouble() * Math.max(.2, band - 1);
                Graphics2D s = (Graphics2D) g.create();
                s.setColor(cs[i % 4]);
                s.rotate(r.nextDouble() * Math.PI, sx, sy);
                s.fill(new RoundRectangle2D.Double(sx - 1.1, sy - .4, 2.2, .8, .8, .8));
                s.dispose();
            }
        }
    }

    /** Gently scalloped lower edge for a snow field, run right to left. */
    private static void hem(Path2D p, int w, double y) {
        p.lineTo(w, y);
        for (double x = w; x > 0; x -= 22) p.quadTo(x - 11, y + 1.6, Math.max(0, x - 22), y);
    }

    private static void star(Graphics2D g, double x, double y, double r) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? r : r * .45;
            double px = x + Math.cos(ang) * rr, py = y + Math.sin(ang) * rr;
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.closePath(); g.fill(p);
    }

    private static void poly(Graphics2D g, double... coords) {
        Path2D p = new Path2D.Double();
        p.moveTo(coords[0], coords[1]);
        for (int i = 2; i < coords.length; i += 2) p.lineTo(coords[i], coords[i + 1]);
        p.closePath();
        g.fill(p);
    }
}

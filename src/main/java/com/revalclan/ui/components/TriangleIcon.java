package com.revalclan.ui.components;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;

/**
 * Expand/collapse triangle drawn in code — the RuneScape font has no
 * "▼"/"▲" glyphs, which rendered as a missing-glyph box on macOS.
 */
public class TriangleIcon implements Icon {
	private final boolean down;
	private final int size;
	private final Color color;

	public TriangleIcon(boolean down, int size, Color color) {
		this.down = down;
		this.size = size;
		this.color = color;
	}

	@Override
	public int getIconWidth() {
		return size;
	}

	@Override
	public int getIconHeight() {
		return size;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(color);
		Polygon triangle = new Polygon();
		if (down) {
			triangle.addPoint(x, y + 2);
			triangle.addPoint(x + size - 1, y + 2);
			triangle.addPoint(x + (size - 1) / 2, y + size - 2);
		} else {
			triangle.addPoint(x, y + size - 2);
			triangle.addPoint(x + size - 1, y + size - 2);
			triangle.addPoint(x + (size - 1) / 2, y + 2);
		}
		g2d.fillPolygon(triangle);
		g2d.dispose();
	}
}

package com.revalclan.ui.components;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;

/**
 * Expand/collapse triangle drawn in code — the RuneScape font has no
 * "▼"/"▲" glyphs, which rendered as a missing-glyph box on macOS.
 */
public class TriangleIcon extends PaintedIcon {
	private final boolean down;

	public TriangleIcon(boolean down, int size, Color color) {
		super(size, color);
		this.down = down;
	}

	@Override
	void paint(Graphics2D g2d, int x, int y) {
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
	}
}

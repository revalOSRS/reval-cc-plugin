package com.revalclan.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Check mark drawn in code — the RuneScape font has no "✓" glyph, which
 * rendered as a missing-glyph box on macOS.
 */
public class CheckIcon extends PaintedIcon {
	public CheckIcon(int size, Color color) {
		super(size, color);
	}

	@Override
	void paint(Graphics2D g2d, int x, int y) {
		g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int[] xs = {x + size / 5, x + (int) (size * 0.42), x + size - size / 6};
		int[] ys = {y + (int) (size * 0.55), y + size - size / 5, y + size / 5};
		g2d.drawPolyline(xs, ys, 3);
	}
}

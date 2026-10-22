package com.revalclan.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Right-pointing arrow drawn in code — the RuneScape font has no "→" glyph,
 * which rendered as a missing-glyph box on macOS.
 */
public class ArrowIcon extends PaintedIcon {
	public ArrowIcon(int size, Color color) {
		super(size, color);
	}

	@Override
	void paint(Graphics2D g2d, int x, int y) {
		g2d.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		int midY = y + size / 2;
		int x0 = x + 1;
		int x1 = x + size - 2;
		int head = Math.max(3, size / 3);

		g2d.drawLine(x0, midY, x1, midY);
		g2d.drawLine(x1 - head, midY - head, x1, midY);
		g2d.drawLine(x1 - head, midY + head, x1, midY);
	}
}

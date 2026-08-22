package com.revalclan.ui.components;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Check mark drawn in code — the RuneScape font has no "✓" glyph, which
 * rendered as a missing-glyph box on macOS.
 */
public class CheckIcon implements Icon {
	private final int size;
	private final Color color;

	public CheckIcon(int size, Color color) {
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
		g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int[] xs = {x + size / 5, x + (int) (size * 0.42), x + size - size / 6};
		int[] ys = {y + (int) (size * 0.55), y + size - size / 5, y + size / 5};
		g2d.drawPolyline(xs, ys, 3);
		g2d.dispose();
	}
}

package com.revalclan.ui.components;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Right-pointing arrow drawn in code — the RuneScape font has no "→" glyph,
 * which rendered as a missing-glyph box on macOS.
 */
public class ArrowIcon implements Icon {
	private final int size;
	private final Color color;

	public ArrowIcon(int size, Color color) {
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
		g2d.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		int midY = y + size / 2;
		int x0 = x + 1;
		int x1 = x + size - 2;
		int head = Math.max(3, size / 3);

		g2d.drawLine(x0, midY, x1, midY);
		g2d.drawLine(x1 - head, midY - head, x1, midY);
		g2d.drawLine(x1 - head, midY + head, x1, midY);
		g2d.dispose();
	}
}

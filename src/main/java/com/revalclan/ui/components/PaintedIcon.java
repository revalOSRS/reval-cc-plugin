package com.revalclan.ui.components;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Square single-colour icon drawn in code, for glyphs the RuneScape fonts lack. */
abstract class PaintedIcon implements Icon {
	final int size;
	private final Color color;

	PaintedIcon(int size, Color color) {
		this.size = size;
		this.color = color;
	}

	/** Draws the shape at (x, y) on an antialiased copy whose colour is already set. */
	abstract void paint(Graphics2D g2d, int x, int y);

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(color);
		paint(g2d, x, y);
		g2d.dispose();
	}

	@Override
	public int getIconWidth() {
		return size;
	}

	@Override
	public int getIconHeight() {
		return size;
	}
}

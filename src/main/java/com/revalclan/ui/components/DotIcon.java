package com.revalclan.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Filled dot when done, hollow ring otherwise. An Icon so a label centers it
 * with its text; the RuneScape font has no ✓/○ glyphs.
 */
public class DotIcon extends PaintedIcon {
	private final boolean done;

	public DotIcon(boolean done, int size, Color doneColor, Color pendingColor) {
		super(size, done ? doneColor : pendingColor);
		this.done = done;
	}

	@Override
	void paint(Graphics2D g2, int x, int y) {
		if (done) {
			g2.fillOval(x, y, size, size);
		} else {
			g2.setStroke(new BasicStroke(1.5f));
			g2.drawOval(x + 1, y + 1, size - 2, size - 2);
		}
	}
}

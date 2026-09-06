package com.revalclan.ui.components;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Filled dot when done, hollow ring otherwise. An Icon so a label centers it
 * with its text; the RuneScape font has no ✓/○ glyphs.
 */
public class DotIcon implements Icon {
	private final boolean done;
	private final int size;
	private final Color doneColor;
	private final Color pendingColor;

	public DotIcon(boolean done, int size, Color doneColor, Color pendingColor) {
		this.done = done;
		this.size = size;
		this.doneColor = doneColor;
		this.pendingColor = pendingColor;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (done) {
			g2.setColor(doneColor);
			g2.fillOval(x, y, size, size);
		} else {
			g2.setColor(pendingColor);
			g2.setStroke(new BasicStroke(1.5f));
			g2.drawOval(x + 1, y + 1, size - 2, size - 2);
		}
		g2.dispose();
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

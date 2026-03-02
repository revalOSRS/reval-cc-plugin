package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import java.awt.*;

/**
 * A JButton subclass that paints a small colored dot indicator
 * in the top-right corner to signal active/upcoming content.
 */
public class IndicatorTabButton extends JButton {
	private boolean indicatorVisible = false;
	private Color indicatorColor = UIConstants.ACCENT_GOLD;

	public IndicatorTabButton(String text) {
		super(text);
	}

	public void setIndicator(boolean visible, Color color) {
		if (this.indicatorVisible == visible && this.indicatorColor.equals(color)) return;
		this.indicatorVisible = visible;
		this.indicatorColor = color;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (indicatorVisible) {
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int size = 7;
			int x = getWidth() - size - 3;
			int y = 3;
			g2d.setColor(new Color(indicatorColor.getRed(), indicatorColor.getGreen(), indicatorColor.getBlue(), 80));
			g2d.fillOval(x - 1, y - 1, size + 2, size + 2);
			g2d.setColor(indicatorColor);
			g2d.fillOval(x, y, size, size);
			g2d.dispose();
		}
	}
}

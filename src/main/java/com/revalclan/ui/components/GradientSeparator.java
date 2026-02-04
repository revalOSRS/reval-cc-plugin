package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import java.awt.*;

public class GradientSeparator extends JPanel {
	public GradientSeparator() {
		setPreferredSize(new Dimension(0, 3));
		setBackground(UIConstants.BACKGROUND);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int width = getWidth();
		int centerX = width / 2;

		GradientPaint gradient = new GradientPaint(
			centerX - 60, 0, new Color(UIConstants.ACCENT_GOLD.getRed(), UIConstants.ACCENT_GOLD.getGreen(), UIConstants.ACCENT_GOLD.getBlue(), 0),
			centerX, 0, UIConstants.ACCENT_GOLD
		);
		g2d.setPaint(gradient);
		g2d.fillRect(centerX - 60, 1, 60, 1);

		GradientPaint gradient2 = new GradientPaint(
			centerX, 0, UIConstants.ACCENT_GOLD,
			centerX + 60, 0, new Color(UIConstants.ACCENT_GOLD.getRed(), UIConstants.ACCENT_GOLD.getGreen(), UIConstants.ACCENT_GOLD.getBlue(), 0)
		);
		g2d.setPaint(gradient2);
		g2d.fillRect(centerX, 1, 60, 1);

		g2d.dispose();
	}
}

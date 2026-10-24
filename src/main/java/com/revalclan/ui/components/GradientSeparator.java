package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;

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

		int centerX = getWidth() / 2;
		// Gold fading out to both sides of the centre
		Color clear = Colors.withAlpha(UIConstants.ACCENT_GOLD, 0);

		g2d.setPaint(new GradientPaint(centerX - 60, 0, clear, centerX, 0, UIConstants.ACCENT_GOLD));
		g2d.fillRect(centerX - 60, 1, 60, 1);

		g2d.setPaint(new GradientPaint(centerX, 0, UIConstants.ACCENT_GOLD, centerX + 60, 0, clear));
		g2d.fillRect(centerX, 1, 60, 1);

		g2d.dispose();
	}
}

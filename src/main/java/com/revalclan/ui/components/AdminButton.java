package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Small header button that shows a lock icon.
 * Gold background when admin, dark/muted when not.
 */
public class AdminButton extends JButton {
	private boolean isAdmin = false;

	public AdminButton() {
		setPreferredSize(new Dimension(22, 22));
		setMaximumSize(new Dimension(22, 22));
		setMinimumSize(new Dimension(22, 22));
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setEnabled(false);
	}

	public void setAdmin(boolean admin) {
		this.isAdmin = admin;
		setEnabled(admin);
		setCursor(admin ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
		setToolTipText(admin ? "Admin" : null);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();

		// Background
		if (!isAdmin) {
			g2d.setColor(UIConstants.CARD_BG);
		} else if (getModel().isPressed()) {
			g2d.setColor(UIConstants.ACCENT_GOLD.darker());
		} else if (getModel().isRollover()) {
			g2d.setColor(UIConstants.ACCENT_GOLD.brighter());
		} else {
			g2d.setColor(UIConstants.ACCENT_GOLD);
		}
		g2d.fillRoundRect(0, 0, w, h, 6, 6);

		// Lock icon — scale to fit within the button
		float s = w / 22f;
		g2d.setColor(isAdmin ? Color.BLACK : UIConstants.TEXT_MUTED);
		g2d.setStroke(new BasicStroke(1.6f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		// Body (rounded rect)
		g2d.draw(new RoundRectangle2D.Float(6 * s, 10 * s, 10 * s, 8 * s, 2 * s, 2 * s));

		// Shackle (arc on top)
		Path2D shackle = new Path2D.Float();
		shackle.moveTo(8 * s, 10 * s);
		shackle.curveTo(8 * s, 6 * s, 11 * s, 4 * s, 11 * s, 4 * s);
		shackle.curveTo(11 * s, 4 * s, 14 * s, 4 * s, 14 * s, 10 * s);
		g2d.draw(shackle);

		g2d.dispose();
	}
}

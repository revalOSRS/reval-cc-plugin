package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;
import net.runelite.client.ui.FontManager;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Solid rounded action button in an accent colour (Register, Unlock board, ...). */
public class AccentButton extends JButton {
	private final Color color;

	public AccentButton(String text, Color color) {
		super(text);
		this.color = color;
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(UIConstants.TEXT_PRIMARY);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setFocusPainted(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Color bg = !isEnabled() ? Colors.withAlpha(color, 100)
			: getModel().isPressed() ? color.darker()
			: getModel().isRollover() ? color.brighter()
			: color;
		g2d.setColor(bg);
		g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
		g2d.dispose();
		super.paintComponent(g);
	}
}

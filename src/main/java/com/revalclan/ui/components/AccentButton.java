package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;
import net.runelite.client.ui.FontManager;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;

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
		Color bg = !isEnabled() ? Colors.withAlpha(color, 100)
			: getModel().isPressed() ? color.darker()
			: getModel().isRollover() ? color.brighter()
			: color;
		RoundedPanel.fill(g, this, bg, 8);
		super.paintComponent(g);
	}
}

package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.ui.FontManager;

import javax.swing.JButton;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Full-width rounded outline button for primary side panel actions. */
public class BlockButton extends JButton {
	private final Color border;

	public BlockButton(String text, Color accent, int height) {
		super(text);
		this.border = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120);
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(accent);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setHorizontalAlignment(SwingConstants.CENTER);
		setPreferredSize(new Dimension(100, height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(getModel().isRollover() && isEnabled() ? UIConstants.CARD_HOVER : UIConstants.BACKGROUND);
		g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
		g2d.setColor(border);
		g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
		g2d.dispose();
		super.paintComponent(g);
	}
}

package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Small borderless text button with a rounded fill on hover (Back, Refresh). */
class FlatButton extends JButton {
	FlatButton(String text) {
		super(text);
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(UIConstants.TEXT_PRIMARY);
		setBorder(new EmptyBorder(4, 8, 4, 8));
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setVerticalAlignment(SwingConstants.CENTER);
		setVerticalTextPosition(SwingConstants.CENTER);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	/** Whether the hover fill is painted. */
	boolean showsHover() {
		return getModel().isRollover();
	}

	@Override
	protected void paintComponent(Graphics g) {
		if (showsHover()) {
			RoundedPanel.fill(g, this, UIConstants.CARD_HOVER, 6);
		}
		super.paintComponent(g);
	}
}

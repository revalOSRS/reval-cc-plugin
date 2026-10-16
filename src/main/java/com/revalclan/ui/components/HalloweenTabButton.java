package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.JButton;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/** Navigation button with a rounded pumpkin background during Halloween. */
public class HalloweenTabButton extends JButton {

	public HalloweenTabButton(String text) {
		super(text);
		if (UIConstants.isHalloweenTheme()) {
			setContentAreaFilled(false);
			setOpaque(false);
			setRolloverEnabled(true);
			setMargin(new Insets(0, 2, 0, 2));
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		if (UIConstants.isHalloweenTheme()) {
			Graphics2D background = (Graphics2D) g.create();
			background.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			background.setColor(getModel().isRollover() && !isSelected()
				? UIConstants.CARD_HOVER : getBackground());
			background.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
			if (isSelected()) {
				background.setColor(UIConstants.HALLOWEEN_TAB_BORDER);
				background.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
			}
			background.dispose();
		}
		super.paintComponent(g);
	}
}

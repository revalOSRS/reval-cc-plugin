package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class RefreshButton extends JButton {
	private boolean loading = false;

	public RefreshButton(Runnable onRefresh) {
		super("Refresh");
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(UIConstants.TEXT_PRIMARY);
		setBorder(new EmptyBorder(4, 8, 4, 8));
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setVerticalAlignment(SwingConstants.CENTER);
		setVerticalTextPosition(SwingConstants.CENTER);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addActionListener(e -> {
			if (!loading) onRefresh.run();
		});
	}

	public void setLoading(boolean loading) {
		this.loading = loading;
		setText(loading ? "Loading..." : "Refresh");
		setEnabled(!loading);
	}

	@Override
	protected void paintComponent(Graphics g) {
		if (getModel().isRollover() && !loading) {
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setColor(UIConstants.CARD_HOVER);
			g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
			g2d.dispose();
		}
		super.paintComponent(g);
	}
}

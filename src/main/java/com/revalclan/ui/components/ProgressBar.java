package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Thin rounded progress bar that stretches to its container's width. */
public class ProgressBar extends JComponent {
	private static final int HEIGHT = 6;
	private final int percent;
	private final Color color;

	public ProgressBar(int percent, Color color) {
		this.percent = Math.max(0, Math.min(100, percent));
		this.color = color;
		setPreferredSize(new Dimension(100, HEIGHT));
		setMinimumSize(new Dimension(20, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(UIConstants.PROGRESS_BG);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), HEIGHT, HEIGHT);
		int w = (int) Math.round(getWidth() * percent / 100d);
		if (w > 0) {
			g2.setColor(color);
			g2.fillRoundRect(0, 0, Math.max(w, HEIGHT), getHeight(), HEIGHT, HEIGHT);
		}
		g2.dispose();
	}
}

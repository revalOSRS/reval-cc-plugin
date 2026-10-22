package com.revalclan.ui.components;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** Non-opaque panel that paints its background colour as an antialiased rounded rectangle. */
public class RoundedPanel extends JPanel {
	private final int arc;

	public RoundedPanel(int arc) {
		this(new FlowLayout(), arc);
	}

	public RoundedPanel(LayoutManager layout, int arc) {
		super(layout);
		this.arc = arc;
		setOpaque(false);
	}

	@Override
	protected void paintComponent(Graphics g) {
		fill(g, this, getBackground(), arc);
	}

	/** Fills the component's bounds with a rounded rectangle, for components that paint their own background. */
	public static void fill(Graphics g, Component c, Color color, int arc) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(color);
		g2d.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
		g2d.dispose();
	}
}

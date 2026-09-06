package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** Rounded card with a left accent bar, stacking its children vertically; optionally hover-highlighted. */
public class AccentCard extends JPanel {
	private final Color accent;
	private final boolean hoverable;
	private boolean hovered;
	private boolean dim;

	public AccentCard(Color accent, boolean hoverable) {
		this.accent = accent;
		this.hoverable = hoverable;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setBorder(new EmptyBorder(9, 14, 9, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	public void setHovered(boolean hovered) {
		this.hovered = hoverable && hovered;
		repaint();
	}

	/** Fades the accent bar, for cards describing something locked. */
	public void setDim(boolean dim) {
		this.dim = dim;
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(hovered ? UIConstants.CARD_HOVER : UIConstants.CARD_BG);
		g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
		g2.setColor(UIConstants.BORDER_COLOR);
		g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 10, 10));
		g2.setColor(dim ? Colors.withAlpha(accent, 90) : accent);
		g2.fillRoundRect(0, 6, 4, getHeight() - 12, 4, 4);
		g2.dispose();
	}
}

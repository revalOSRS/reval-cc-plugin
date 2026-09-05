package com.revalclan.ui.leaguesbingo;

import com.revalclan.api.leaguesbingo.LeaguesBingoResponse;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.ui.FontManager;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * One square of a Leagues Bingo board. Paints the same states the homepage
 * uses: completed (green), in progress (amber bar), untouched, filler for an
 * empty position, and a lock while tiles are still hidden.
 */
public class TileCell extends JComponent {
	public enum State { COMPLETED, IN_PROGRESS, NOT_STARTED, FILLER, HIDDEN }

	static final Color COMPLETED = new Color(76, 175, 80);
	static final Color IN_PROGRESS = new Color(245, 158, 11);

	private final LeaguesBingoResponse.Tile tile;
	private final State state;
	private final int percent;
	private final Color accent;
	private final int size;

	private BufferedImage icon;
	private boolean hovered;
	private boolean selected;
	private boolean dimmed;

	public TileCell(LeaguesBingoResponse.Tile tile, State state, int percent, Color accent, int size) {
		this.tile = tile;
		this.state = state;
		this.percent = percent;
		this.accent = accent;
		this.size = size;
		Dimension d = new Dimension(size, size);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
		setOpaque(false);
		if (tile != null) {
			setToolTipText(tile.getTask());
		}
	}

	public LeaguesBingoResponse.Tile getTile() {
		return tile;
	}

	public void setIcon(BufferedImage icon) {
		this.icon = icon;
		repaint();
	}

	public void setHovered(boolean hovered) {
		this.hovered = hovered;
		repaint();
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
		repaint();
	}

	public void setDimmed(boolean dimmed) {
		this.dimmed = dimmed;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		if (dimmed) {
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
		}

		int w = getWidth();
		int h = getHeight();
		int arc = Math.max(6, size / 6);
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc);

		Color fill;
		Color border;
		switch (state) {
			case COMPLETED:
				fill = withAlpha(COMPLETED, hovered ? 70 : 45);
				border = withAlpha(COMPLETED, 150);
				break;
			case IN_PROGRESS:
				fill = withAlpha(IN_PROGRESS, hovered ? 55 : 30);
				border = withAlpha(IN_PROGRESS, 130);
				break;
			case FILLER:
				fill = UIConstants.ROW_BG;
				border = withAlpha(UIConstants.BORDER_COLOR, 90);
				break;
			case HIDDEN:
				fill = UIConstants.ROW_BG;
				border = UIConstants.BORDER_COLOR;
				break;
			default:
				fill = hovered ? UIConstants.CARD_HOVER : UIConstants.CARD_BG;
				border = UIConstants.BORDER_COLOR;
		}

		g2.setColor(fill);
		g2.fill(shape);

		if (state == State.FILLER) {
			paintWeave(g2, w, h, arc);
		} else if (state == State.HIDDEN) {
			paintLock(g2, w, h);
		} else if (icon != null) {
			paintIcon(g2, w, h);
		} else if (tile != null) {
			paintPosition(g2, w, h);
		}

		if (state == State.IN_PROGRESS) {
			int barH = Math.max(3, size / 14);
			int barW = (int) Math.round((w - 6) * Math.max(percent, 8) / 100d);
			g2.setColor(withAlpha(IN_PROGRESS, 60));
			g2.fillRoundRect(3, h - barH - 3, w - 6, barH, barH, barH);
			g2.setColor(IN_PROGRESS);
			g2.fillRoundRect(3, h - barH - 3, barW, barH, barH, barH);
		}

		g2.setStroke(new BasicStroke(selected ? 2f : 1f));
		g2.setColor(selected ? accent : border);
		g2.draw(shape);

		if (state == State.COMPLETED) {
			paintCheck(g2, w);
		}

		g2.dispose();
	}

	private void paintIcon(Graphics2D g2, int w, int h) {
		int box = (int) Math.round(Math.min(w, h) * 0.66);
		double scale = Math.min((double) box / icon.getWidth(), (double) box / icon.getHeight());
		int iw = Math.max(1, (int) Math.round(icon.getWidth() * scale));
		int ih = Math.max(1, (int) Math.round(icon.getHeight() * scale));
		int x = (w - iw) / 2;
		int y = (h - ih) / 2 - (state == State.IN_PROGRESS ? 2 : 0);
		g2.drawImage(icon, x, y, iw, ih, null);
	}

	private void paintPosition(Graphics2D g2, int w, int h) {
		g2.setFont(FontManager.getRunescapeSmallFont());
		g2.setColor(UIConstants.TEXT_MUTED);
		FontMetrics fm = g2.getFontMetrics();
		String text = tile.getPosition() != null ? tile.getPosition() : "";
		g2.drawString(text, (w - fm.stringWidth(text)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
	}

	private void paintWeave(Graphics2D g2, int w, int h, int arc) {
		g2.setClip(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc, arc));
		g2.setColor(withAlpha(accent, 22));
		g2.setStroke(new BasicStroke(1f));
		for (int i = -h; i < w + h; i += 6) {
			g2.drawLine(i, h, i + h, 0);
		}
		g2.setClip(null);
	}

	private void paintLock(Graphics2D g2, int w, int h) {
		int bw = Math.max(8, size / 4);
		int bh = Math.max(6, size / 5);
		int x = (w - bw) / 2;
		int y = (h - bh) / 2 + bh / 4;
		g2.setColor(UIConstants.TEXT_MUTED);
		g2.fillRoundRect(x, y, bw, bh, 3, 3);
		g2.setStroke(new BasicStroke(2f));
		int r = bw / 2 - 1;
		g2.drawArc(x + 1, y - r, bw - 2, r * 2, 0, 180);
	}

	private void paintCheck(Graphics2D g2, int w) {
		int d = Math.max(10, size / 4);
		int x = w - d - 2;
		int y = 2;
		g2.setComposite(AlphaComposite.SrcOver);
		g2.setColor(COMPLETED);
		g2.fillOval(x, y, d, d);
		g2.setColor(Color.WHITE);
		g2.setStroke(new BasicStroke(Math.max(1.5f, d / 7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int cx = x + d / 2;
		int cy = y + d / 2;
		g2.drawLine(cx - d / 4, cy, cx - d / 12, cy + d / 4);
		g2.drawLine(cx - d / 12, cy + d / 4, cx + d / 4, cy - d / 4);
	}

	static Color withAlpha(Color c, int alpha) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}

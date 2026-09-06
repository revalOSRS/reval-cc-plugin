package com.revalclan.ui.leaguesbingo;

import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Tile;
import com.revalclan.ui.components.CheckIcon;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;
import net.runelite.client.ui.FontManager;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * One square of a Leagues Bingo board. Paints the same states the homepage
 * uses: completed (green), in progress (amber bar), untouched, filler for an
 * empty position, and a lock while tiles are still hidden.
 */
public class TileCell extends JComponent {
	public enum State { COMPLETED, IN_PROGRESS, NOT_STARTED, FILLER, HIDDEN }

	public static final Color COMPLETED = new Color(76, 175, 80);
	public static final Color IN_PROGRESS = new Color(245, 158, 11);

	private final Tile tile;
	private final State state;
	private final int percent;
	private final Color accent;
	private final int size;

	private Image icon;
	private boolean hovered;
	private boolean selected;
	private boolean dimmed;

	public TileCell(Tile tile, State state, int percent, Color accent, int size) {
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

	public Tile getTile() {
		return tile;
	}

	public void setIcon(Image icon) {
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
				fill = Colors.withAlpha(COMPLETED, hovered ? 70 : 45);
				border = Colors.withAlpha(COMPLETED, 150);
				break;
			case IN_PROGRESS:
				fill = Colors.withAlpha(IN_PROGRESS, hovered ? 55 : 30);
				border = Colors.withAlpha(IN_PROGRESS, 130);
				break;
			case FILLER:
				fill = UIConstants.ROW_BG;
				border = Colors.withAlpha(UIConstants.BORDER_COLOR, 90);
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
			int barW = (int) Math.round((w - 6) * Math.max(percent, BoardStats.MIN_VISIBLE_PERCENT) / 100d);
			g2.setColor(Colors.withAlpha(IN_PROGRESS, 60));
			g2.fillRoundRect(3, h - barH - 3, w - 6, barH, barH, barH);
			g2.setColor(IN_PROGRESS);
			g2.fillRoundRect(3, h - barH - 3, barW, barH, barH, barH);
		}

		g2.setStroke(new BasicStroke(selected ? 2f : 1f));
		g2.setColor(selected ? accent : border);
		g2.draw(shape);

		if (state == State.COMPLETED) {
			paintCheckBadge(g2, w);
		}

		g2.dispose();
	}

	private void paintIcon(Graphics2D g2, int w, int h) {
		int box = (int) Math.round(Math.min(w, h) * 0.72);
		// Item sprites from the game cache are 36x32 pixel art: keep them 1:1
		// whenever they fit, and only shrink them on tiny boards.
		int srcW = Math.max(1, icon.getWidth(null));
		int srcH = Math.max(1, icon.getHeight(null));
		double scale = Math.min(1d, Math.min((double) box / srcW, (double) box / srcH));
		int iw = Math.max(1, (int) Math.round(srcW * scale));
		int ih = Math.max(1, (int) Math.round(srcH * scale));
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
		g2.setColor(Colors.withAlpha(accent, 22));
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

	/** Green disc in the top-right corner carrying the shared check mark. */
	private void paintCheckBadge(Graphics2D g2, int w) {
		int d = Math.max(10, size / 4);
		int x = w - d - 2;
		int y = 2;
		g2.setComposite(AlphaComposite.SrcOver);
		g2.setColor(COMPLETED);
		g2.fillOval(x, y, d, d);
		int inset = Math.max(2, d / 5);
		new CheckIcon(d - inset * 2, Color.WHITE).paintIcon(this, g2, x + inset, y + inset);
	}
}

package com.revalclan.playercards;

import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.NumberFmt;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Trading-card style profile rendered in-game: the whole client dims and the
 * card draws centered on the canvas (OSRS TCG pack-opening style), fading in.
 * A switcher row under the card cycles between designs; clicks elsewhere
 * close it (input is consumed by {@link PlayerCardManager} while open).
 */
@Singleton
public class PlayerCardOverlay extends Overlay {
	private static final int CARD_W = 250;
	private static final int CARD_H = 384;
	private static final int GLOW = 12;
	private static final long FADE_MS = 200;
	private static final String[] DESIGNS = {"Classic", "Slate", "Banner"};

	private final Client client;

	private volatile PlayerCardData data;
	private volatile Color accent;
	private volatile BufferedImage rankSprite;
	private volatile long openedAt;
	private volatile int design;

	private Rectangle prevHit = new Rectangle();
	private Rectangle nextHit = new Rectangle();

	@Inject
	public PlayerCardOverlay(Client client) {
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
	}

	void show(PlayerCardData data, Color accent) {
		this.rankSprite = null;
		this.accent = accent;
		this.openedAt = System.currentTimeMillis();
		this.data = data;
	}

	void setRankSprite(BufferedImage sprite) {
		this.rankSprite = sprite;
	}

	boolean isOpen() {
		return data != null;
	}

	/** How long the card has been open, in milliseconds. */
	long openForMs() {
		return data != null ? System.currentTimeMillis() - openedAt : 0;
	}

	void close() {
		data = null;
	}

	/** Returns true when the click hit a control (design switcher) and was handled. */
	boolean handleClick(Point point) {
		if (prevHit.contains(point)) {
			design = (design + DESIGNS.length - 1) % DESIGNS.length;
			return true;
		}
		if (nextHit.contains(point)) {
			design = (design + 1) % DESIGNS.length;
			return true;
		}
		return false;
	}

	@Override
	public Dimension render(Graphics2D g) {
		PlayerCardData card = data;
		Color accent = this.accent;
		if (card == null || accent == null) {
			return null;
		}

		float fade = Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) FADE_MS);
		int canvasW = client.getCanvasWidth();
		int canvasH = client.getCanvasHeight();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Dim the whole client so the card is in focus
		g.setColor(new Color(0, 0, 0, (int) (150 * fade)));
		g.fillRect(0, 0, canvasW, canvasH);

		Composite prevComposite = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade));

		int x = (canvasW - CARD_W) / 2;
		int y = (canvasH - CARD_H) / 2;
		drawFrame(g, accent, x, y);
		String hoveredWin = drawContent(g, card, accent, x, y);
		drawSwitcher(g, x, y);
		if (hoveredWin != null) {
			drawWinTooltip(g, hoveredWin);
		}

		g.setComposite(prevComposite);
		return null;
	}

	// ==================== Frames ====================

	private void drawFrame(Graphics2D g, Color accent, int x, int y) {
		switch (design) {
			case 1: drawSlateFrame(g, accent, x, y); break;
			case 2: drawBannerFrame(g, accent, x, y); break;
			default: drawClassicFrame(g, accent, x, y); break;
		}
	}

	/** Glow border, vertical gradient, accent header wash, diagonal sheen. */
	private void drawClassicFrame(Graphics2D g, Color accent, int x, int y) {
		int arc = 20;
		glow(g, accent, x, y, arc);

		g.setPaint(new GradientPaint(x, y, new Color(0x24242f), x, y + CARD_H, new Color(0x121219)));
		g.fillRoundRect(x, y, CARD_W, CARD_H, arc, arc);

		g.setPaint(new GradientPaint(x, y, withAlpha(accent, 46), x, y + 140, new Color(0, 0, 0, 0)));
		g.fillRoundRect(x, y, CARD_W, CARD_H, arc, arc);

		sheen(g, x, y, arc);

		g.setColor(withAlpha(accent, 210));
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(x, y, CARD_W - 1, CARD_H - 1, arc, arc);
	}

	/** Flat body with a bold accent header band; squared corners, no sheen. */
	private void drawSlateFrame(Graphics2D g, Color accent, int x, int y) {
		int arc = 8;
		g.setColor(new Color(0x1b1b22));
		g.fillRoundRect(x, y, CARD_W, CARD_H, arc, arc);

		Shape prevClip = g.getClip();
		g.clip(new RoundRectangle2D.Float(x, y, CARD_W, CARD_H, arc, arc));
		g.setPaint(new GradientPaint(x, y, withAlpha(accent, 170), x, y + 84, withAlpha(accent.darker(), 90)));
		g.fillRect(x, y, CARD_W, 84);
		g.setColor(withAlpha(accent, 230));
		g.fillRect(x, y + 84, CARD_W, 2);
		g.setClip(prevClip);

		g.setColor(new Color(255, 255, 255, 60));
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(x, y, CARD_W - 1, CARD_H - 1, arc, arc);
	}

	/** Hanging clan banner: rod with finials on top, dovetail bottom edge. */
	private void drawBannerFrame(Graphics2D g, Color accent, int x, int y) {
		int notch = 30;
		int rodY = y + 2;
		int top = y + 12;
		int bottom = y + CARD_H;

		Path2D.Float body = new Path2D.Float();
		body.moveTo(x, top);
		body.lineTo(x + CARD_W, top);
		body.lineTo(x + CARD_W, bottom);
		body.lineTo(x + CARD_W / 2f, bottom - notch);
		body.lineTo(x, bottom);
		body.closePath();

		// Drop shadow, then cloth
		Graphics2D shadow = (Graphics2D) g.create();
		shadow.translate(3, 4);
		shadow.setColor(new Color(0, 0, 0, 90));
		shadow.fill(body);
		shadow.dispose();

		g.setPaint(new GradientPaint(x, top, new Color(0x23232e), x, bottom, new Color(0x13131a)));
		g.fill(body);

		Shape prevClip = g.getClip();
		g.clip(body);
		g.setPaint(new GradientPaint(x, top, withAlpha(accent, 60), x, top + 130, new Color(0, 0, 0, 0)));
		g.fill(body);
		// Radial light from the top center, like the banner catches light
		g.setPaint(new RadialGradientPaint(new Point2D.Float(x + CARD_W / 2f, top + 30), CARD_W,
			new float[]{0f, 1f}, new Color[]{new Color(255, 255, 255, 16), new Color(0, 0, 0, 0)}));
		g.fill(body);
		g.setClip(prevClip);

		// Cloth outline + stitched inner seam
		g.setColor(withAlpha(accent, 220));
		g.setStroke(new BasicStroke(2f));
		g.draw(body);
		g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
			10f, new float[]{4f, 3f}, 0));
		g.setColor(withAlpha(accent, 110));
		g.drawLine(x + 7, top + 7, x + CARD_W - 7, top + 7);
		g.drawLine(x + 7, top + 7, x + 7, bottom - 12);
		g.drawLine(x + CARD_W - 7, top + 7, x + CARD_W - 7, bottom - 12);

		// Rod with finials
		g.setStroke(new BasicStroke(1f));
		g.setPaint(new GradientPaint(x, rodY, new Color(0xE0B84F), x, rodY + 7, new Color(0x8A6D25)));
		g.fillRoundRect(x - 10, rodY, CARD_W + 20, 7, 4, 4);
		g.setColor(withAlpha(accent, 235));
		g.fillOval(x - 16, rodY - 2, 11, 11);
		g.fillOval(x + CARD_W + 5, rodY - 2, 11, 11);
	}

	private void glow(Graphics2D g, Color accent, int x, int y, int arc) {
		g.setStroke(new BasicStroke(1));
		for (int i = GLOW; i > 0; i--) {
			g.setColor(withAlpha(accent, Math.max(1, 24 - i * 2)));
			g.drawRoundRect(x - i, y - i, CARD_W + i * 2, CARD_H + i * 2, arc + i, arc + i);
		}
	}

	private void sheen(Graphics2D g, int x, int y, int arc) {
		Shape prevClip = g.getClip();
		g.clip(new RoundRectangle2D.Float(x, y, CARD_W, CARD_H, arc, arc));
		g.setPaint(new GradientPaint(x, y + CARD_H, new Color(255, 255, 255, 0),
			x + CARD_W, y, new Color(255, 255, 255, 16)));
		int band = 90;
		g.fillPolygon(
			new int[]{x + CARD_W - band - 50, x + CARD_W - 50, x + CARD_W, x + CARD_W - band},
			new int[]{y + CARD_H, y, y, y + CARD_H}, 4);
		g.setClip(prevClip);
	}

	// ==================== Content ====================

	/** Returns the event name of a hovered win star, or null. */
	private String drawContent(Graphics2D g, PlayerCardData card, Color accent, int x, int y) {
		Font small = FontManager.getRunescapeSmallFont();
		Font bold = FontManager.getRunescapeBoldFont();
		Font nameFont = bold.deriveFont(Font.BOLD, 20f);
		Font pointsFont = bold.deriveFont(Font.BOLD, 24f);
		// The slate header band carries the rank/name text instead of the accent
		Color rankColor = design == 1 ? new Color(0, 0, 0, 170) : accent;
		Color nameColor = design == 1 ? Color.WHITE : UIConstants.TEXT_PRIMARY;

		int cx = x + CARD_W / 2;
		int cy = y + 26;

		BufferedImage sprite = rankSprite;
		if (sprite != null) {
			int sw = Math.min(24, sprite.getWidth());
			int sh = Math.min(24, sprite.getHeight());
			g.drawImage(sprite, cx - sw / 2, cy - sh / 2, sw, sh, null);
		}
		cy += 24;
		centerText(g, card.getRankName().toUpperCase(), small, rankColor, cx, cy);
		cy += 26;
		centerText(g, card.getPlayerName(), nameFont, nameColor, cx, cy);
		cy += 16;
		String hoveredWin = drawWinStars(g, card, cx, cy);
		cy += 26;
		centerText(g, NumberFmt.group(card.getPoints()), pointsFont, UIConstants.ACCENT_GOLD, cx, cy);
		cy += 16;
		centerText(g, "ACTIVITY POINTS", small, UIConstants.TEXT_MUTED, cx, cy);
		cy += 20;

		int barW = CARD_W - 70;
		g.setColor(new Color(255, 255, 255, 26));
		g.fillRoundRect(cx - barW / 2, cy, barW, 5, 5, 5);
		int fill = (int) Math.round(barW * Math.min(1.0, Math.max(0.0, card.getRankProgress())));
		if (fill > 0) {
			g.setColor(accent);
			g.fillRoundRect(cx - barW / 2, cy, fill, 5, 5, 5);
		}
		cy += 18;
		String progressText = card.getNextRankName() != null
			? NumberFmt.group(card.getPointsToNext()) + " pts to " + card.getNextRankName()
			: "Max rank";
		centerText(g, progressText, small, UIConstants.TEXT_SECONDARY, cx, cy);
		cy += 16;

		int gap = 8;
		int tileW = (CARD_W - 40 - gap) / 2;
		int tileH = 48;
		String[][] stats = {
			{String.valueOf(card.getDrops()), "Drops"},
			{String.valueOf(card.getPets()), "Pets"},
			{String.valueOf(card.getEventsPlayed()), "Events"},
			{String.valueOf(card.getDiariesDone()), "Diary tasks"},
		};
		for (int i = 0; i < stats.length; i++) {
			int tx = x + 20 + (i % 2) * (tileW + gap);
			int ty = cy + (i / 2) * (tileH + gap);
			drawTile(g, accent, tx, ty, tileW, tileH);
			centerText(g, stats[i][0], bold, UIConstants.TEXT_PRIMARY, tx + tileW / 2, ty + 22);
			centerText(g, stats[i][1], small, UIConstants.TEXT_MUTED, tx + tileW / 2, ty + 38);
		}

		int fy = y + CARD_H - 14;
		g.setFont(bold);
		drawShadowed(g, "REVAL", x + 20, fy, UIConstants.ACCENT_GOLD);
		g.setFont(small);
		String since = "Member since " + card.getMemberSince();
		int sinceW = g.getFontMetrics().stringWidth(since);
		drawShadowed(g, since, x + CARD_W - 20 - sinceW, fy, UIConstants.TEXT_MUTED);
		return hoveredWin;
	}

	/** One star per event win, centered; returns the hovered star's event. */
	private String drawWinStars(Graphics2D g, PlayerCardData card, int cx, int centerY) {
		java.util.List<String> wins = card.getEventWins();
		if (wins == null || wins.isEmpty()) {
			return null;
		}
		int spacing = 18;
		int startX = cx - (wins.size() - 1) * spacing / 2;
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		String hovered = null;

		for (int i = 0; i < wins.size(); i++) {
			int sx = startX + i * spacing;
			boolean hot = Math.abs(mouse.getX() - sx) <= 9 && Math.abs(mouse.getY() - centerY) <= 9;
			if (hot) {
				hovered = wins.get(i);
			}
			Shape star = star(sx, centerY, hot ? 8 : 7, hot ? 3.6 : 3.2);
			Graphics2D shadow = (Graphics2D) g.create();
			shadow.translate(1, 1);
			shadow.setColor(new Color(0, 0, 0, 160));
			shadow.fill(star);
			shadow.dispose();
			g.setColor(hot ? new Color(0xFFD75E) : UIConstants.ACCENT_GOLD);
			g.fill(star);
			g.setColor(new Color(0x8A6D25));
			g.setStroke(new BasicStroke(1f));
			g.draw(star);
		}
		return hovered;
	}

	private Shape star(int cx, int cy, double outer, double inner) {
		Path2D.Double path = new Path2D.Double();
		for (int i = 0; i < 10; i++) {
			double r = i % 2 == 0 ? outer : inner;
			double angle = Math.PI / 5 * i - Math.PI / 2;
			double px = cx + Math.cos(angle) * r;
			double py = cy + Math.sin(angle) * r;
			if (i == 0) {
				path.moveTo(px, py);
			} else {
				path.lineTo(px, py);
			}
		}
		path.closePath();
		return path;
	}

	/** Small tooltip near the cursor naming the won event. */
	private void drawWinTooltip(Graphics2D g, String eventName) {
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		String text = "Won: " + eventName;
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 16;
		int h = fm.getHeight() + 8;
		int tx = Math.min(mouse.getX() + 12, client.getCanvasWidth() - w - 4);
		int ty = mouse.getY() - h - 6;

		g.setColor(new Color(0, 0, 0, 210));
		g.fillRoundRect(tx, ty, w, h, 6, 6);
		g.setColor(withAlpha(UIConstants.ACCENT_GOLD, 160));
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(tx, ty, w, h, 6, 6);
		drawShadowed(g, text, tx + 8, ty + fm.getAscent() + 4, UIConstants.ACCENT_GOLD);
	}

	private void drawTile(Graphics2D g, Color accent, int tx, int ty, int w, int h) {
		switch (design) {
			case 1:
				// Divider style: hairline top rule instead of a filled tile
				g.setColor(new Color(255, 255, 255, 50));
				g.fillRect(tx, ty, w, 1);
				break;
			case 2:
				g.setColor(withAlpha(accent, 18));
				g.fillRoundRect(tx, ty, w, h, 10, 10);
				g.setStroke(new BasicStroke(1f));
				g.setColor(withAlpha(accent, 60));
				g.drawRoundRect(tx, ty, w, h, 10, 10);
				break;
			default:
				g.setColor(new Color(255, 255, 255, 14));
				g.fillRoundRect(tx, ty, w, h, 10, 10);
				break;
		}
	}

	// ==================== Switcher ====================

	private void drawSwitcher(Graphics2D g, int x, int y) {
		Font small = FontManager.getRunescapeSmallFont();
		int cx = x + CARD_W / 2;
		int sy = y + CARD_H + GLOW + 18;

		String label = DESIGNS[design] + "  " + (design + 1) + "/" + DESIGNS.length;
		g.setFont(small);
		FontMetrics fm = g.getFontMetrics();
		int labelW = fm.stringWidth(label);

		drawShadowed(g, label, cx - labelW / 2, sy, new Color(255, 255, 255, 200));
		drawShadowed(g, "<", cx - labelW / 2 - 22, sy, UIConstants.ACCENT_GOLD);
		drawShadowed(g, ">", cx + labelW / 2 + 14, sy, UIConstants.ACCENT_GOLD);

		prevHit = new Rectangle(cx - labelW / 2 - 34, sy - 16, 28, 24);
		nextHit = new Rectangle(cx + labelW / 2 + 6, sy - 16, 28, 24);

		centerText(g, "Click anywhere to close", small, new Color(255, 255, 255, 110), cx, sy + 20);
	}

	// ==================== Helpers ====================

	private static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	private void centerText(Graphics2D g, String text, Font font, Color color, int cx, int baselineY) {
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		drawShadowed(g, text, cx - fm.stringWidth(text) / 2, baselineY, color);
	}

	private void drawShadowed(Graphics2D g, String text, int x, int y, Color color) {
		g.setColor(new Color(0, 0, 0, 180));
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}
}

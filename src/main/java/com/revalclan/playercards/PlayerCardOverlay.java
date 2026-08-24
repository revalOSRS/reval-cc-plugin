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
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Trading-card style profile rendered in-game: the whole client dims and the
 * card draws centered on the canvas, fading in.
 * The accent color comes from the player's rank. Clicks and Escape close it
 * (input is consumed by {@link PlayerCardManager} while open).
 */
@Singleton
public class PlayerCardOverlay extends Overlay {
	private static final int CARD_W = 250;
	private static final int CARD_H = 384;
	private static final int GLOW = 12;
	private static final int ARC = 20;
	private static final long FADE_MS = 200;

	private final Client client;

	private volatile PlayerCardData data;
	private volatile Color accent;
	private volatile BufferedImage rankSprite;
	private volatile BufferedImage nextRankSprite;
	private volatile long openedAt;
	private volatile String pendingName;
	private volatile String statusText;

	@Inject
	public PlayerCardOverlay(Client client) {
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
	}

	/** Dim and show a loading message while the profile is fetched. */
	void showLoading(String playerName) {
		this.data = null;
		this.rankSprite = null;
		this.nextRankSprite = null;
		this.statusText = "Loading Reval profile...";
		this.openedAt = System.currentTimeMillis();
		this.pendingName = playerName;
	}

	/** True while the card is still waiting for this player's profile. */
	boolean isLoadingFor(String playerName) {
		return data == null && playerName.equals(pendingName);
	}

	void showError(String message) {
		this.statusText = message;
	}

	void show(PlayerCardData data, Color accent) {
		this.accent = accent;
		this.data = data;
	}

	void setRankSprite(BufferedImage sprite) {
		this.rankSprite = sprite;
	}

	void setNextRankSprite(BufferedImage sprite) {
		this.nextRankSprite = sprite;
	}

	boolean isOpen() {
		return pendingName != null;
	}

	void close() {
		data = null;
		pendingName = null;
		statusText = null;
	}

	@Override
	public Dimension render(Graphics2D g) {
		if (!isOpen()) {
			return null;
		}
		PlayerCardData card = data;

		float fade = Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) FADE_MS);
		int canvasW = client.getCanvasWidth();
		int canvasH = client.getCanvasHeight();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Dim the whole client so the card is in focus
		g.setColor(new Color(0, 0, 0, (int) (150 * fade)));
		g.fillRect(0, 0, canvasW, canvasH);

		Composite prevComposite = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade));

		Font small = FontManager.getRunescapeSmallFont();
		int cx = canvasW / 2;

		if (card == null || accent == null) {
			// Loading or error text instead of the card
			centerText(g, statusText != null ? statusText : "Loading Reval profile...",
				FontManager.getRunescapeFont(), UIConstants.ACCENT_GOLD, cx, canvasH / 2);
			centerText(g, "Click or press Esc to close", small, new Color(255, 255, 255, 110),
				cx, canvasH / 2 + 22);
			g.setComposite(prevComposite);
			return null;
		}

		int x = (canvasW - CARD_W) / 2;
		int y = (canvasH - CARD_H) / 2;
		drawFrame(g, accent, x, y);
		String hoveredWin = drawContent(g, card, accent, x, y);

		centerText(g, "Click or press Esc to close", small, new Color(255, 255, 255, 110),
			x + CARD_W / 2, y + CARD_H + GLOW + 16);

		if (hoveredWin != null) {
			drawWinTooltip(g, hoveredWin);
		}

		g.setComposite(prevComposite);
		return null;
	}

	/** Classic frame with the foil radial color: glow, gradient, sheen. */
	private void drawFrame(Graphics2D g, Color accent, int x, int y) {
		g.setStroke(new BasicStroke(1));
		for (int i = GLOW; i > 0; i--) {
			g.setColor(withAlpha(accent, Math.max(1, 24 - i * 2)));
			g.drawRoundRect(x - i, y - i, CARD_W + i * 2, CARD_H + i * 2, ARC + i, ARC + i);
		}

		g.setPaint(new GradientPaint(x, y, new Color(0x24242f), x, y + CARD_H, new Color(0x121219)));
		g.fillRoundRect(x, y, CARD_W, CARD_H, ARC, ARC);

		Shape prevClip = g.getClip();
		g.clip(new RoundRectangle2D.Float(x, y, CARD_W, CARD_H, ARC, ARC));
		g.setPaint(new RadialGradientPaint(new Point2D.Float(x + CARD_W / 2f, y + 60), CARD_W,
			new float[]{0f, 1f}, new Color[]{withAlpha(accent, 78), new Color(0, 0, 0, 0)}));
		g.fillRect(x, y, CARD_W, CARD_H);

		g.setPaint(new GradientPaint(x, y + CARD_H, new Color(255, 255, 255, 0),
			x + CARD_W, y, new Color(255, 255, 255, 16)));
		int band = 90;
		g.fillPolygon(
			new int[]{x + CARD_W - band - 50, x + CARD_W - 50, x + CARD_W, x + CARD_W - band},
			new int[]{y + CARD_H, y, y, y + CARD_H}, 4);
		g.setClip(prevClip);

		g.setColor(withAlpha(accent, 210));
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(x, y, CARD_W - 1, CARD_H - 1, ARC, ARC);
	}

	/** Returns the event name of a hovered win star, or null. */
	private String drawContent(Graphics2D g, PlayerCardData card, Color accent, int x, int y) {
		Font small = FontManager.getRunescapeSmallFont();
		Font bold = FontManager.getRunescapeBoldFont();
		Font nameFont = bold.deriveFont(Font.BOLD, 20f);
		Font pointsFont = bold.deriveFont(Font.BOLD, 24f);

		int cx = x + CARD_W / 2;
		int cy = y + 26;

		BufferedImage sprite = rankSprite;
		if (sprite != null) {
			int sw = Math.min(24, sprite.getWidth());
			int sh = Math.min(24, sprite.getHeight());
			g.drawImage(sprite, cx - sw / 2, cy - sh / 2, sw, sh, null);
		}
		cy += 24;
		centerText(g, card.getRankName().toUpperCase(), small, accent, cx, cy);
		cy += 26;
		centerText(g, card.getPlayerName(), nameFont, UIConstants.TEXT_PRIMARY, cx, cy);
		cy += 12;
		String hoveredWin = drawWinStars(g, card, cx, cy);
		cy += 32;
		centerText(g, NumberFmt.group(card.getPoints()), pointsFont, UIConstants.ACCENT_GOLD, cx, cy);
		cy += 16;
		centerText(g, "REVAL POINTS", small, UIConstants.TEXT_MUTED, cx, cy);
		cy += 20;

		if (card.getRankProgress() >= 0) {
			int barW = CARD_W - 70;
			g.setColor(new Color(255, 255, 255, 26));
			g.fillRoundRect(cx - barW / 2, cy, barW, 5, 5, 5);
			int fill = (int) Math.round(barW * Math.min(1.0, Math.max(0.0, card.getRankProgress())));
			if (fill > 0) {
				g.setColor(accent);
				g.fillRoundRect(cx - barW / 2, cy, fill, 5, 5, 5);
			}
			cy += 18;
			drawProgressRow(g, card, small, cx, cy);
		} else {
			cy += 18;
		}
		cy += 16;

		int gap = 8;
		int tileW = (CARD_W - 40 - gap) / 2;
		int tileH = 48;
		String[][] stats = {
			{NumberFmt.group(card.getDropPoints()) + "M", "GP from drops"},
			{String.valueOf(card.getPetCount()), "Pets"},
			{String.valueOf(card.getClogCount()), "Collection log"},
			{card.getDiaryTasksDone() + "/" + card.getDiaryTasksTotal(), "Reval diary tasks"},
		};
		for (int i = 0; i < stats.length; i++) {
			int tx = x + 20 + (i % 2) * (tileW + gap);
			int ty = cy + (i / 2) * (tileH + gap);
			g.setColor(withAlpha(accent, 18));
			g.fillRoundRect(tx, ty, tileW, tileH, 10, 10);
			g.setStroke(new BasicStroke(1f));
			g.setColor(withAlpha(accent, 60));
			g.drawRoundRect(tx, ty, tileW, tileH, 10, 10);
			centerText(g, stats[i][0], bold, UIConstants.TEXT_PRIMARY, tx + tileW / 2, ty + 22);
			centerText(g, stats[i][1], small, UIConstants.TEXT_MUTED, tx + tileW / 2, ty + 38);
		}

		int fy = y + CARD_H - 14;
		g.setFont(bold);
		drawShadowed(g, "REVAL", x + 20, fy, UIConstants.ACCENT_GOLD);
		if (card.getMemberSince() != null) {
			g.setFont(small);
			String since = "Member since " + card.getMemberSince();
			int sinceW = g.getFontMetrics().stringWidth(since);
			drawShadowed(g, since, x + CARD_W - 20 - sinceW, fy, UIConstants.TEXT_MUTED);
		}
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

	/** "N pts to <rank icon>" centered; falls back to the rank name until the sprite loads. */
	private void drawProgressRow(Graphics2D g, PlayerCardData card, Font small, int cx, int baselineY) {
		if (card.getNextRankName() == null) {
			centerText(g, "Max rank", small, UIConstants.TEXT_SECONDARY, cx, baselineY);
			return;
		}
		String text = NumberFmt.group(card.getPointsToNext()) + " pts to ";
		BufferedImage sprite = nextRankSprite;
		if (sprite == null) {
			centerText(g, text + card.getNextRankName(), small, UIConstants.TEXT_SECONDARY, cx, baselineY);
			return;
		}
		g.setFont(small);
		FontMetrics fm = g.getFontMetrics();
		// Aspect-fit the icon to 14px and center it on the text's midline
		double scale = Math.min(1.0, 14.0 / Math.max(sprite.getWidth(), sprite.getHeight()));
		int iconW = Math.max(1, (int) Math.round(sprite.getWidth() * scale));
		int iconH = Math.max(1, (int) Math.round(sprite.getHeight() * scale));
		int textW = fm.stringWidth(text);
		int tx = cx - (textW + 3 + iconW) / 2;
		drawShadowed(g, text, tx, baselineY, UIConstants.TEXT_SECONDARY);
		g.drawImage(sprite, tx + textW + 3, baselineY - 5 - iconH / 2, iconW, iconH, null);
	}

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

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
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Trading-card style profile rendered in-game: the whole client dims and the
 * card draws centered on the canvas (OSRS TCG pack-opening style), fading in
 * over the first frames. Clicks are consumed and close it while it is open.
 */
@Singleton
public class PlayerCardOverlay extends Overlay {
	private static final int CARD_W = 300;
	private static final int CARD_H = 440;
	private static final int ARC = 20;
	private static final int GLOW = 14;
	private static final long FADE_MS = 200;

	private final Client client;

	private volatile PlayerCardData data;
	private volatile Color accent;
	private volatile BufferedImage rankSprite;
	private volatile long openedAt;

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
		drawCard(g, card, accent, x, y);

		Font small = FontManager.getRunescapeSmallFont();
		centerText(g, "Click anywhere to close", small, new Color(255, 255, 255, 120),
			x + CARD_W / 2, y + CARD_H + 26);

		g.setComposite(prevComposite);
		return null;
	}

	private void drawCard(Graphics2D g, PlayerCardData card, Color accent, int x, int y) {
		// Soft accent glow radiating from the card edge
		g.setStroke(new BasicStroke(1));
		for (int i = GLOW; i > 0; i--) {
			g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
				Math.max(1, 26 - i * 2)));
			g.drawRoundRect(x - i, y - i, CARD_W + i * 2, CARD_H + i * 2, ARC + i, ARC + i);
		}

		// Card body
		g.setPaint(new GradientPaint(x, y, new Color(0x24242f), x, y + CARD_H, new Color(0x121219)));
		g.fillRoundRect(x, y, CARD_W, CARD_H, ARC, ARC);

		// Accent wash behind the header
		g.setPaint(new GradientPaint(x, y, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 46),
			x, y + 150, new Color(0, 0, 0, 0)));
		g.fillRoundRect(x, y, CARD_W, CARD_H, ARC, ARC);

		// Diagonal holo sheen
		Shape prevClip = g.getClip();
		g.clip(new RoundRectangle2D.Float(x, y, CARD_W, CARD_H, ARC, ARC));
		g.setPaint(new GradientPaint(x, y + CARD_H, new Color(255, 255, 255, 0),
			x + CARD_W, y, new Color(255, 255, 255, 18)));
		int band = 110;
		g.fillPolygon(
			new int[]{x + CARD_W - band - 60, x + CARD_W - 60, x + CARD_W, x + CARD_W - band},
			new int[]{y + CARD_H, y, y, y + CARD_H}, 4);
		g.setClip(prevClip);

		// Border
		g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 210));
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(x, y, CARD_W - 1, CARD_H - 1, ARC, ARC);

		// ---- Content ----
		Font small = FontManager.getRunescapeSmallFont();
		Font bold = FontManager.getRunescapeBoldFont();
		Font nameFont = bold.deriveFont(Font.BOLD, 22f);
		Font pointsFont = bold.deriveFont(Font.BOLD, 26f);
		int cx = x + CARD_W / 2;
		int cy = y + 30;

		BufferedImage sprite = rankSprite;
		if (sprite != null) {
			int sw = Math.min(26, sprite.getWidth());
			int sh = Math.min(26, sprite.getHeight());
			g.drawImage(sprite, cx - sw / 2, cy - sh / 2, sw, sh, null);
		}
		cy += 26;
		centerText(g, card.getRankName().toUpperCase(), small, accent, cx, cy);
		cy += 30;
		centerText(g, card.getPlayerName(), nameFont, UIConstants.TEXT_PRIMARY, cx, cy);
		cy += 44;
		centerText(g, NumberFmt.group(card.getPoints()), pointsFont, UIConstants.ACCENT_GOLD, cx, cy);
		cy += 18;
		centerText(g, "ACTIVITY POINTS", small, UIConstants.TEXT_MUTED, cx, cy);
		cy += 22;

		// Rank progress
		int barW = CARD_W - 80;
		g.setColor(new Color(255, 255, 255, 26));
		g.fillRoundRect(cx - barW / 2, cy, barW, 5, 5, 5);
		int fill = (int) Math.round(barW * Math.min(1.0, Math.max(0.0, card.getRankProgress())));
		if (fill > 0) {
			g.setColor(accent);
			g.fillRoundRect(cx - barW / 2, cy, fill, 5, 5, 5);
		}
		cy += 20;
		String progressText = card.getNextRankName() != null
			? NumberFmt.group(card.getPointsToNext()) + " pts to " + card.getNextRankName()
			: "Max rank";
		centerText(g, progressText, small, UIConstants.TEXT_SECONDARY, cx, cy);
		cy += 20;

		// Stats grid
		int gap = 10;
		int tileW = (CARD_W - 48 - gap) / 2;
		int tileH = 56;
		String[][] stats = {
			{String.valueOf(card.getDrops()), "Drops"},
			{String.valueOf(card.getPets()), "Pets"},
			{String.valueOf(card.getEventsPlayed()), "Events"},
			{String.valueOf(card.getDiariesDone()), "Diary tasks"},
		};
		for (int i = 0; i < stats.length; i++) {
			int tx = x + 24 + (i % 2) * (tileW + gap);
			int ty = cy + (i / 2) * (tileH + gap);
			g.setColor(new Color(255, 255, 255, 14));
			g.fillRoundRect(tx, ty, tileW, tileH, 10, 10);
			centerText(g, stats[i][0], bold, UIConstants.TEXT_PRIMARY, tx + tileW / 2, ty + 26);
			centerText(g, stats[i][1], small, UIConstants.TEXT_MUTED, tx + tileW / 2, ty + 42);
		}

		// Footer
		int fy = y + CARD_H - 16;
		g.setFont(bold);
		drawShadowed(g, "REVAL", x + 24, fy, UIConstants.ACCENT_GOLD);
		g.setFont(small);
		String since = "Member since " + card.getMemberSince();
		int sinceW = g.getFontMetrics().stringWidth(since);
		drawShadowed(g, since, x + CARD_W - 24 - sinceW, fy, UIConstants.TEXT_MUTED);
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

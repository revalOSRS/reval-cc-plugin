package com.revalclan.collectionlog;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Highlights the path to the "Sync Reval" collection log entry while the
 * sync guide is armed: a hint banner until the collection log is open, a
 * pulsing glow on the burger menu button, then on the Sync Reval entry once
 * the menu is expanded. Each phase's hint lasts 20 seconds (a slim bar
 * drains along its bottom edge); moving to another phase restarts the
 * clock, running one out disarms the guide.
 */
public class SyncGuideOverlay extends Overlay {
	private static final Color GOLD = new Color(255, 200, 60);
	private static final long HINT_TIMEOUT_MS = 20_000;

	private final Client client;
	private final SyncGuide guide;

	private enum Phase { BANNER, BURGER, MENU }

	private Phase phase;
	private long phaseStartedAt;

	@Inject
	public SyncGuideOverlay(Client client, SyncGuide guide) {
		this.client = client;
		this.guide = guide;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g) {
		if (!guide.isArmed()) {
			phase = null;
			return null;
		}

		Widget burger = client.getWidget(InterfaceID.Collection.BURGER_BTN_MENU);
		Widget menuFrame = client.getWidget(InterfaceID.Collection.BURGER_MENU_FRAME);
		boolean logOpen = burger != null && !burger.isHidden();
		boolean menuOpen = logOpen && menuFrame != null && !menuFrame.isHidden();
		Phase current = !logOpen ? Phase.BANNER : menuOpen ? Phase.MENU : Phase.BURGER;

		// Each phase gets its own full window; changing phase restarts the clock
		long now = System.currentTimeMillis();
		if (current != phase) {
			phase = current;
			phaseStartedAt = now;
		}
		double fraction = (double) (HINT_TIMEOUT_MS - (now - phaseStartedAt)) / HINT_TIMEOUT_MS;
		if (fraction <= 0) {
			guide.disarm();
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		switch (current) {
			case BANNER:
				drawHint(g, "Open your Collection Log to sync your points", fraction);
				break;
			case BURGER:
				glow(g, burger.getBounds());
				drawHint(g, "Open this menu, then click Sync Reval", fraction);
				break;
			case MENU:
				Widget syncEntry = guide.getSyncButtonWidget();
				if (syncEntry != null && !syncEntry.isHidden()) {
					glow(g, syncEntry.getBounds());
					drawHint(g, "Click Sync Reval", fraction);
				} else {
					drawHint(g, "Click Sync Reval in the menu", fraction);
				}
				break;
		}
		return null;
	}

	private void glow(Graphics2D g, Rectangle bounds) {
		if (bounds == null || bounds.isEmpty()) {
			return;
		}
		// Pulse between soft and bright
		double pulse = (Math.sin(System.currentTimeMillis() / 220.0) + 1) / 2;
		int alpha = (int) (100 + pulse * 155);

		g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), alpha / 4));
		g.fillRoundRect(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6, 8, 8);

		g.setStroke(new BasicStroke(2));
		g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), alpha));
		g.drawRoundRect(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6, 8, 8);
	}

	/** Hint banner with a slim time bar draining along the bottom edge. */
	private void drawHint(Graphics2D g, String text, double fraction) {
		g.setFont(FontManager.getRunescapeFont());
		FontMetrics fm = g.getFontMetrics();

		int width = fm.stringWidth(text) + 24;
		int height = fm.getHeight() + 12 + 7;
		int x = (client.getCanvasWidth() - width) / 2;
		int y = 26;

		g.setColor(new Color(0, 0, 0, 180));
		g.fillRoundRect(x, y, width, height, 10, 10);
		g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 140));
		g.setStroke(new BasicStroke(1));
		g.drawRoundRect(x, y, width, height, 10, 10);

		g.setColor(GOLD);
		g.drawString(text, x + 12, y + fm.getAscent() + 6);

		int trackX = x + 8;
		int trackWidth = width - 16;
		int barY = y + height - 6;
		g.setColor(new Color(255, 255, 255, 30));
		g.fillRoundRect(trackX, barY, trackWidth, 3, 3, 3);
		int fillWidth = (int) Math.round(trackWidth * Math.min(1.0, Math.max(0.0, fraction)));
		if (fillWidth > 0) {
			g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 220));
			g.fillRoundRect(trackX, barY, fillWidth, 3, 3, 3);
		}
	}
}

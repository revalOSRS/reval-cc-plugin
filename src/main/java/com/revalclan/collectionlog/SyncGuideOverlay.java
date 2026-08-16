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
 * the menu is expanded.
 */
public class SyncGuideOverlay extends Overlay {
	private static final Color GOLD = new Color(255, 200, 60);
	// The pre-collection-log hint gives up after this long; once the log is
	// open the player is clearly following the guide, so it keeps going.
	private static final long BANNER_TIMEOUT_MS = 20_000;

	private final Client client;
	private final SyncGuide guide;

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
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		Widget burger = client.getWidget(InterfaceID.Collection.BURGER_BTN_MENU);
		if (burger == null || burger.isHidden()) {
			if (guide.armedForMs() > BANNER_TIMEOUT_MS) {
				guide.disarm();
				return null;
			}
			drawHint(g, "Open your Collection Log to sync your points");
			return null;
		}

		Widget menuFrame = client.getWidget(InterfaceID.Collection.BURGER_MENU_FRAME);
		boolean menuOpen = menuFrame != null && !menuFrame.isHidden();

		if (menuOpen) {
			Widget syncEntry = guide.getSyncButtonWidget();
			if (syncEntry != null && !syncEntry.isHidden()) {
				glow(g, syncEntry.getBounds());
				drawHint(g, "Click Sync Reval");
				return null;
			}
			drawHint(g, "Click Sync Reval in the menu");
			return null;
		}

		glow(g, burger.getBounds());
		drawHint(g, "Open this menu, then click Sync Reval");
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

	private void drawHint(Graphics2D g, String text) {
		g.setFont(FontManager.getRunescapeFont());
		FontMetrics fm = g.getFontMetrics();
		int width = fm.stringWidth(text) + 24;
		int height = fm.getHeight() + 12;
		int x = (client.getCanvasWidth() - width) / 2;
		int y = 26;

		g.setColor(new Color(0, 0, 0, 180));
		g.fillRoundRect(x, y, width, height, 10, 10);
		g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 140));
		g.setStroke(new BasicStroke(1));
		g.drawRoundRect(x, y, width, height, 10, 10);

		g.setColor(GOLD);
		g.drawString(text, x + 12, y + fm.getAscent() + 6);
	}
}

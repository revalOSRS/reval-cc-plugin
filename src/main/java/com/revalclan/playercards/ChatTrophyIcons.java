package com.revalclan.playercards;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedSprite;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Drawn trophy/star chat icons registered as mod icons, so the profile chat
 * commands can embed them with img tags. Registered once after login.
 */
@Singleton
public class ChatTrophyIcons {
	private static final Color GOLD = new Color(0xFFC83C);
	private static final Color SILVER = new Color(0xC6C6CE);
	private static final Color BRONZE = new Color(0xCD7F50);

	private final Client client;

	private volatile int starIdx = -1;
	private volatile int goldIdx = -1;
	private volatile int silverIdx = -1;
	private volatile int bronzeIdx = -1;

	@Inject
	public ChatTrophyIcons(Client client) {
		this.client = client;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			loadIcons();
		}
	}

	public void startUp() {
		if (client.getGameState() == GameState.LOGGED_IN) {
			loadIcons();
		}
	}

	/** Empty string until icons are registered. */
	public String star() {
		return tag(starIdx);
	}

	public String goldTrophy() {
		return tag(goldIdx);
	}

	public String silverTrophy() {
		return tag(silverIdx);
	}

	public String bronzeTrophy() {
		return tag(bronzeIdx);
	}

	private static String tag(int index) {
		return index >= 0 ? "<img=" + index + ">" : "";
	}

	private synchronized void loadIcons() {
		if (starIdx != -1) {
			return;
		}
		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null) {
			return;
		}
		BufferedImage[] images = {starImage(), trophyImage(GOLD), trophyImage(SILVER), trophyImage(BRONZE)};
		IndexedSprite[] newIcons = Arrays.copyOf(modIcons, modIcons.length + images.length);
		for (int i = 0; i < images.length; i++) {
			newIcons[modIcons.length + i] = ImageUtil.getImageIndexedSprite(images[i], client);
		}
		client.setModIcons(newIcons);
		starIdx = modIcons.length;
		goldIdx = modIcons.length + 1;
		silverIdx = modIcons.length + 2;
		bronzeIdx = modIcons.length + 3;
	}

	private static BufferedImage starImage() {
		BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Path2D.Double star = new Path2D.Double();
		for (int i = 0; i < 10; i++) {
			double r = i % 2 == 0 ? 5.5 : 2.4;
			double angle = Math.PI / 5 * i - Math.PI / 2;
			double px = 6 + Math.cos(angle) * r;
			double py = 6.5 + Math.sin(angle) * r;
			if (i == 0) {
				star.moveTo(px, py);
			} else {
				star.lineTo(px, py);
			}
		}
		star.closePath();
		g.setColor(GOLD);
		g.fill(star);
		g.setColor(new Color(0x8A6D25));
		g.setStroke(new BasicStroke(1f));
		g.draw(star);
		g.dispose();
		return image;
	}

	private static BufferedImage trophyImage(Color metal) {
		BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(metal);
		// Cup bowl, stem and base
		g.fillRoundRect(3, 1, 6, 5, 3, 3);
		g.fillRect(5, 6, 2, 3);
		g.fillRect(3, 9, 6, 2);
		// Handles
		g.setStroke(new BasicStroke(1f));
		g.drawArc(1, 1, 3, 4, 90, 180);
		g.drawArc(8, 1, 3, 4, 270, 180);
		// Shade
		g.setColor(metal.darker());
		g.drawLine(4, 5, 7, 5);
		g.dispose();
		return image;
	}
}

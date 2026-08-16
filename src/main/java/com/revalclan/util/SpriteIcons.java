package com.revalclan.util;

import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** Loads game cache sprites into Swing components, aspect-fit into a square box. */
public final class SpriteIcons {
	private SpriteIcons() {
	}

	/** Load a sprite async and hand the fitted icon to {@code consumer} on the EDT. */
	public static void load(SpriteManager spriteManager, int spriteId, int size, Consumer<ImageIcon> consumer) {
		if (spriteManager == null || consumer == null) {
			return;
		}
		spriteManager.getSpriteAsync(spriteId, 0, sprite -> {
			if (sprite != null) {
				SwingUtilities.invokeLater(() -> consumer.accept(fit(sprite, size)));
			}
		});
	}

	/** Load a sprite async and set it on a label. */
	public static void apply(SpriteManager spriteManager, int spriteId, JLabel target, int size) {
		if (target == null) {
			return;
		}
		load(spriteManager, spriteId, size, target::setIcon);
	}

	/** Scale preserving aspect ratio, then pad onto a size x size canvas. */
	public static ImageIcon fit(BufferedImage image, int size) {
		BufferedImage scaled = image;
		if (scaled.getWidth() > size || scaled.getHeight() > size) {
			double scale = Math.min((double) size / scaled.getWidth(), (double) size / scaled.getHeight());
			scaled = ImageUtil.resizeImage(scaled,
				Math.max(1, (int) Math.round(scaled.getWidth() * scale)),
				Math.max(1, (int) Math.round(scaled.getHeight() * scale)));
		}
		return new ImageIcon(ImageUtil.resizeCanvas(scaled, size, size));
	}
}

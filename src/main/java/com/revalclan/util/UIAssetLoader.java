package com.revalclan.util;

import javax.imageio.ImageIO;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cached loader for images under /com/revalclan/ui/assets/. Missing files yield null (and are not cached). */
@Singleton
public class UIAssetLoader {
	private final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();
	private final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();

	/** @param filename e.g. "reval.png"; any directory prefix is dropped and ".png" added when missing */
	public BufferedImage getImage(String filename) {
		if (filename == null || filename.isEmpty()) return null;
		return imageCache.computeIfAbsent(normalize(filename), name -> {
			URL url = UIAssetLoader.class.getResource("/com/revalclan/ui/assets/" + name);
			try {
				return url == null ? null : ImageIO.read(url);
			} catch (IOException e) {
				return null;
			}
		});
	}

	/** The image scaled to {@code size}×{@code size}, cached per size. */
	public ImageIcon getIcon(String filename, int size) {
		if (filename == null || filename.isEmpty()) return null;
		return iconCache.computeIfAbsent(normalize(filename) + "_" + size, key -> {
			BufferedImage image = getImage(filename);
			return image == null ? null : new ImageIcon(image.getScaledInstance(size, size, Image.SCALE_SMOOTH));
		});
	}

	private static String normalize(String filename) {
		String name = filename.substring(filename.lastIndexOf('/') + 1);
		return name.endsWith(".png") ? name : name + ".png";
	}
}

package com.revalclan.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DrawManager;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Singleton
public class ScreenshotService {
	private static final float JPEG_QUALITY = 0.92f;
	private static final int MAX_WIDTH = 1200;

	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;

	@Inject
	public ScreenshotService(DrawManager drawManager, ScheduledExecutorService executor) {
		this.drawManager = drawManager;
		this.executor = executor;
	}

	/**
	 * Captures the next game frame, compresses it as JPEG, and returns
	 * a CompletableFuture containing the base64-encoded string.
	 *
	 * @return CompletableFuture that resolves to a base64 JPEG string, or null on failure
	 */
	public CompletableFuture<String> captureScreenshot() {
		CompletableFuture<Image> frameFuture = new CompletableFuture<>();

		try {
			drawManager.requestNextFrameListener(frameFuture::complete);
		} catch (Exception e) {
			frameFuture.complete(null);
		}

		return frameFuture
			.thenApplyAsync(image -> {
				if (image == null) {
					return null;
				}
				try {
					BufferedImage screenshot = toBufferedImage(image);
					screenshot = resizeIfNeeded(screenshot);
					return compressAndEncode(screenshot);
				} catch (Exception e) {
					return null;
				}
			}, executor);
	}

	/**
	 * Converts an Image to a BufferedImage if it isn't one already.
	 */
	private BufferedImage toBufferedImage(Image image) {
		if (image instanceof BufferedImage) {
			return (BufferedImage) image;
		}

		BufferedImage buffered = new BufferedImage(
			image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = buffered.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return buffered;
	}

	/**
	 * Downsizes the image if wider than MAX_WIDTH to keep payload small.
	 */
	private BufferedImage resizeIfNeeded(BufferedImage image) {
		if (image.getWidth() <= MAX_WIDTH) {
			return image;
		}

		double scale = (double) MAX_WIDTH / image.getWidth();
		int newHeight = (int) (image.getHeight() * scale);

		BufferedImage resized = new BufferedImage(MAX_WIDTH, newHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = resized.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(image, 0, 0, MAX_WIDTH, newHeight, null);
		g.dispose();
		return resized;
	}

	/**
	 * Compresses the image as JPEG and returns a base64-encoded string.
	 */
	private String compressAndEncode(BufferedImage image) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);

		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(JPEG_QUALITY);

		try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(image, null, null), param);
		} finally {
			writer.dispose();
		}

		byte[] bytes = baos.toByteArray();
		log.debug("Screenshot captured: {}x{}, {} KB", image.getWidth(), image.getHeight(), bytes.length / 1024);

		return Base64.getEncoder().encodeToString(bytes);
	}
}

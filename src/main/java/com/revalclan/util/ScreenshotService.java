package com.revalclan.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
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
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class ScreenshotService {
	private static final float JPEG_QUALITY = 0.92f;
	private static final int MAX_WIDTH = 1600;

	@Inject private DrawManager drawManager;
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ScheduledExecutorService executor;
	private Capture pending;
	private boolean running = true;

	private static final class Capture {
		final CompletableFuture<byte[]> result = new CompletableFuture<>();
		ScheduledFuture<?> timeout;
		Runnable watcher;
		Widget chat, pm;
		boolean frameQueued, frameReceived, encoderPending, restoreQueued;
	}

	public synchronized void startUp() {
		running = true;
		if (pending != null) drawManager.registerEveryFrameListener(pending.watcher);
	}

	public synchronized void shutDown() {
		running = false;
		if (pending != null) {
			Capture capture = pending;
			finish(capture, null);
			drawManager.unregisterEveryFrameListener(capture.watcher);
		}
	}

	/** JPEG bytes of the next frame, or null when the capture failed. */
	public synchronized CompletableFuture<byte[]> captureScreenshot() {
		if (!running) return CompletableFuture.completedFuture(null);
		if (pending != null) return pending.result;
		Capture capture = new Capture();
		pending = capture;
		// RuneLite cannot remove a next-frame listener and can discard it on a
		// failed image read. Keep at most one, with a weak owner, until drawing
		// resumes; the watcher then releases a timed-out capture even in that case.
		WeakReference<ScreenshotService> owner = new WeakReference<>(this);
		capture.watcher = () -> {
			ScreenshotService service = owner.get();
			if (service != null) service.onDraw(capture);
		};
		try {
			drawManager.registerEveryFrameListener(capture.watcher);
			capture.timeout = executor.schedule(() -> finish(capture, null), 5, TimeUnit.SECONDS);
			clientThread.invoke(() -> {
				synchronized (ScreenshotService.this) {
					if (capture.result.isDone()) return;
					Widget chat = client.getWidget(InterfaceID.Chatbox.CHATAREA);
					Widget pm = client.getWidget(InterfaceID.PmChat.CONTAINER);
					if (chat != null && !chat.isHidden()) { capture.chat = chat; chat.setHidden(true); }
					if (pm != null && !pm.isHidden()) { capture.pm = pm; pm.setHidden(true); }
					capture.frameQueued = true;
					drawManager.requestNextFrameListener(image -> {
						ScreenshotService service = owner.get();
						if (service != null) service.onFrame(capture, image);
					});
				}
			});
		} catch (RuntimeException error) {
			finish(capture, null);
		}
		return capture.result;
	}

	private synchronized void onDraw(Capture capture) {
		if (capture.result.isDone() && !capture.encoderPending) {
			drawManager.unregisterEveryFrameListener(capture.watcher);
			if (pending == capture) pending = null;
		}
	}

	private synchronized void onFrame(Capture capture, Image image) {
		if (pending != capture) return;
		capture.frameReceived = true;
		if (capture.result.isDone()) { onDraw(capture); return; }
		restoreChat(capture);
		capture.encoderPending = true;
		try {
			executor.execute(() -> {
				byte[] bytes = null;
				try {
					if (!capture.result.isDone() && image != null) bytes = compress(resizeIfNeeded(toBufferedImage(image)));
				} catch (Exception error) {
					log.warn("Error processing screenshot", error);
				} finally {
					synchronized (ScreenshotService.this) {
						capture.encoderPending = false;
						finish(capture, bytes);
						onDraw(capture);
					}
				}
			});
		} catch (RuntimeException error) {
			capture.encoderPending = false;
			finish(capture, null);
		}
	}

	private synchronized void finish(Capture capture, byte[] bytes) {
		if (capture.result.isDone()) return;
		restoreChat(capture);
		if (capture.timeout != null) capture.timeout.cancel(false);
		if ((capture.frameReceived && !capture.encoderPending) || !capture.frameQueued) {
			drawManager.unregisterEveryFrameListener(capture.watcher);
			if (pending == capture) pending = null;
		}
		capture.result.complete(bytes);
	}

	private void restoreChat(Capture capture) {
		if (capture.restoreQueued) return;
		capture.restoreQueued = true;
		clientThread.invoke(() -> {
			if (capture.chat != null) { capture.chat.setHidden(false); capture.chat = null; }
			if (capture.pm != null) { capture.pm.setHidden(false); capture.pm = null; }
		});
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
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(image, 0, 0, MAX_WIDTH, newHeight, null);
		g.dispose();
		return resized;
	}

	/**
	 * Compresses the image as JPEG.
	 */
	private byte[] compress(BufferedImage image) throws Exception {
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

		return bytes;
	}
}

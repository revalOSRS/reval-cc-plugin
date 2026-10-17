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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Shares one capture and permits one queued/active encoder. The JDK timeout
 * scheduler remains responsive even if RuneLite's shared worker is blocked.
 * DrawManager cannot remove a next-frame listener: an expired listener retains
 * only its small closure until rendering resumes, never another image/encoder.
 */
@Slf4j
@Singleton
public class ScreenshotService {
	private static final float JPEG_QUALITY = 0.92f;
	private static final int MAX_WIDTH = 1600;
	@FunctionalInterface interface FrameSource { void request(Consumer<Image> listener); }
	private final FrameSource frames;
	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final long timeoutMillis;
	private CompletableFuture<byte[]> current;
	private boolean running = true;
	private boolean encoding;

	@Inject
	public ScreenshotService(DrawManager drawManager, Client client, ClientThread clientThread,
		ScheduledExecutorService executor) {
		this(drawManager::requestNextFrameListener, client, clientThread, executor, 5000);
	}

	ScreenshotService(FrameSource frames, Client client, ClientThread clientThread,
		ScheduledExecutorService executor, long timeoutMillis) {
		this.frames = frames;
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
		this.timeoutMillis = timeoutMillis;
	}

	public synchronized void startUp() { running = true; }

	public void shutDown() {
		CompletableFuture<byte[]> pending;
		synchronized (this) { running = false; pending = current; }
		if (pending != null) pending.complete(null);
	}

	/** JPEG bytes of the next frame, or null on failure, timeout or shutdown. */
	public CompletableFuture<byte[]> captureScreenshot() {
		CompletableFuture<byte[]> result;
		synchronized (this) {
			if (!running) return CompletableFuture.completedFuture(null);
			if (current != null) return current;
			if (encoding) return CompletableFuture.completedFuture(null);
			result = current = new CompletableFuture<>();
		}
		result.completeOnTimeout(null, timeoutMillis, TimeUnit.MILLISECONDS);
		try { clientThread.invoke(() -> captureFrame(result)); }
		catch (RuntimeException error) { release(result); result.complete(null); }
		return result;
	}

	private void captureFrame(CompletableFuture<byte[]> result) {
		if (result.isDone()) { release(result); return; }
		Widget chat = client.getWidget(InterfaceID.Chatbox.CHATAREA);
		Widget pm = client.getWidget(InterfaceID.PmChat.CONTAINER);
		boolean restoreChat = chat != null && !chat.isHidden();
		boolean restorePm = pm != null && !pm.isHidden();
		// Registered before modifying widgets, including setup/request failures.
		result.whenComplete((bytes, error) -> clientThread.invoke(() -> {
			if (restoreChat) chat.setHidden(false);
			if (restorePm) pm.setHidden(false);
			release(result);
		}));
		try {
			if (restoreChat) chat.setHidden(true);
			if (restorePm) pm.setHidden(true);
			frames.request(image -> onFrame(result, image));
		} catch (RuntimeException error) { result.complete(null); }
	}

	private synchronized void release(CompletableFuture<byte[]> result) {
		if (current == result) current = null;
	}

	private void onFrame(CompletableFuture<byte[]> result, Image image) {
		synchronized (this) {
			if (result.isDone() || encoding || current != result) return;
			encoding = true;
		}
		try {
			executor.execute(() -> {
				byte[] bytes = null;
				try {
					if (!result.isDone() && image != null) bytes = compress(resizeIfNeeded(toBufferedImage(image)));
				} catch (Exception error) { log.warn("Error processing screenshot", error); }
				finally {
					synchronized (ScreenshotService.this) { encoding = false; }
					result.complete(bytes);
				}
			});
		} catch (RuntimeException error) {
			synchronized (this) { encoding = false; }
			result.complete(null);
		}
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

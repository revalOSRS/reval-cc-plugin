package com.revalclan.util;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ScreenshotServiceTest {
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
	private final Widget chat = mock(Widget.class);
	private final Widget pm = mock(Widget.class);
	private final Queue<Runnable> tasks = new ArrayDeque<>();
	private final Queue<Consumer<Image>> frames = new ArrayDeque<>();
	private ScreenshotService screenshots;

	@Before public void setUp() {
		when(client.getWidget(InterfaceID.Chatbox.CHATAREA)).thenReturn(chat);
		when(client.getWidget(InterfaceID.PmChat.CONTAINER)).thenReturn(pm);
		when(pm.isHidden()).thenReturn(true);
		doAnswer(invocation -> { invocation.<Runnable>getArgument(0).run(); return null; })
			.when(clientThread).invoke(any(Runnable.class));
		doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(executor).execute(any(Runnable.class));
		screenshots = new ScreenshotService(frames::add, client, clientThread, executor, 5000);
	}

	@Test public void simultaneousEventsShareOneFrameAndEncoder() throws Exception {
		CompletableFuture<byte[]> first = screenshots.captureScreenshot();
		for (int i = 0; i < 1000; i++) assertSame(first, screenshots.captureScreenshot());
		assertEquals(1, frames.size());
		frames.remove().accept(image());
		assertEquals(1, tasks.size());
		CompletableFuture<Boolean> callbackLock = first.thenApply(bytes -> Thread.holdsLock(screenshots));
		tasks.remove().run();
		assertFalse(callbackLock.join());
		assertEquals(20, ImageIO.read(new ByteArrayInputStream(first.join())).getWidth());
		verify(chat).setHidden(true);
		verify(chat).setHidden(false);
		verify(pm, never()).setHidden(anyBoolean());
	}

	@Test public void missingFramesTimeOutAndLateFramesDoNoWork() throws Exception {
		screenshots = new ScreenshotService(frames::add, client, clientThread, executor, 50);
		assertNull(screenshots.captureScreenshot().get(2, TimeUnit.SECONDS));
		verify(chat, timeout(2000)).setHidden(false);
		frames.remove().accept(image());
		assertTrue(tasks.isEmpty());
		CompletableFuture<byte[]> next = screenshots.captureScreenshot();
		assertFalse(next.isDone());
		screenshots.shutDown();
	}

	@Test public void shutdownRestoresChatAndLateFramesDoNoWork() {
		CompletableFuture<byte[]> first = screenshots.captureScreenshot();
		screenshots.shutDown();
		assertNull(first.join());
		verify(chat).setHidden(false);
		frames.remove().accept(image());
		assertTrue(tasks.isEmpty());
		assertNull(screenshots.captureScreenshot().join());
		screenshots.startUp();
		assertNotSame(first, screenshots.captureScreenshot());
		screenshots.shutDown();
	}

	@Test public void aRejectedEncoderStillCompletesTheNotification() {
		doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));
		CompletableFuture<byte[]> first = screenshots.captureScreenshot();
		frames.remove().accept(image());
		assertNull(first.join());
		verify(chat).setHidden(false);
	}

	@Test public void timeoutFiresWhileTheRealSingleWorkerIsBlockedInTheEncoder() throws Exception {
		ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			screenshots = new ScreenshotService(frames::add, client, clientThread, worker, 500);
			BufferedImage blocked = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB) {
				@Override public int getWidth() {
					entered.countDown();
					try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
					return super.getWidth();
				}
			};
			CompletableFuture<byte[]> first = screenshots.captureScreenshot();
			frames.remove().accept(blocked);
			assertTrue(entered.await(2, TimeUnit.SECONDS));
			assertNull(first.get(2, TimeUnit.SECONDS));
			for (int i = 0; i < 1000; i++) assertNull(screenshots.captureScreenshot().join());
			assertTrue("Blocked encoding must not retain more frames", frames.isEmpty());
			verify(chat, timeout(2000)).setHidden(false);
		} finally {
			release.countDown();
			worker.shutdown();
			assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	private BufferedImage image() { return new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB); }
}

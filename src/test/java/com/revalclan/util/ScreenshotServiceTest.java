package com.revalclan.util;

import com.revalclan.TestSupport;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ScreenshotServiceTest {
	private final ScreenshotService screenshots = new ScreenshotService();
	private final DrawManager draw = new DrawManager();
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
	private final Widget chat = mock(Widget.class);
	private final Widget pm = mock(Widget.class);
	private final Queue<Runnable> tasks = new ArrayDeque<>();
	private final AtomicReference<Runnable> timeout = new AtomicReference<>();

	@Before public void setUp() throws Exception {
		TestSupport.inject(screenshots, "client", client);
		TestSupport.inject(screenshots, "clientThread", clientThread);
		TestSupport.inject(screenshots, "drawManager", draw);
		TestSupport.inject(screenshots, "executor", executor);
		when(client.getWidget(InterfaceID.Chatbox.CHATAREA)).thenReturn(chat);
		when(client.getWidget(InterfaceID.PmChat.CONTAINER)).thenReturn(pm);
		when(pm.isHidden()).thenReturn(true);
		doAnswer(invocation -> { invocation.<Runnable>getArgument(0).run(); return null; })
			.when(clientThread).invoke(any(Runnable.class));
		doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(executor).execute(any(Runnable.class));
		doAnswer(invocation -> {
			timeout.set(invocation.getArgument(0));
			return mock(ScheduledFuture.class);
		}).when(executor).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
	}

	@Test public void simultaneousEventsShareOneFrameAndEncoder() throws Exception {
		CompletableFuture<byte[]> first = screenshots.captureScreenshot();
		for (int i = 0; i < 1000; i++) assertSame(first, screenshots.captureScreenshot());
		draw.processDrawComplete(() -> new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
		assertEquals(1, tasks.size());
		assertFalse(first.isDone());
		while (!tasks.isEmpty()) tasks.remove().run();
		assertEquals(20, ImageIO.read(new ByteArrayInputStream(first.join())).getWidth());
		verify(chat).setHidden(true);
		verify(chat).setHidden(false);
		verify(pm, never()).setHidden(anyBoolean());
	}

	@Test public void missingFramesTimeOutAndRestoreChatWithoutAddingMoreListeners() {
		CompletableFuture<byte[]> first = screenshots.captureScreenshot();
		timeout.get().run();
		assertNull(first.join());
		for (int i = 0; i < 1000; i++) assertSame(first, screenshots.captureScreenshot());
		verify(executor, times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
		verify(chat).setHidden(false);
		draw.processDrawComplete(() -> new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
		assertTrue(tasks.isEmpty());
		assertNotSame(first, screenshots.captureScreenshot());
		screenshots.shutDown();
	}

	@Test public void aDiscardedRuneLiteFrameDoesNotWedgeFutureCaptures() {
		CompletableFuture<byte[]> first = screenshots.captureScreenshot();
		draw.processDrawComplete(() -> null);
		timeout.get().run();
		draw.processDrawComplete(() -> null);
		assertNull(first.join());
		assertNotSame(first, screenshots.captureScreenshot());
		screenshots.shutDown();
	}

	@Test public void shutdownRestoresChatAndLateFramesDoNoWork() {
		CompletableFuture<byte[]> first = screenshots.captureScreenshot();
		screenshots.shutDown();
		assertNull(first.join());
		verify(chat).setHidden(false);
		draw.processDrawComplete(() -> new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
		assertTrue(tasks.isEmpty());
		assertNull(screenshots.captureScreenshot().join());
		screenshots.startUp();
		draw.processDrawComplete(() -> null);
		assertNotSame(first, screenshots.captureScreenshot());
		screenshots.shutDown();
	}

	@Test public void aRejectedEncoderStillCompletesTheNotification() {
		doThrow(new java.util.concurrent.RejectedExecutionException()).when(executor).execute(any(Runnable.class));
		CompletableFuture<byte[]> first = screenshots.captureScreenshot();
		draw.processDrawComplete(() -> new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
		assertNull(first.join());
		verify(chat).setHidden(false);
	}

	@Test public void aRejectedTimeoutDoesNotLeaveAFrameListenerOrHiddenChat() {
		doThrow(new java.util.concurrent.RejectedExecutionException()).when(executor)
			.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
		assertNull(screenshots.captureScreenshot().join());
		draw.processDrawComplete(() -> { fail("No screenshot should have been requested"); return null; });
		verify(chat, never()).setHidden(anyBoolean());
	}

	@Test public void aBlockedEncoderCannotAccumulateMoreImagesAfterTimeout() {
		CompletableFuture<byte[]> first = screenshots.captureScreenshot();
		draw.processDrawComplete(() -> new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
		timeout.get().run();
		for (int i = 0; i < 1000; i++) {
			assertSame(first, screenshots.captureScreenshot());
			draw.processDrawComplete(() -> new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
		}
		assertEquals(1, tasks.size());
		tasks.remove().run();
		assertNotSame(first, screenshots.captureScreenshot());
		screenshots.shutDown();
	}
}

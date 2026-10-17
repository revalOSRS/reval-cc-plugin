package com.revalclan;

import com.revalclan.notifiers.DeathNotifier;
import com.revalclan.notifiers.LootNotifier;
import com.revalclan.notifiers.PetNotifier;
import com.revalclan.session.SessionTracker;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import javax.inject.Inject;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RevalClanPluginTest {
	@Test public void shutdownReleasesNotifierStateOnTheClientThread() throws Exception {
		RevalClanPlugin plugin = new RevalClanPlugin();
		Map<Class<?>, Object> injected = new HashMap<>();
		for (Field field : RevalClanPlugin.class.getDeclaredFields()) {
			if (!field.isAnnotationPresent(Inject.class)) continue;
			Object dependency = mock(field.getType());
			field.setAccessible(true);
			field.set(plugin, dependency);
			injected.put(field.getType(), dependency);
		}
		ClientThread clientThread = (ClientThread) injected.get(ClientThread.class);
		Queue<Runnable> tasks = new ArrayDeque<>();
		doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(clientThread).invoke(any(Runnable.class));
		plugin.shutDown();
		LootNotifier loot = (LootNotifier) injected.get(LootNotifier.class);
		verify(loot, never()).reset();
		assertEquals(1, tasks.size());
		tasks.remove().run();
		verify(loot).reset();
		verify((DeathNotifier) injected.get(DeathNotifier.class)).reset();
		verify((PetNotifier) injected.get(PetNotifier.class)).reset();
		verify((SessionTracker) injected.get(SessionTracker.class)).reset();
	}
}

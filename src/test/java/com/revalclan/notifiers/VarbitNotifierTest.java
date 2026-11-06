package com.revalclan.notifiers;

import com.revalclan.util.EventFilterManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class VarbitNotifierTest {
	@Test public void removedWatchesAreNotRetainedAndFlushedInLaterBatches() throws Exception {
		List<Map<String, Object>> sent = new ArrayList<>();
		ClientThread clientThread = mock(ClientThread.class);
		VarbitNotifier notifier = new VarbitNotifier(clientThread) {
			@Override protected void sendCompactNotification(String eventType, Map<String, Object> data) { sent.add(data); }
		};
		notifier.filterManager = new EventFilterManager(mock(okhttp3.OkHttpClient.class), new com.google.gson.Gson());
		notifier.client = mock(Client.class);
		when(notifier.client.getGameState()).thenReturn(GameState.LOGGED_IN);
		doAnswer(invocation -> { invocation.<Runnable>getArgument(0).run(); return null; })
			.when(clientThread).invokeLater(any(Runnable.class));
		notifier.filterManager.getFilters().getVarbitWatch().add(1);
		notifier.syncBaselines();
		VarbitChanged change = new VarbitChanged();
		change.setVarbitId(1);
		change.setValue(10);
		notifier.onVarbitChanged(change);
		notifier.filterManager.getFilters().getVarbitWatch().clear();
		notifier.filterManager.getFilters().getVarbitWatch().add(2);
		notifier.syncBaselines();
		sent.clear();
		for (int i = 0; i < 50; i++) notifier.onGameTick();
		assertTrue("Only current watches may survive a filter update", sent.isEmpty());
	}
}

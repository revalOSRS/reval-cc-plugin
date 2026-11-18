package com.revalclan.notifiers;

import com.revalclan.util.RaidPartyTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class KillCountNotifierTest {
	@Mock private RaidPartyTracker tracker;
	private final List<Map<String, Object>> sent = new ArrayList<>();
	@InjectMocks private KillCountNotifier notifier = new KillCountNotifier() {
		@Override public boolean isEnabled() { return true; }
		@Override protected void sendNotification(Map<String, Object> data) { sent.add(data); }
	};

	@Before public void setup() {
		lenient().when(tracker.completionSource(anyString())).thenAnswer(call -> {
			String source = call.getArgument(0);
			return source.contains("Practice") ? "The Fractured Archive: Practice Mode" : source;
		});
	}

	@Test public void practiceCompletionKeepsItsOwnCounterAndOmitsPhaseSkippedTimes() {
		notifier.onChatMessage("Your completed Fractured Archive: Practice Mode count is: 4.");
		notifier.onChatMessage("Fractured Archive: Practice Mode: Total completion time: 1:00.0 (new personal best)");
		notifier.onTick();
		assertEquals(1, sent.size());
		assertEquals("The Fractured Archive: Practice Mode", sent.get(0).get("boss"));
		assertEquals(4, sent.get(0).get("killCount"));
		assertFalse(sent.get(0).containsKey("timeSeconds"));
		assertFalse(sent.get(0).containsKey("personalBest"));
	}

	@Test public void nativePracticeEvidenceCanLabelAnUnsuffixedCompletion() {
		when(tracker.completionSource("Fractured Archive")).thenReturn("The Fractured Archive: Practice Mode");
		notifier.onChatMessage("Your Fractured Archive completion count is: 7.");
		notifier.onTick();
		assertEquals("The Fractured Archive: Practice Mode", sent.get(0).get("boss"));
		assertEquals(7, sent.get(0).get("killCount"));
	}

	@Test public void phasesAndBossDeathsDoNotManufacturePracticeCompletions() {
		notifier.onChatMessage("Ocellus has been defeated.");
		notifier.onChatMessage("Duration: 1:23.4");
		for (int i = 0; i < 12; i++) notifier.onTick();
		assertTrue(sent.isEmpty());
	}

	@Test public void normalArchiveCompletionKeepsItsRaidTime() {
		notifier.onChatMessage("Your completed Fractured Archive count is: 12.");
		notifier.onChatMessage("Fractured Archive: Total completion time: 20:00.0");
		notifier.onTick();
		assertEquals(12, sent.get(0).get("killCount"));
		assertEquals(1200.0, sent.get(0).get("timeSeconds"));
	}
}

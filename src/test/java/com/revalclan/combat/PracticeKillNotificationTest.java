package com.revalclan.combat;

import com.google.gson.Gson;
import com.revalclan.notifiers.DetailedKillNotifier;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.RaidPartyTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PracticeKillNotificationTest {
	@Mock private RaidPartyTracker tracker;
	@Spy private EventFilterManager filters = new EventFilterManager(mock(okhttp3.OkHttpClient.class), new Gson());
	private final List<Map<String, Object>> sent = new ArrayList<>();
	@InjectMocks private DetailedKillNotifier notifier = new DetailedKillNotifier() {
		@Override public boolean isEnabled() { return true; }
		@Override protected void sendNotification(Map<String, Object> data) { sent.add(data); }
	};

	@Test public void repeatedPracticeBossesDoNotScoreNpcKillsButNormalBossesStillDo() {
		KillTracker.KillData kill = new KillTracker.KillData("Ocellus", 1);
		when(tracker.isArchivePractice()).thenReturn(true);
		notifier.onKill(kill);
		notifier.onKill(kill);
		assertTrue(sent.isEmpty());
		when(tracker.isArchivePractice()).thenReturn(false);
		notifier.onKill(kill);
		assertEquals(1, sent.size());
		assertEquals("Ocellus", sent.get(0).get("npcName"));
	}
}

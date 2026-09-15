package com.revalclan.util;
import org.junit.Test;
import static org.junit.Assert.*;
public class NotificationPollBackoffTest {
	private void expectAfter(NotificationPollBackoff poll, int ticks) {
		for (int i = 1; i < ticks; i++) assertFalse(poll.tick());
		assertTrue(poll.tick());
	}
	@Test public void capsEmptyPollsAndRestoresFastPollAfterActivityOrFailure() {
		NotificationPollBackoff poll = new NotificationPollBackoff();
		poll.completed(true); expectAfter(poll, 1000);
		poll.completed(true); expectAfter(poll, 1500);
		poll.completed(true); expectAfter(poll, 1500);
		poll.completed(false); expectAfter(poll, 500);
		poll.reset(); assertTrue(poll.tick());
	}
}

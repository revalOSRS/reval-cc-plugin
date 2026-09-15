package com.revalclan.util;

/** Empty polls back off to fifteen minutes; activity/errors restore five. Owned by AnnouncementService's lock. */
final class NotificationPollBackoff {
	private static final int BASE_TICKS = 500;
	private static final int MAX_TICKS = 1500;
	private int interval = BASE_TICKS;
	private int remaining;

	boolean tick() {
		return --remaining <= 0;
	}

	void completed(boolean empty) {
		interval = empty ? Math.min(MAX_TICKS, interval + BASE_TICKS) : BASE_TICKS;
		remaining = interval;
	}

	void reset() {
		interval = BASE_TICKS;
		remaining = 0;
	}
}

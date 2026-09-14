package com.revalclan.util;

/** Empty polls back off from five to fifteen minutes; activity/errors restore five. */
final class NotificationPollBackoff {
    private int interval = 500;
    private int remaining;
    synchronized boolean tick() { return --remaining <= 0; }
    synchronized void completed(boolean empty) {
        interval = empty ? Math.min(1500, interval + 500) : 500;
        remaining = interval;
    }
    synchronized void reset() { interval = 500; remaining = 0; }
}

package com.revalclan.util;

/** Shared across plugin restarts, including cancelled preparation still waiting on the worker. */
final class WebhookCapacity {
	private static final int MAX_PENDING = 64;
	private static final int MAX_ORDINARY = 48;
	private final Runnable warn;
	private int pending, ordinary;
	private boolean warned;

	WebhookCapacity(Runnable warn) { this.warn = warn; }

	boolean acquire(boolean critical) {
		boolean report;
		synchronized (this) {
			if (pending < MAX_PENDING && (critical || ordinary < MAX_ORDINARY)) {
				pending++;
				if (!critical) ordinary++;
				return true;
			}
			report = !warned;
			warned = true;
		}
		if (report) warn.run();
		return false;
	}

	synchronized void release(boolean critical) {
		if (!critical) ordinary--;
		if (--pending == 0) warned = false;
	}

	static boolean isCritical(Object type) {
		if (!(type instanceof String)) return false;
		String event = (String) type;
		return event.equals("LOGIN") || event.equals("LOGOUT") || event.equals("SYNC")
			|| event.equals("LEAGUES_SYNC") || event.startsWith("SESSION_");
	}
}

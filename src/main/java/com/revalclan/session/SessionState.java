package com.revalclan.session;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Singleton;

/** Participants register during injection, so new notifiers join the same lifecycle automatically. */
@Singleton
public final class SessionState {
	private final Set<SessionScoped> participants = new LinkedHashSet<>();
	public void register(SessionScoped participant) { participants.add(participant); }

	/**
	 * Drop partial buffers on logout: their original player/world context is no
	 * longer available for a reliable send. Completed session totals persist separately.
	 */
	public void reset() { participants.forEach(SessionScoped::reset); }
}

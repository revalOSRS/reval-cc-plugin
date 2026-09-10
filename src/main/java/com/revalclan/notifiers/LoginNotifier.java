package com.revalclan.notifiers;

import javax.inject.Singleton;

import com.google.inject.Inject;
import com.revalclan.PlayerDataCollector;
import com.revalclan.session.SessionTracker;
import com.revalclan.util.SyncStateManager;

import java.util.Map;

/**
 * Sends the LOGIN boundary payload (full or slim depending on the fingerprint).
 */
@Singleton
public class LoginNotifier extends BaseNotifier {
	@Inject
	private PlayerDataCollector dataCollector;

	@Inject
	private SyncStateManager syncStateManager;

	@Inject
	private SessionTracker sessionTracker;

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	protected String getEventType() {
		return "LOGIN";
	}

	/**
	 * Called when the player logs in.
	 */
	public void onLogin() {
		Map<String, Object> data = dataCollector.collectBoundaryData();
		// Jagex's own "Time played" (minutes), sent to the client at login — server-side calibration
		int playtime = sessionTracker.getLastKnownPlaytimeMinutes();
		if (playtime > 0) data.put("playtimeMinutes", playtime);
		sendNotification(data, syncStateManager.ackHandler(client.getAccountHash()));
	}
}

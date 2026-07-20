package com.revalclan.notifiers;

import javax.inject.Singleton;

import com.google.inject.Inject;
import com.revalclan.PlayerDataCollector;
import com.revalclan.util.SyncStateManager;

import java.util.Map;

/**
 * Notifies when a player logs in.
 * Sends the session-boundary payload: full state when it changed since the last
 * server ack, or a slim player+fingerprint payload when unchanged (v2.17+).
 */
@Singleton
public class LoginNotifier extends BaseNotifier {
	@Inject
	private PlayerDataCollector dataCollector;

	@Inject
	private SyncStateManager syncStateManager;

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
		long accountHash = client.getAccountHash();
		sendNotificationWithResponse(data, response ->
			syncStateManager.handleSyncAckResponse(response, accountHash));
	}
}

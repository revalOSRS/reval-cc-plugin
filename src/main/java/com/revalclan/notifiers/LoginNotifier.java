package com.revalclan.notifiers;

import javax.inject.Singleton;

import com.google.inject.Inject;
import com.revalclan.PlayerDataCollector;
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
		sendNotification(data, response ->
			syncStateManager.handleSyncAckResponse(response, accountHash));
	}
}

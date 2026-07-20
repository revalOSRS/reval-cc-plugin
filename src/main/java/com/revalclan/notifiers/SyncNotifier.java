package com.revalclan.notifiers;

import com.revalclan.PlayerDataCollector;
import com.revalclan.util.SyncStateManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Handles full account sync events.
 * Triggered manually via the collection log button, or automatically when the
 * server reports the sync fingerprint as stale. ALWAYS sends the full state —
 * this is the repair path for fingerprint drift.
 */
@Singleton
public class SyncNotifier extends BaseNotifier {
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
		return "SYNC";
	}

	/**
	 * Trigger a full account sync.
	 * Collects all player data (collection log, quests, diaries, combat achievements, etc.)
	 * and sends it to the webhook.
	 */
	public void triggerSync() {
		Map<String, Object> data = dataCollector.collectAllData();
		long accountHash = client.getAccountHash();
		sendNotificationWithResponse(data, response ->
			syncStateManager.handleSyncAckResponse(response, accountHash));
	}
}

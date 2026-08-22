package com.revalclan.notifiers;

import com.revalclan.PlayerDataCollector;
import com.revalclan.util.SyncStateManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Full account sync — manual button press or server-requested fingerprint
 * repair. ALWAYS sends the full state.
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
		Map<String, Object> data = dataCollector.collectSyncData();
		long accountHash = client.getAccountHash();
		sendNotification(data, response ->
			syncStateManager.handleSyncAckResponse(response, accountHash));
	}
}

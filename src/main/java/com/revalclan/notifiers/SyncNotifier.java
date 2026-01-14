package com.revalclan.notifiers;

import com.revalclan.PlayerDataCollector;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Handles full account sync events.
 * This is triggered on logout or manually via the collection log button.
 */
@Singleton
public class SyncNotifier extends BaseNotifier {
	@Inject
	private PlayerDataCollector dataCollector;

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
		sendNotification(data);
	}
}


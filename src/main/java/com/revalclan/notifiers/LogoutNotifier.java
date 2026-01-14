package com.revalclan.notifiers;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.revalclan.PlayerDataCollector;

/**
 * Handles logout events.
 * Triggers a full account sync when the player logs out.
 */
@Singleton
public class LogoutNotifier extends BaseNotifier {
	@Inject
	private PlayerDataCollector dataCollector;

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	protected String getEventType() {
		return "LOGOUT";
	}

	/**
	 * Called when the player logs out.
	 * Triggers a full account sync.
	 */
	public void onLogout() {
		Map<String, Object> data = dataCollector.collectAllData();
		sendNotification(data);
	}
}


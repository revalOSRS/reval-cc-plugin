package com.revalclan.notifiers;

import javax.inject.Singleton;

import com.google.inject.Inject;
import com.revalclan.PlayerDataCollector;

import java.util.Map;

/**
 * Notifies when a player logs in.
 * Sends a lightweight LOGIN event with basic player info.
 */
@Singleton
public class LoginNotifier extends BaseNotifier {
	@Inject
	private PlayerDataCollector dataCollector;
	
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
		Map<String, Object> data = dataCollector.collectAllData();
		sendNotification(data);
	}
}


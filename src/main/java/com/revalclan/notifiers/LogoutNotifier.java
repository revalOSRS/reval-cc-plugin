package com.revalclan.notifiers;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.revalclan.PlayerDataCollector;
import com.revalclan.session.SessionTracker;
import com.revalclan.util.SyncStateManager;

/**
 * Sends the LOGOUT boundary payload plus the session summary accumulated since login.
 */
@Singleton
public class LogoutNotifier extends BaseNotifier {
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
		return "LOGOUT";
	}

	/** The clan channel is already torn down when LOGOUT fires; RevalClanPlugin gates on wasInClan. */
	@Override
	protected boolean passesClanCheck() {
		return true;
	}

	/** @param sessionSummary Finalized summary from SessionTracker (nullable) */
	public void onLogout(Map<String, Object> sessionSummary) {
		Map<String, Object> data = dataCollector.collectBoundaryData();
		if (sessionSummary != null) {
			data.put("sessionSummary", sessionSummary);
		}
		long accountHash = client.getAccountHash();
		String sessionId = sessionSummary != null ? (String) sessionSummary.get("sessionId") : null;
		sendNotification(data, response -> {
			syncStateManager.handleSyncAckResponse(response, accountHash);
			if (sessionId != null) {
				sessionTracker.confirmDelivered(sessionId);
			}
		});
	}
}

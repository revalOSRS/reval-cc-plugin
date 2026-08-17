package com.revalclan.notifiers;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.revalclan.PlayerDataCollector;
import com.revalclan.session.SessionTracker;
import com.revalclan.util.PayloadDebugDumper;
import com.revalclan.util.SyncStateManager;

/**
 * Handles logout events.
 * Sends the session-boundary payload (full state when changed since the last
 * server ack, slim otherwise) plus the client-side session summary accumulated
 * since login.
 */
@Singleton
public class LogoutNotifier extends BaseNotifier {
	@Inject
	private PlayerDataCollector dataCollector;

	@Inject
	private SyncStateManager syncStateManager;

	@Inject
	private SessionTracker sessionTracker;

	@Inject
	private PayloadDebugDumper payloadDumper;

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
	 *
	 * @param sessionSummary Finalized session summary from SessionTracker (nullable)
	 */
	public void onLogout(Map<String, Object> sessionSummary) {
		Map<String, Object> data = dataCollector.collectBoundaryData();
		if (sessionSummary != null) {
			data.put("sessionSummary", sessionSummary);
		}
		long accountHash = client.getAccountHash();
		String sessionId = sessionSummary != null ? (String) sessionSummary.get("sessionId") : null;
		sendNotificationPrevalidated(data, response -> {
			syncStateManager.handleSyncAckResponse(response, accountHash);
			if (sessionId != null) {
				sessionTracker.confirmDelivered(sessionId);
			}
		});
		payloadDumper.dump("logout-sent", data);
	}
}

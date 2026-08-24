package com.revalclan.events;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.util.ClanRanks;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin-only: tracks which clan members have registered for an upcoming
 * event. {@link RegistrationMarksOverlay} draws a checkmark after their name
 * in the clan sidepanel and shows the event(s) on hover. Fetches go through
 * the service's events cache; this class only derives the lookup map.
 */
@Slf4j
@Singleton
public class RegistrationMarks {
	private final Client client;
	private final RevalApiService apiService;

	/** Standardized nickname -> comma-joined upcoming event names */
	private volatile Map<String, String> registrations = Map.of();

	@Inject
	public RegistrationMarks(Client client, RevalApiService apiService) {
		this.client = client;
		this.apiService = apiService;
	}

	public void startUp() {
		refresh();
	}

	Map<String, String> getRegistrations() {
		return registrations;
	}

	/** Marks render only for staff viewers. */
	boolean isActive() {
		return ClanRanks.isDeputyOwnerPlus(client);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			refresh();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == ScriptID.CLAN_SIDEPANEL_DRAW) {
			refresh();
		}
	}

	private void refresh() {
		apiService.fetchEvents(
			response -> {
				Map<String, String> map = new HashMap<>();
				if (response.getData() != null && response.getData().getEvents() != null) {
					for (EventsResponse.EventSummary event : response.getData().getEvents()) {
						if (!event.isUpcoming() || event.getRegistrations() == null) {
							continue;
						}
						for (EventsResponse.EventRegistration reg : event.getRegistrations()) {
							if (!reg.isRegistered() || reg.getOsrsNickname() == null) {
								continue;
							}
							map.merge(Text.standardize(reg.getOsrsNickname()), event.getName(),
								(a, b) -> a + ", " + b);
						}
					}
				}
				registrations = map;
			},
			error -> log.debug("Failed to fetch event registrations", error)
		);
	}
}

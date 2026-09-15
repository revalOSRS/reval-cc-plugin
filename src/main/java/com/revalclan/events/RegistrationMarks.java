package com.revalclan.events;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.util.ClanRanks;
import net.runelite.api.Client;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin-only: tracks which clan members have registered for an upcoming
 * event. {@link RegistrationMarksOverlay} draws a checkmark after their name
 * in the clan sidepanel and shows the event(s) on hover. Uses only the last
 * explicitly fetched event list; startup/login/redraw never request it.
 */
@Singleton
public class RegistrationMarks {
	private final Client client;

	/** Standardized nickname -> comma-joined upcoming event names */
	private volatile Map<String, String> registrations = Map.of();

	@Inject
	public RegistrationMarks(Client client, RevalApiService apiService) {
		this.client = client;
		apiService.addEventsListener(this::update);
	}


	Map<String, String> getRegistrations() {
		return registrations;
	}

	/** Marks render only for staff viewers. */
	boolean isActive() {
		return ClanRanks.isDeputyOwnerPlus(client);
	}

	private void update(EventsResponse response) {
		Map<String, String> map = new HashMap<>();
		if (response != null && response.getData() != null && response.getData().getEvents() != null) {
			for (EventsResponse.EventSummary event : response.getData().getEvents()) {
				if (!event.isUpcoming() || event.getRegistrations() == null) continue;
				for (EventsResponse.EventRegistration reg : event.getRegistrations()) {
					if (!reg.isRegistered() || reg.getOsrsNickname() == null) continue;
					map.merge(Text.standardize(reg.getOsrsNickname()), event.getName(), (a, b) -> a + ", " + b);
				}
			}
		}
		registrations = Map.copyOf(map);
	}
}

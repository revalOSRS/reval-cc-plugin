package com.revalclan.events;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.util.PlayerNames;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin-only: tracks which clan members have registered for an upcoming
 * event. {@link RegistrationMarksOverlay} draws a checkmark after their name
 * in the clan sidepanel and shows the event(s) on hover.
 */
@Slf4j
@Singleton
public class RegistrationMarks {
	/** Same in-game gate as the side panel's admin button (Deputy Owner+). */
	private static final int ADMIN_MIN_CLAN_RANK = 125;
	private static final long REFRESH_MS = 5 * 60_000;

	private final Client client;
	private final RevalApiService apiService;

	/** Normalized nickname -> comma-joined upcoming event names */
	private volatile Map<String, String> registrations = Map.of();
	private volatile long fetchedAt;
	private volatile boolean fetching;

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

	boolean isAdminViewer() {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return false;
		}
		ClanChannel clanChannel = client.getClanChannel();
		String name = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		if (clanChannel == null || name == null) {
			return false;
		}
		ClanChannelMember member = clanChannel.findMember(name);
		return member != null && member.getRank().getRank() >= ADMIN_MIN_CLAN_RANK;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			refresh();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == ScriptID.CLAN_SIDEPANEL_DRAW
			&& System.currentTimeMillis() - fetchedAt > REFRESH_MS) {
			refresh();
		}
	}

	private void refresh() {
		if (fetching) {
			return;
		}
		fetching = true;
		apiService.fetchEvents(
			response -> {
				fetching = false;
				fetchedAt = System.currentTimeMillis();
				Map<String, String> map = new HashMap<>();
				if (response.getData() != null && response.getData().getEvents() != null) {
					for (EventsResponse.EventSummary event : response.getData().getEvents()) {
						if (!event.isUpcoming() || event.getRegistrations() == null) {
							continue;
						}
						for (EventsResponse.EventRegistration reg : event.getRegistrations()) {
							if (!"registered".equalsIgnoreCase(reg.getStatus()) || reg.getOsrsNickname() == null) {
								continue;
							}
							map.merge(PlayerNames.normalize(reg.getOsrsNickname()), event.getName(),
								(a, b) -> a + ", " + b);
						}
					}
				}
				registrations = map;
			},
			error -> {
				fetching = false;
				// Keep the stale map and back off until the next refresh window
				fetchedAt = System.currentTimeMillis();
				log.debug("Failed to fetch event registrations", error);
			}
		);
	}
}

package com.revalclan.teams;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.ActiveTeamsResponse;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Event rosters from the backend: maps clan member nicknames to their team's
 * hex color. Network fetches are cached by {@link RevalApiService}; this class
 * only derives the lookup map.
 *
 * The endpoint serves teams for events that are running or being planned, so
 * rosters are colorable from the draft onward rather than only once the event
 * starts. Empty (no coloring) when no event has teams yet or the fetch fails.
 */
@Slf4j
@Singleton
public class ActiveTeamColors {
	/** Teams whose color is unset get the backend's default gray. */
	private static final Color FALLBACK_COLOR = new Color(0x888888);

	private final RevalApiService apiService;

	/** Standardized nickname -> team color */
	private volatile Map<String, Color> colors = Map.of();

	@Inject
	public ActiveTeamColors(RevalApiService apiService) {
		this.apiService = apiService;
	}

	public Color teamColorFor(String playerName) {
		return playerName != null ? colors.get(Text.standardize(playerName)) : null;
	}

	/** Rebuild the derived roster; the service cache throttles the network. */
	public void refresh() {
		apiService.fetchActiveTeams(
			response -> {
				Map<String, Color> map = new HashMap<>();
				if (response.getData() != null && response.getData().getTeams() != null) {
					for (ActiveTeamsResponse.Team team : response.getData().getTeams()) {
						if (team.getMembers() == null) {
							continue;
						}
						Color color = parseColor(team.getColor());
						for (String member : team.getMembers()) {
							// Newest event first in the response; first match wins
							map.putIfAbsent(Text.standardize(member), color);
						}
					}
				}
				colors = map;
			},
			error -> log.debug("Failed to fetch active event teams", error)
		);
	}

	private static Color parseColor(String hex) {
		if (hex == null || hex.isEmpty()) {
			return FALLBACK_COLOR;
		}
		try {
			return Color.decode(hex);
		} catch (NumberFormatException e) {
			return FALLBACK_COLOR;
		}
	}
}

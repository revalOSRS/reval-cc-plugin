package com.revalclan.teams;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.ActiveTeamsResponse;
import com.revalclan.util.PlayerNames;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Active event rosters from the backend: maps clan member nicknames to their
 * team's name and hex color. Refreshes lazily at most every five minutes;
 * empty (no coloring) when no event is active or the fetch fails.
 */
@Slf4j
@Singleton
public class ActiveTeamColors {
	private static final long REFRESH_MS = 5 * 60_000;
	/** Teams whose color is unset get the backend's default gray. */
	private static final Color FALLBACK_COLOR = new Color(0x888888);

	/** Preview roster until the active-teams endpoint is deployed: flip to false to go live. */
	private static final boolean MOCK_ROSTER = true;
	private static final Team[] MOCK_TEAMS = {
		new Team("Zamorak", new Color(0xE64545)),
		new Team("Saradomin", new Color(0x3B8AE0)),
		new Team("Guthix", new Color(0x3FBF4A)),
		new Team("Zaros", new Color(0xA85CE0)),
	};

	@Value
	static class Team {
		String name;
		Color color;
	}

	private final RevalApiService apiService;

	/** Normalized nickname -> team */
	private volatile Map<String, Team> teams = Map.of();
	private volatile long fetchedAt;
	private volatile boolean fetching;

	@Inject
	public ActiveTeamColors(RevalApiService apiService) {
		this.apiService = apiService;
	}

	public Color teamColorFor(String playerName) {
		Team team = teamFor(playerName);
		return team != null ? team.getColor() : null;
	}

	public String teamNameFor(String playerName) {
		Team team = teamFor(playerName);
		return team != null ? team.getName() : null;
	}

	private Team teamFor(String playerName) {
		String key = PlayerNames.normalize(playerName);
		if (key.isEmpty()) {
			return null;
		}
		if (MOCK_ROSTER) {
			// A third of players stay unassigned, previewing the mixed look
			int bucket = Math.abs(key.hashCode()) % (MOCK_TEAMS.length + 2);
			return bucket < MOCK_TEAMS.length ? MOCK_TEAMS[bucket] : null;
		}
		return teams.get(key);
	}

	/** Cheap to call often; refetches at most once per window. */
	public void ensureFresh() {
		if (MOCK_ROSTER || fetching || System.currentTimeMillis() - fetchedAt < REFRESH_MS) {
			return;
		}
		fetching = true;
		apiService.fetchActiveTeams(
			response -> {
				fetching = false;
				fetchedAt = System.currentTimeMillis();
				Map<String, Team> map = new HashMap<>();
				if (response.getData() != null && response.getData().getTeams() != null) {
					for (ActiveTeamsResponse.Team team : response.getData().getTeams()) {
						if (team.getMembers() == null || team.getName() == null) {
							continue;
						}
						Team entry = new Team(team.getName(), parseColor(team.getColor()));
						for (String member : team.getMembers()) {
							map.putIfAbsent(PlayerNames.normalize(member), entry);
						}
					}
				}
				teams = map;
			},
			error -> {
				fetching = false;
				// Keep the stale roster and back off until the next window
				fetchedAt = System.currentTimeMillis();
				log.debug("Failed to fetch active event teams", error);
			}
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

package com.revalclan.teams;

import com.revalclan.util.PlayerNames;

import javax.inject.Singleton;
import java.awt.Color;

/**
 * Preview stand-in for the real event roster: deterministically assigns
 * players to one of four mock teams by name hash, so the coloring is stable
 * across chat lines and sidepanel rebuilds. Replaced by an API-backed
 * provider once the active-event teams endpoint exists.
 */
@Singleton
public class MockTeamColorProvider implements TeamColorProvider {
	private static final Color[] TEAM_COLORS = {
		new Color(0xFF6B6B), // red
		new Color(0x4DA6FF), // blue
		new Color(0x6BCB77), // green
		new Color(0xC77DFF), // purple
	};

	@Override
	public Color teamColorFor(String playerName) {
		if (playerName == null || playerName.isEmpty()) {
			return null;
		}
		String key = PlayerNames.normalize(playerName);
		if (key.isEmpty()) {
			return null;
		}
		// A third of players land outside the "event", previewing the mixed look
		int bucket = Math.abs(key.hashCode()) % (TEAM_COLORS.length + 2);
		return bucket < TEAM_COLORS.length ? TEAM_COLORS[bucket] : null;
	}
}

package com.revalclan.teams;

import java.awt.Color;

/**
 * Maps a clan member's in-game name to their event team color, or null when
 * the player is not on a team (or no event is active).
 */
public interface TeamColorProvider {
	Color teamColorFor(String playerName);
}

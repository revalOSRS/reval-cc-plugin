package com.revalclan.api.leaguesbingo;

import com.google.gson.JsonObject;
import com.revalclan.api.common.PublicApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.util.Text;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Response for the public GET /leagues-bingo/events/{id}: the payload the
 * homepage renders, trimmed to the fields the side panel reads. Gson ignores
 * everything else.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaguesBingoResponse extends PublicApiResponse {
	private Payload data;

	@Data
	public static class Payload {
		private EventInfo event;
		private Config config;
		private boolean tilesHidden;
		private String tilesRevealAt;
		private List<Board> boards;
		private List<Team> teams;

		public List<Board> getBoards() {
			return boards != null ? boards : Collections.emptyList();
		}

		public List<Team> getTeams() {
			return teams != null ? teams : Collections.emptyList();
		}

		public Board boardFor(String region) {
			for (Board b : getBoards()) {
				if (region != null && region.equals(b.getRegion())) return b;
			}
			return null;
		}

		public Team teamById(String id) {
			for (Team t : getTeams()) {
				if (id != null && id.equals(t.getId())) return t;
			}
			return null;
		}

		/** Regions every team starts with; these are never pickable. */
		public boolean isDefaultRegion(String region) {
			return config != null && config.getDefaultRegions() != null && config.getDefaultRegions().contains(region);
		}
	}

	@Data
	public static class EventInfo {
		private String status;
		private String endDate;
	}

	@Data
	public static class Config {
		private List<String> defaultRegions;
	}

	@Data
	public static class Board {
		private String region;
		private int rows;
		private int columns;
		private int totalPoints;
		/** Null while the reveal gate is closed. */
		private List<Tile> tiles;

		public List<Tile> getTiles() {
			return tiles != null ? tiles : Collections.emptyList();
		}

		public Tile tileAt(String position) {
			for (Tile t : getTiles()) {
				if (position.equalsIgnoreCase(t.getPosition())) return t;
			}
			return null;
		}
	}

	@Data
	public static class Tile {
		private String boardTileId;
		/** "A1" style: letter = column, number = row. */
		private String position;
		private int points;
		private String task;
		private String description;
		private String category;
		private String difficulty;
		/** OSRS wiki image name, kept for the one client-side fallback. */
		private String icon;
		/** Game item id behind the icon, resolved by the backend; null when it could not. */
		private Integer iconItemId;
		private Requirements requirements;
	}

	@Data
	public static class Requirements {
		private String matchType;
		/** Kept loose: each requirement type carries its own fields. */
		private List<JsonObject> requirements;

		public List<JsonObject> getRequirements() {
			return requirements != null ? requirements : Collections.emptyList();
		}

		public boolean isAnyMatch() {
			return "any".equalsIgnoreCase(matchType);
		}
	}

	@Data
	public static class Team {
		private String id;
		private String name;
		private String color;
		private int score;
		private List<Member> members;
		private List<UnlockedRegion> unlockedRegions;
		private int uniqueCompletedTiles;
		private PickTokens pickTokens;
		/** Keyed by boardTileId. */
		private Map<String, Completion> completions;
		/** Keyed by boardTileId; absent while tiles are hidden. */
		private Map<String, TileProgress> progress;

		public List<UnlockedRegion> getUnlockedRegions() {
			return unlockedRegions != null ? unlockedRegions : Collections.emptyList();
		}

		public UnlockedRegion unlockFor(String region) {
			for (UnlockedRegion u : getUnlockedRegions()) {
				if (region != null && region.equals(u.getRegion())) return u;
			}
			return null;
		}

		public boolean hasUnlocked(String region) {
			return unlockFor(region) != null;
		}

		public int availableTokens() {
			return pickTokens != null ? pickTokens.getAvailable() : 0;
		}

		public Completion completionFor(String boardTileId) {
			return completions != null && boardTileId != null ? completions.get(boardTileId) : null;
		}

		public TileProgress progressFor(String boardTileId) {
			return progress != null && boardTileId != null ? progress.get(boardTileId) : null;
		}

		public boolean hasMember(String playerName) {
			if (playerName == null || members == null) return false;
			String wanted = Text.standardize(playerName);
			for (Member m : members) {
				if (m.getDisplayName() != null && wanted.equals(Text.standardize(m.getDisplayName()))) return true;
			}
			return false;
		}
	}

	@Data
	public static class Member {
		private String displayName;
	}

	@Data
	public static class UnlockedRegion {
		private String region;
		private String unlockedAt;
		private String boardCompletedAt;
		private Integer bonusPoints;
	}

	@Data
	public static class PickTokens {
		private int available;
	}

	@Data
	public static class Completion {
		private String completedAt;
	}

	@Data
	public static class TileProgress {
		private Double value;
		private Double target;
		private List<Integer> completedRequirementIndices;
		private Integer totalRequirements;
		/** Keyed by requirement index as a string ("0", "1", ...). */
		private Map<String, RequirementProgress> requirementProgress;

		public int completedRequirementCount() {
			return completedRequirementIndices != null ? completedRequirementIndices.size() : 0;
		}

		public RequirementProgress requirement(int index) {
			return requirementProgress != null ? requirementProgress.get(String.valueOf(index)) : null;
		}
	}

	@Data
	public static class RequirementProgress {
		private boolean isCompleted;
		private Double progressValue;
		/** targetValue, currentTotalCount, playerContributions[], ... per requirement type. */
		private JsonObject progressMetadata;
	}
}

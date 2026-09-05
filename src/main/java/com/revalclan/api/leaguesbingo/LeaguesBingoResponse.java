package com.revalclan.api.leaguesbingo;

import com.google.gson.JsonObject;
import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Response for GET https://api.revalosrs.ee/leagues-bingo/events/{id}.
 * This is the same public payload the homepage renders: every region board
 * with its tiles, and every team with its unlocks, completions and progress.
 * The public routes answer with {"success": true, "data": ...} rather than
 * the plugin routes' {"status": "success"}, hence the extra flag here.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaguesBingoResponse extends ApiResponse {
	private Boolean success;
	private Payload data;

	@Override
	public boolean isSuccess() {
		return Boolean.TRUE.equals(success) || super.isSuccess();
	}

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
	}

	@Data
	public static class EventInfo {
		private String id;
		private String name;
		private String description;
		private String status;
		private String startDate;
		private String endDate;
	}

	@Data
	public static class Config {
		private List<String> defaultRegions;
		private int initialPickTokens;
		private List<Integer> unlockThresholds;
		private String tileRevealAt;
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
		/** OSRS wiki image name, without the _detail.png suffix. */
		private String icon;
		private Requirements requirements;

		public int column() {
			if (position == null || position.isEmpty()) return 0;
			return Character.toUpperCase(position.charAt(0)) - 'A';
		}

		public int row() {
			if (position == null || position.length() < 2) return 0;
			try {
				return Integer.parseInt(position.substring(1)) - 1;
			} catch (NumberFormatException e) {
				return 0;
			}
		}
	}

	@Data
	public static class Requirements {
		private String matchType;
		/** Kept loose: each requirement type carries its own fields. */
		private List<JsonObject> requirements;
		private List<JsonObject> tiers;

		public List<JsonObject> getRequirements() {
			return requirements != null ? requirements : Collections.emptyList();
		}
	}

	@Data
	public static class Team {
		private String id;
		private String name;
		private String color;
		private String icon;
		private int score;
		private List<Member> members;
		private List<UnlockedRegion> unlockedRegions;
		private int uniqueCompletedTiles;
		private PickTokens pickTokens;
		/** Keyed by boardTileId. */
		private Map<String, Completion> completions;
		/** Keyed by boardTileId; may be absent while tiles are hidden. */
		private Map<String, TileProgress> progress;

		public List<Member> getMembers() {
			return members != null ? members : Collections.emptyList();
		}

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

		public Completion completionFor(String boardTileId) {
			return completions != null && boardTileId != null ? completions.get(boardTileId) : null;
		}

		public TileProgress progressFor(String boardTileId) {
			return progress != null && boardTileId != null ? progress.get(boardTileId) : null;
		}

		public boolean hasMember(String playerName) {
			if (playerName == null) return false;
			String wanted = normalizeName(playerName);
			for (Member m : getMembers()) {
				if (m.getDisplayName() != null && wanted.equals(normalizeName(m.getDisplayName()))) return true;
			}
			return false;
		}

		private static String normalizeName(String s) {
			return s.replace('\u00A0', ' ').replace('_', ' ').trim().toLowerCase();
		}
	}

	@Data
	public static class Member {
		private int osrsAccountId;
		private String displayName;
		private String role;
	}

	@Data
	public static class UnlockedRegion {
		private String region;
		private String unlockType;
		private String unlockedAt;
		private String boardCompletedAt;
		private Integer bonusPoints;
	}

	@Data
	public static class PickTokens {
		private int earned;
		private int spent;
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

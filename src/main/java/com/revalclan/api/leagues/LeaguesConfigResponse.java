package com.revalclan.api.leagues;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeaguesConfigResponse extends ApiResponse {
	private LeaguesConfig data;

	@Data
	public static class LeaguesConfig {
		private int leagueVersion;
		private String leagueName;
		private List<Trophy> trophies;
		private List<RelicTier> relicTiers;
		private List<Integer> areaUnlockTasks;
		private List<TaskDifficulty> taskDifficulties;
	}

	@Data
	public static class Trophy {
		private String name;
		private int pointsRequired;
	}

	@Data
	public static class RelicTier {
		private int tier;
		private int pointsRequired;
		private List<String> relics;
	}

	@Data
	public static class TaskDifficulty {
		private String name;
		private int points;
	}
}

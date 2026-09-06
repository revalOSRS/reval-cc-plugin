package com.revalclan.api.leaguesbingo;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Response for POST /plugin/events/{id}/leagues-bingo/pick. */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaguesBingoPickResponse extends ApiResponse {
	private PickData data;

	@Data
	public static class PickData {
		private LeaguesBingoResponse.UnlockedRegion unlock;
		private LeaguesBingoMeResponse.Viewer me;
	}
}

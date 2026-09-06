package com.revalclan.api.leaguesbingo;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response for GET /plugin/events/{id}/leagues-bingo/me: what this account
 * may do in the event. The backend decides; the panel only mirrors it.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaguesBingoMeResponse extends ApiResponse {
	private Viewer data;

	@Data
	public static class Viewer {
		private boolean participating;
		private String teamId;
		private String teamName;
		private String role;
		private boolean superadmin;
		private boolean canPick;
		private String eventStatus;

		/** May this viewer spend a token for the given team? */
		public boolean canPickFor(String targetTeamId) {
			if (!canPick) return false;
			if (superadmin) return true;
			return teamId != null && teamId.equals(targetTeamId);
		}
	}
}

package com.revalclan.api.leaguesbingo;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response for GET /plugin/events/{id}/leagues-bingo/me: whether this account
 * may spend pick tokens, and for which team. The backend decides; the panel
 * only mirrors it.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaguesBingoMeResponse extends ApiResponse {
	private Viewer data;

	@Data
	public static class Viewer {
		private String teamId;
		private boolean superadmin;
		private boolean canPick;

		/** May this viewer spend a token for the given team? */
		public boolean canPickFor(String targetTeamId) {
			if (!canPick) return false;
			if (superadmin) return true;
			return teamId != null && teamId.equals(targetTeamId);
		}

		public boolean isOwnTeam(String targetTeamId) {
			return teamId != null && teamId.equals(targetTeamId);
		}
	}
}

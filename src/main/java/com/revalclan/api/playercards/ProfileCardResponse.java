package com.revalclan.api.playercards;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Response for GET /players/profile-card: minimal data for the profile card. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProfileCardResponse extends ApiResponse {
	private CardData data;

	@Data
	public static class CardData {
		private String nickname;
		private String clanRank;        // slug, e.g. "red_topaz"; may be null
		private Integer activityPoints;
		private Integer dropPoints;     // 1 point = 1M gp from tracked drops
		private Integer petCount;
		private Integer clogCount;
		private Integer diaryTasksDone;
		private Integer diaryTasksTotal;
		private String memberSince;     // ISO timestamp; may be null
	}
}

package com.revalclan.api.playercards;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** Response for GET /players/profile-card: minimal data for the profile card. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProfileCardResponse extends ApiResponse {
	private CardData data;

	@Data
	public static class Podiums {
		private Integer first;
		private Integer second;
		private Integer third;
	}

	@Data
	public static class CardData {
		private String nickname;
		private String clanRank;        // slug, e.g. "red_topaz"; may be null
		private Integer activityPoints;
		private String nextRank;        // slug; null at max rank
		private Integer pointsToNext;
		private Double rankProgress;    // 0..1 within the current rank band
		private Integer dropPoints;     // 1 point = 1M gp from tracked drops
		private Integer petCount;
		private Integer clogCount;
		private Integer diaryTasksDone;
		private Integer diaryTasksTotal;
		/** Completed events won by the player's team; one star each */
		private List<String> eventWins;
		private Podiums competitionPodiums;
		private String memberSince;     // ISO timestamp; may be null
	}
}

package com.revalclan.playercards;

import com.revalclan.api.playercards.ProfileCardResponse;
import com.revalclan.util.RankNames;
import lombok.Value;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Data shown on a clan player card, mapped from the profile-card endpoint. */
@Value
public class PlayerCardData {
	private static final DateTimeFormatter SINCE_FMT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

	String playerName;
	String rankName;       // display form, e.g. "Red Topaz"
	int points;
	String nextRankName;   // display form; null at max rank
	int pointsToNext;
	double rankProgress;   // 0..1 toward the next rank; negative hides the row
	int dropPoints;        // 1 point = 1M gp from tracked drops
	int petCount;
	int clogCount;
	int diaryTasksDone;
	int diaryTasksTotal;
	/** Names of events this player's team won, one star each */
	List<String> eventWins;
	String memberSince;    // "Jan 2022", or null

	static PlayerCardData from(ProfileCardResponse.CardData profile) {
		return new PlayerCardData(
			profile.getNickname(),
			RankNames.display(profile.getClanRank()),
			orZero(profile.getActivityPoints()),
			profile.getNextRank() != null ? RankNames.display(profile.getNextRank()) : null,
			orZero(profile.getPointsToNext()),
			profile.getRankProgress() != null ? profile.getRankProgress() : -1,
			orZero(profile.getDropPoints()),
			orZero(profile.getPetCount()),
			orZero(profile.getClogCount()),
			orZero(profile.getDiaryTasksDone()),
			orZero(profile.getDiaryTasksTotal()),
			profile.getEventWins() != null ? profile.getEventWins() : List.of(),
			formatMemberSince(profile.getMemberSince())
		);
	}

	private static int orZero(Integer value) {
		return value != null ? value : 0;
	}

	private static String formatMemberSince(String iso) {
		if (iso == null || iso.isEmpty()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(iso).format(SINCE_FMT);
		} catch (Exception e) {
			return null;
		}
	}
}

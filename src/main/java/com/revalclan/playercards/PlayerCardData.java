package com.revalclan.playercards;

import com.revalclan.api.playercards.ProfileCardResponse;
import com.revalclan.api.points.PointsResponse;
import com.revalclan.util.RankNames;
import lombok.Value;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Data shown on a clan player card, assembled from the profile-card endpoint. */
@Value
public class PlayerCardData {
	private static final DateTimeFormatter SINCE_FMT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

	String playerName;
	String rankName;       // display form, e.g. "Red Topaz"
	int points;
	String nextRankName;   // null at max rank or when rank data is unavailable
	int pointsToNext;
	double rankProgress;   // 0..1 toward the next rank; negative hides the row
	int dropPoints;        // 1 point = 1M gp from tracked drops
	int petCount;
	int clogCount;
	int diaryTasksDone;
	int diaryTasksTotal;
	String memberSince;    // "Jan 2022", or null
	/** Names of events this player has won, one star each; not served yet. */
	List<String> eventWins;

	static PlayerCardData from(ProfileCardResponse.CardData profile, List<PointsResponse.Rank> ranks) {
		int points = orZero(profile.getActivityPoints());

		String nextRank = null;
		int pointsToNext = 0;
		double progress = -1;
		if (ranks != null && !ranks.isEmpty()) {
			List<PointsResponse.Rank> sorted = new ArrayList<>(ranks);
			sorted.sort(Comparator.comparingInt(PointsResponse.Rank::getPointsRequired));
			int floor = 0;
			PointsResponse.Rank next = null;
			for (PointsResponse.Rank rank : sorted) {
				if (rank.getPointsRequired() > points) {
					next = rank;
					break;
				}
				floor = rank.getPointsRequired();
			}
			if (next != null) {
				nextRank = next.getDisplayName() != null ? next.getDisplayName() : next.getName();
				pointsToNext = next.getPointsRequired() - points;
				progress = next.getPointsRequired() > floor
					? (double) (points - floor) / (next.getPointsRequired() - floor) : 0;
			} else {
				progress = 1;
			}
		}

		return new PlayerCardData(
			profile.getNickname(),
			RankNames.display(profile.getClanRank()),
			points,
			nextRank,
			pointsToNext,
			progress,
			orZero(profile.getDropPoints()),
			orZero(profile.getPetCount()),
			orZero(profile.getClogCount()),
			orZero(profile.getDiaryTasksDone()),
			orZero(profile.getDiaryTasksTotal()),
			formatMemberSince(profile.getMemberSince()),
			List.of()
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

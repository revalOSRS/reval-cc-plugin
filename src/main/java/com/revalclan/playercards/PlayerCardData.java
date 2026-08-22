package com.revalclan.playercards;

import com.revalclan.util.PlayerNames;
import lombok.Value;

import java.util.Random;

/**
 * Data shown on a clan player card. Currently generated deterministically
 * from the player name so the preview is stable; replaced by an API-backed
 * profile fetch once the endpoint exists.
 */
@Value
public class PlayerCardData {
	private static final String[][] RANKS = {
		{"Mentor", "0"}, {"Prefect", "500"}, {"Leader", "1000"}, {"Supervisor", "2000"},
		{"Superior", "4000"}, {"Executive", "7500"}, {"Senator", "10000"}, {"Monarch", "15000"},
		{"Red Topaz", "17500"}, {"Sapphire", "20000"}, {"Emerald", "22500"}, {"Ruby", "25000"},
		{"Diamond", "27500"}, {"Dragonstone", "30000"}, {"Onyx", "35000"}, {"Zenyte", "40000"},
		{"Marshal", "50000"},
	};

	String playerName;
	String rankName;
	int points;
	String nextRankName;   // null at max rank
	int pointsToNext;
	double rankProgress;   // 0..1 toward the next rank
	int drops;
	int pets;
	int eventsPlayed;
	int diariesDone;
	String memberSince;

	public static PlayerCardData mock(String playerName) {
		Random rng = new Random(PlayerNames.normalize(playerName).hashCode());
		int points = 300 + rng.nextInt(48_000);

		int rankIdx = 0;
		for (int i = 0; i < RANKS.length; i++) {
			if (points >= Integer.parseInt(RANKS[i][1])) {
				rankIdx = i;
			}
		}
		String next = rankIdx + 1 < RANKS.length ? RANKS[rankIdx + 1][0] : null;
		int floor = Integer.parseInt(RANKS[rankIdx][1]);
		int ceil = next != null ? Integer.parseInt(RANKS[rankIdx + 1][1]) : points;
		double progress = next != null && ceil > floor
			? (double) (points - floor) / (ceil - floor) : 1.0;

		String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
		String since = months[rng.nextInt(12)] + " " + (2021 + rng.nextInt(5));

		return new PlayerCardData(
			playerName,
			RANKS[rankIdx][0],
			points,
			next,
			next != null ? ceil - points : 0,
			progress,
			5 + rng.nextInt(420),
			rng.nextInt(10),
			rng.nextInt(26),
			rng.nextInt(180),
			since
		);
	}
}

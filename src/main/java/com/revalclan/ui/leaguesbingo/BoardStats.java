package com.revalclan.ui.leaguesbingo;

import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Board;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Team;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Tile;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.TileProgress;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.UnlockedRegion;
import lombok.Getter;

/** One team's standing on one region board, derived from the public payload. */
@Getter
public final class BoardStats {
	/** A started-but-tiny progress still gets a visible sliver on tiles and bars. */
	public static final int MIN_VISIBLE_PERCENT = 8;

	private final int total;
	private final int completed;
	private final int earned;
	private final int bonus;
	private final boolean locked;
	private final boolean boardComplete;

	private BoardStats(int total, int completed, int earned, int bonus, boolean locked, boolean boardComplete) {
		this.total = total;
		this.completed = completed;
		this.earned = earned;
		this.bonus = bonus;
		this.locked = locked;
		this.boardComplete = boardComplete;
	}

	public static BoardStats of(Team team, Board board) {
		UnlockedRegion unlock = team.unlockFor(board.getRegion());
		int completed = 0;
		int earned = 0;
		for (Tile t : board.getTiles()) {
			if (team.completionFor(t.getBoardTileId()) != null) {
				completed++;
				earned += t.getPoints();
			}
		}
		return new BoardStats(
			board.getTiles().size(), completed, earned,
			unlock != null && unlock.getBonusPoints() != null ? unlock.getBonusPoints() : 0,
			unlock == null,
			unlock != null && unlock.getBoardCompletedAt() != null);
	}

	public int percent() {
		return total == 0 ? 0 : (int) Math.round(100d * completed / total);
	}

	public boolean allDone() {
		return total > 0 && completed == total;
	}

	/** "5/9 tiles  |  12 + 19/19 pts", or the board's shape while tiles are hidden. */
	public String line(Board board) {
		if (board.getTiles().isEmpty()) {
			return board.getRows() + "x" + board.getColumns() + " board  |  tiles hidden";
		}
		return completed + "/" + total + " tiles  |  " + earned + (bonus > 0 ? " + " + bonus : "") + "/" + board.getTotalPoints() + " pts";
	}

	/** 100 when done, MIN_VISIBLE_PERCENT..99 while in progress, 0 when untouched. */
	public static int tilePercent(Team team, Tile tile) {
		if (team.completionFor(tile.getBoardTileId()) != null) return 100;
		TileProgress p = team.progressFor(tile.getBoardTileId());
		if (p == null) return 0;
		double value = p.getValue() != null ? p.getValue() : 0;
		int total = p.getTotalRequirements() != null ? p.getTotalRequirements() : 0;
		int pct;
		if (total > 1) {
			pct = (int) Math.round(100d * p.completedRequirementCount() / total);
		} else if (p.getTarget() != null && p.getTarget() > 0) {
			pct = (int) Math.round(100d * value / p.getTarget());
		} else {
			pct = 0;
		}
		if (pct <= 0 && value > 0) pct = MIN_VISIBLE_PERCENT;
		return Math.max(0, Math.min(99, pct));
	}
}

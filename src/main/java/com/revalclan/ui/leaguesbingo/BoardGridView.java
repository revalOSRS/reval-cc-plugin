package com.revalclan.ui.leaguesbingo;

import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Board;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Payload;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Team;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Tile;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;
import net.runelite.client.ui.FontManager;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The lettered tile grid of one board for one team; a finished board gets an accent frame. */
final class BoardGridView extends JPanel {
	private static final int GAP = 4;
	private static final int GUTTER = 16;
	private static final int PANEL_WIDTH = 198;

	private final List<TileCell> cells = new ArrayList<>();

	BoardGridView(Payload payload, Team team, Board board, LeaguesRegions.Region meta, BoardStats stats,
				  TileIcons icons, String selectedTileId, Consumer<Tile> onSelect) {
		super(new FlowLayout(FlowLayout.CENTER, 0, 0));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		int cols = Math.max(1, board.getColumns());
		int rows = Math.max(1, board.getRows());
		int tileSize = Math.max(14, Math.min(56, (PANEL_WIDTH - GUTTER - (cols - 1) * GAP) / cols));

		JPanel grid = new JPanel(new GridBagLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (!stats.isBoardComplete()) return;
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(Colors.withAlpha(meta.getAccent(), 110));
				g2.setStroke(new BasicStroke(1.5f));
				g2.draw(new RoundRectangle2D.Float(GUTTER - 2, GUTTER - 2, getWidth() - GUTTER + 1, getHeight() - GUTTER + 1, 10, 10));
				g2.dispose();
			}
		};
		grid.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(GAP / 2, GAP / 2, GAP / 2, GAP / 2);

		Font small = FontManager.getRunescapeSmallFont();
		for (int col = 0; col < cols; col++) {
			c.gridx = col + 1;
			c.gridy = 0;
			grid.add(gutterLabel(String.valueOf((char) ('A' + col)), small, tileSize, GUTTER - GAP), c);
		}

		Color accent = team != null ? LeaguesBingoPanel.teamColor(team) : meta.getAccent();
		for (int r = 0; r < rows; r++) {
			c.gridx = 0;
			c.gridy = r + 1;
			grid.add(gutterLabel(String.valueOf(r + 1), small, GUTTER - GAP, tileSize), c);

			for (int col = 0; col < cols; col++) {
				c.gridx = col + 1;
				String position = String.valueOf((char) ('A' + col)) + (r + 1);
				Tile tile = payload.isTilesHidden() ? null : board.tileAt(position);

				TileCell cell;
				if (payload.isTilesHidden()) {
					cell = new TileCell(null, TileCell.State.HIDDEN, 0, meta.getAccent(), tileSize);
				} else if (tile == null) {
					cell = new TileCell(null, TileCell.State.FILLER, 0, meta.getAccent(), tileSize);
				} else {
					int percent = BoardStats.tilePercent(team, tile);
					TileCell.State state = percent >= 100 ? TileCell.State.COMPLETED
						: percent > 0 ? TileCell.State.IN_PROGRESS : TileCell.State.NOT_STARTED;
					cell = new TileCell(tile, state, percent, accent, tileSize);
					cell.setDimmed(stats.isLocked());
					cell.setSelected(tile.getBoardTileId().equals(selectedTileId));
					cells.add(cell);
					Clickable.onPress(cell, () -> onSelect.accept(tile), cell::setHovered);
					icons.load(tile, cell);
				}
				grid.add(cell, c);
			}
		}
		add(grid);
	}

	void setSelected(String tileId) {
		for (TileCell c : cells) {
			c.setSelected(c.getTile().getBoardTileId().equals(tileId));
		}
	}

	private static JLabel gutterLabel(String text, Font font, int width, int height) {
		JLabel l = Labels.label(text, font, UIConstants.TEXT_MUTED);
		l.setHorizontalAlignment(JLabel.CENTER);
		l.setPreferredSize(new Dimension(width, height));
		return l;
	}
}

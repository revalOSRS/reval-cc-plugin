package com.revalclan.ui.leaguesbingo;

import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Board;
import com.revalclan.ui.components.AccentCard;
import com.revalclan.ui.components.Badge;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.SpriteIcons;
import net.runelite.client.game.SpriteManager;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * One region board for one team: banner, name, LOCKED / x2 badges, percent,
 * the tiles-and-points line, and a bottom slot (progress bar or an action).
 * Used both as the clickable row in the board list and as the board header.
 */
final class BoardSummaryCard {
	private static final int BANNER_SIZE = 32;

	private BoardSummaryCard() {
	}

	static AccentCard build(LeaguesRegions.Region meta, BoardStats stats, Board board, SpriteManager spriteManager,
							Color nameColor, boolean hoverable, JComponent bottom) {
		AccentCard card = new AccentCard(meta.getAccent(), hoverable);
		card.setDim(stats.isLocked());

		JPanel top = Labels.row();
		JPanel left = Labels.flow();
		left.add(Labels.bold(meta.getDisplayName(), nameColor));
		if (stats.isLocked()) left.add(new Badge("LOCKED", UIConstants.TEXT_MUTED));
		if (stats.isBoardComplete()) left.add(new Badge("x2", UIConstants.ACCENT_GOLD));
		top.add(left, BorderLayout.WEST);
		if (!stats.isLocked()) {
			top.add(Labels.small(stats.percent() + "%", stats.allDone() ? TileCell.COMPLETED : UIConstants.TEXT_SECONDARY), BorderLayout.EAST);
		}

		JPanel lines = new JPanel();
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		lines.setOpaque(false);
		lines.add(top);
		lines.add(Box.createVerticalStrut(4));
		lines.add(Labels.small(stats.line(board), UIConstants.TEXT_SECONDARY));
		lines.add(Box.createVerticalStrut(6));
		lines.add(bottom);

		card.add(withBanner(meta, stats, spriteManager, lines));
		return card;
	}

	/**
	 * Region banner (game-cache shield) beside the content. It arrives
	 * asynchronously; the slot is reserved so the row does not jump.
	 */
	private static JComponent withBanner(LeaguesRegions.Region meta, BoardStats stats, SpriteManager spriteManager, JComponent content) {
		JPanel wrap = new JPanel(new BorderLayout(10, 0));
		wrap.setOpaque(false);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel banner = new JLabel();
		banner.setPreferredSize(new Dimension(BANNER_SIZE, BANNER_SIZE));
		banner.setHorizontalAlignment(JLabel.CENTER);
		banner.setVerticalAlignment(JLabel.CENTER);
		int spriteId = stats.isBoardComplete() && meta.getBannerHighlightSprite() >= 0 ? meta.getBannerHighlightSprite() : meta.getBannerSprite();
		if (spriteManager != null && spriteId >= 0) {
			boolean dim = stats.isLocked();
			SpriteIcons.load(spriteManager, spriteId, BANNER_SIZE, icon -> banner.setIcon(dim ? dimmed(icon) : icon));
		}

		wrap.add(banner, BorderLayout.WEST);
		wrap.add(content, BorderLayout.CENTER);
		return wrap;
	}

	private static ImageIcon dimmed(ImageIcon icon) {
		BufferedImage out = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = out.createGraphics();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
		icon.paintIcon(null, g2, 0, 0);
		g2.dispose();
		return new ImageIcon(out);
	}
}

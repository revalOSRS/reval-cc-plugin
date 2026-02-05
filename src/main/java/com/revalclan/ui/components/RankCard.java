package com.revalclan.ui.components;

import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

public class RankCard extends JPanel {
	private static final Map<String, Integer> RANK_SPRITES = Map.ofEntries(
		Map.entry("member", 3137),
		Map.entry("advanced", 3138),
		Map.entry("elite", 3139),
		Map.entry("veteran", 3140),
		Map.entry("hero", 3141),
		Map.entry("champion", 3142),
		Map.entry("legend", 3143),
		Map.entry("mythic", 3144),
		Map.entry("paragon", 3109),
		Map.entry("ascended", 3110),
		Map.entry("eternal", 3111)
	);

	private final JLabel iconLabel = new JLabel();

	public RankCard(PointsResponse.Rank rank, SpriteManager spriteManager) {
		setLayout(new BorderLayout(6, 0));
		setBackground(UIConstants.CARD_BG);
		setBorder(new EmptyBorder(8, 10, 8, 10));

		String displayName = rank.getDisplayName() != null ? rank.getDisplayName() : rank.getName();

		iconLabel.setPreferredSize(new Dimension(18, 18));

		JLabel nameLabel = new JLabel(displayName);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(UIConstants.ACCENT_GOLD);

		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		leftPanel.setOpaque(false);
		leftPanel.add(iconLabel);
		leftPanel.add(nameLabel);

		String pointsText = formatPoints(rank.getPointsRequired());
		if (rank.getMaintenancePerMonth() > 0) {
			pointsText += " (" + rank.getMaintenancePerMonth() + "/mo)";
		}

		JLabel pointsLabel = new JLabel(pointsText);
		pointsLabel.setFont(FontManager.getRunescapeBoldFont());
		pointsLabel.setForeground(UIConstants.POINTS_COLOR);

		add(leftPanel, BorderLayout.WEST);
		add(pointsLabel, BorderLayout.EAST);

		if (rank.getAdditionalRequirements() != null && !rank.getAdditionalRequirements().isEmpty()) {
			StringBuilder tooltip = new StringBuilder();
			for (PointsResponse.Rank.AdditionalRequirement req : rank.getAdditionalRequirements()) {
				if (tooltip.length() > 0) tooltip.append(", ");
				tooltip.append(req.getDescription());
			}
			setToolTipText(tooltip.toString());
		}

		if (spriteManager != null && rank.getName() != null) {
			Integer spriteId = RANK_SPRITES.get(rank.getName());
			if (spriteId != null) {
				loadIcon(spriteId, spriteManager);
			}
		}
	}

	private void loadIcon(int spriteId, SpriteManager spriteManager) {
		spriteManager.getSpriteAsync(spriteId, 0, sprite -> {
			if (sprite != null) {
				SwingUtilities.invokeLater(() -> {
					BufferedImage scaled = ImageUtil.resizeImage(sprite, 18, 18);
					iconLabel.setIcon(new ImageIcon(scaled));
				});
			}
		});
	}

	private String formatPoints(int points) {
		if (points >= 1000) {
			return String.format("%.1fk", points / 1000.0);
		}
		return String.valueOf(points);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(getBackground());
		g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
		g2d.dispose();
	}
}

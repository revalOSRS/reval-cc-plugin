package com.revalclan.ui.components;

import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanRankIconResolver;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

public class RankCard extends JPanel {
	private final JLabel iconLabel = new JLabel();

	public RankCard(PointsResponse.Rank rank, SpriteManager spriteManager, ClanRankIconResolver iconResolver) {
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

		if (spriteManager != null && iconResolver != null && rank.getName() != null) {
			iconResolver.resolve(rank.getName(), spriteId -> loadIcon(spriteId, spriteManager));
		}
	}

	private void loadIcon(int spriteId, SpriteManager spriteManager) {
		spriteManager.getSpriteAsync(spriteId, 0, sprite -> {
			if (sprite != null) {
				SwingUtilities.invokeLater(() -> {
					// Scale preserving aspect ratio, then pad onto an 18x18 canvas —
					// forcing sprites square stretches non-square icons (e.g. gems)
					BufferedImage img = sprite;
					if (img.getWidth() > 18 || img.getHeight() > 18) {
						double scale = Math.min(18.0 / img.getWidth(), 18.0 / img.getHeight());
						img = ImageUtil.resizeImage(img,
							Math.max(1, (int) Math.round(img.getWidth() * scale)),
							Math.max(1, (int) Math.round(img.getHeight() * scale)));
					}
					iconLabel.setIcon(new ImageIcon(ImageUtil.resizeCanvas(img, 18, 18)));
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

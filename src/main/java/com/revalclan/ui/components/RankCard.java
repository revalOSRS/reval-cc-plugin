package com.revalclan.ui.components;

import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanRankIconResolver;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class RankCard extends JPanel {
	private final JLabel iconLabel = new JLabel();

	public RankCard(PointsResponse.Rank rank, ClanRankIconResolver iconResolver) {
		setLayout(new BorderLayout(6, 0));
		setBackground(UIConstants.CARD_BG);
		setBorder(new EmptyBorder(8, 6, 8, 10));

		String displayName = rank.getDisplayName() != null ? rank.getDisplayName() : rank.getName();

		iconLabel.setPreferredSize(new Dimension(18, 18));

		JLabel nameLabel = Labels.bold(displayName, UIConstants.ACCENT_GOLD);

		JPanel leftPanel = Labels.flow();
		leftPanel.add(iconLabel);
		leftPanel.add(nameLabel);

		String pointsText = formatPoints(rank.getPointsRequired());
		if (rank.getMaintenancePerMonth() > 0) {
			pointsText += " (" + rank.getMaintenancePerMonth() + "/mo)";
		}

		JLabel pointsLabel = Labels.bold(pointsText, UIConstants.ACCENT_GREEN);

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

		if (iconResolver != null && rank.getName() != null) {
			iconResolver.apply(rank.getName(), iconLabel, 18);
		}
	}

	private String formatPoints(int points) {
		if (points >= 1000) {
			return String.format("%.1fk", points / 1000.0);
		}
		return String.valueOf(points);
	}

	@Override
	protected void paintComponent(Graphics g) {
		RoundedPanel.fill(g, this, getBackground(), 6);
	}
}

package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;

public class ChecklistItem extends JPanel {
	public ChecklistItem(String description, boolean completed, Integer points, UIAssetLoader assetLoader) {
		setLayout(new BorderLayout(8, 0));
		setOpaque(false);

		ImageIcon checkIcon = assetLoader != null ? assetLoader.getIcon("checkmark", 10) : null;

		JPanel statusBox = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int size = Math.min(getWidth(), getHeight());
				int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
				g2d.setColor(completed ? new Color(70, 75, 70) : UIConstants.TEXT_MUTED);
				g2d.fillRect(x, y, size, size);
				if (completed && checkIcon != null) {
					checkIcon.paintIcon(this, g2d,
						x + (size - checkIcon.getIconWidth()) / 2,
						y + (size - checkIcon.getIconHeight()) / 2);
				}
				g2d.dispose();
			}
		};
		statusBox.setPreferredSize(new Dimension(14, 14));
		statusBox.setOpaque(false);

		JLabel descLabel = new JLabel(description);
		descLabel.setFont(FontManager.getRunescapeSmallFont());
		descLabel.setForeground(completed ? UIConstants.TEXT_PRIMARY : UIConstants.TEXT_SECONDARY);
		descLabel.setToolTipText(description);

		JPanel leftPanel = new JPanel(new BorderLayout(8, 0));
		leftPanel.setOpaque(false);
		leftPanel.add(statusBox, BorderLayout.WEST);
		leftPanel.add(descLabel, BorderLayout.CENTER);
		add(leftPanel, BorderLayout.CENTER);

		if (points != null && points > 0) {
			JLabel pointsLabel = new JLabel("+" + points + " pts");
			pointsLabel.setFont(FontManager.getRunescapeSmallFont());
			pointsLabel.setForeground(UIConstants.ACCENT_GOLD);
			pointsLabel.setVerticalAlignment(SwingConstants.TOP);
			add(pointsLabel, BorderLayout.EAST);
		}
	}
}

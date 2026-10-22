package com.revalclan.ui.components;

import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class PointSourceCard extends JPanel {
	private final JLabel iconLabel = new JLabel();

	public PointSourceCard(PointsResponse.PointSource source, ItemManager itemManager) {
		Integer itemId = source.getMetadata() != null ? source.getMetadata().getItemId() : null;
		init(source.getName(), source.getDescription(), source.getPointsDisplay(), itemId, itemManager);
	}

	public PointSourceCard(String name, String description, String points, ItemManager itemManager) {
		init(name, description, points, null, itemManager);
	}

	public static PointSourceCard forUntradeable(PointsResponse.PointSource source, ItemManager itemManager) {
		Integer itemId = source.getMetadata() != null ? source.getMetadata().getItemId() : null;
		String subtitle = source.getMetadata() != null ? source.getMetadata().getSource() : null;
		PointSourceCard card = new PointSourceCard();
		card.init(source.getName(), subtitle, source.getPointsDisplay(), itemId, itemManager);
		return card;
	}

	private PointSourceCard() {}

	private void init(String name, String description, String points, Integer itemId, ItemManager itemManager) {
		setLayout(new BorderLayout(4, 0));
		setBackground(UIConstants.ROW_BG);
		setBorder(new EmptyBorder(5, 8, 5, 8));
		setToolTipText(name);

		boolean hasIcon = itemId != null && itemManager != null;

		JPanel leftPanel = new JPanel(new BorderLayout(hasIcon ? 6 : 0, 0));
		leftPanel.setOpaque(false);

		if (hasIcon) {
			iconLabel.setPreferredSize(new Dimension(24, 24));
			leftPanel.add(iconLabel, BorderLayout.WEST);
			loadIcon(itemId, itemManager);
		}

		JPanel textPanel = Labels.column();
		textPanel.add(Labels.bold(name, UIConstants.TEXT_PRIMARY));

		if (description != null && !description.isEmpty()) {
			JTextArea descArea = new JTextArea(description);
			descArea.setFont(FontManager.getRunescapeSmallFont());
			descArea.setForeground(UIConstants.TEXT_SECONDARY);
			descArea.setEditable(false);
			descArea.setFocusable(false);
			descArea.setLineWrap(true);
			descArea.setWrapStyleWord(true);
			descArea.setOpaque(false);
			descArea.setBorder(null);
			descArea.setAlignmentX(Component.LEFT_ALIGNMENT);
			textPanel.add(descArea);
		}

		leftPanel.add(textPanel, BorderLayout.CENTER);

		JLabel pointsLabel = Labels.small(points, UIConstants.ACCENT_GREEN);
		pointsLabel.setVerticalAlignment(SwingConstants.TOP);

		add(leftPanel, BorderLayout.CENTER);
		add(pointsLabel, BorderLayout.EAST);
	}

	private void loadIcon(int itemId, ItemManager itemManager) {
		try {
			AsyncBufferedImage img = itemManager.getImage(itemId);
			img.onLoaded(() -> SwingUtilities.invokeLater(() -> 
				iconLabel.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 24, 24)))
			));
		} catch (Exception ignored) {}
	}

	@Override
	protected void paintComponent(Graphics g) {
		RoundedPanel.fill(g, this, getBackground(), 4);
	}
}

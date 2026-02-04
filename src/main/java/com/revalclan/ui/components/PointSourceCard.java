package com.revalclan.ui.components;

import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * A card displaying a single point source
 */
public class PointSourceCard extends JPanel {
	private final JLabel iconLabel = new JLabel();
	private Color pointsColor = UIConstants.ACCENT_GREEN;

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

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setOpaque(false);

		JLabel nameLabel = new JLabel(name);
		nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
		nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		textPanel.add(nameLabel);

		if (description != null && !description.isEmpty()) {
			JTextArea descArea = new JTextArea(description);
			descArea.setFont(new Font("Segoe UI", Font.PLAIN, 9));
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

		JLabel pointsLabel = new JLabel(points);
		pointsLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
		pointsLabel.setForeground(pointsColor);
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
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(getBackground());
		g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
		g2d.dispose();
	}
}

package com.revalclan.ui;

import com.revalclan.api.account.AccountResponse;
import com.revalclan.ui.constants.UIConstants;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class PointsBreakdownPanel extends JDialog {

	private final ItemManager itemManager;

	public PointsBreakdownPanel(String title, List<AccountResponse.PointsLogEntry> entries, ItemManager itemManager) {
		super((Frame) null, title + " Points Breakdown", true);
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setSize(500, 450);
		setLocationRelativeTo(null);

		// Header
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.BACKGROUND);
		header.setBorder(new EmptyBorder(12, 16, 12, 16));

		JLabel titleLabel = new JLabel(title + " Points Breakdown");
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(UIConstants.ACCENT_GOLD);

		JButton closeButton = new JButton("Close");
		closeButton.setFont(FontManager.getRunescapeSmallFont());
		closeButton.setForeground(UIConstants.TEXT_PRIMARY);
		closeButton.setBackground(UIConstants.CARD_BG);
		closeButton.setBorder(new EmptyBorder(6, 12, 6, 12));
		closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		closeButton.addActionListener(e -> dispose());

		header.add(titleLabel, BorderLayout.WEST);
		header.add(closeButton, BorderLayout.EAST);

		// Content
		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(UIConstants.BACKGROUND);
		contentPanel.setBorder(new EmptyBorder(0, 16, 16, 16));

		if (entries.isEmpty()) {
			JLabel emptyLabel = new JLabel("No points entries found for this category.");
			emptyLabel.setFont(FontManager.getRunescapeSmallFont());
			emptyLabel.setForeground(UIConstants.TEXT_SECONDARY);
			emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			emptyLabel.setBorder(new EmptyBorder(40, 0, 0, 0));
			contentPanel.add(emptyLabel);
		} else {
			int totalPoints = entries.stream()
				.filter(e -> e.getPointsChange() != null && e.getPointsChange() > 0)
				.mapToInt(AccountResponse.PointsLogEntry::getPointsChange)
				.sum();

			contentPanel.add(createSummaryCard(totalPoints));
			contentPanel.add(Box.createRigidArea(new Dimension(0, 8)));

			for (AccountResponse.PointsLogEntry entry : entries) {
				if (entry.getPointsChange() != null && entry.getPointsChange() > 0) {
					contentPanel.add(createEntryCard(entry));
					contentPanel.add(Box.createRigidArea(new Dimension(0, 2)));
				}
			}
		}

		JScrollPane scrollPane = new JScrollPane(contentPanel);
		scrollPane.setBackground(UIConstants.BACKGROUND);
		scrollPane.setBorder(null);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		add(header, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	private JPanel createSummaryCard(int totalPoints) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(UIConstants.CARD_BG);
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JLabel label = new JLabel("Total Points");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(UIConstants.TEXT_SECONDARY);

		JLabel value = new JLabel(String.valueOf(totalPoints));
		value.setFont(FontManager.getRunescapeBoldFont());
		value.setForeground(UIConstants.ACCENT_GOLD);

		card.add(label, BorderLayout.WEST);
		card.add(value, BorderLayout.EAST);

		return card;
	}

	private JPanel createEntryCard(AccountResponse.PointsLogEntry entry) {
		Integer itemId = entry.getItemId();
		boolean hasIcon = itemId != null && itemManager != null;

		JPanel card = new JPanel(new BorderLayout(6, 0)) {
			@Override
			public Dimension getMaximumSize() {
				Dimension pref = getPreferredSize();
				return new Dimension(Integer.MAX_VALUE, pref.height);
			}
		};
		card.setBackground(UIConstants.CARD_BG);
		card.setBorder(new EmptyBorder(4, 8, 4, 8));

		JPanel leftPanel = new JPanel(new BorderLayout(hasIcon ? 6 : 0, 0));
		leftPanel.setOpaque(false);

		if (hasIcon) {
			JLabel iconLabel = new JLabel();
			iconLabel.setPreferredSize(new Dimension(24, 24));
			leftPanel.add(iconLabel, BorderLayout.WEST);
			loadIcon(itemId, iconLabel);
		}

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setOpaque(false);

		String desc = entry.getSourceDescription() != null ? entry.getSourceDescription() : "Unknown source";

		JLabel descLabel = new JLabel("<html>" + desc + "</html>");
		descLabel.setFont(FontManager.getRunescapeSmallFont());
		descLabel.setForeground(UIConstants.TEXT_PRIMARY);
		descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel dateLabel = new JLabel(formatDate(entry.getCreatedAt()));
		dateLabel.setFont(FontManager.getRunescapeSmallFont());
		dateLabel.setForeground(UIConstants.TEXT_MUTED);
		dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		textPanel.add(descLabel);
		textPanel.add(dateLabel);

		leftPanel.add(textPanel, BorderLayout.CENTER);

		JLabel pointsLabel = new JLabel("+" + entry.getPointsChange());
		pointsLabel.setFont(FontManager.getRunescapeBoldFont());
		pointsLabel.setForeground(UIConstants.ACCENT_GREEN);

		card.add(leftPanel, BorderLayout.CENTER);
		card.add(pointsLabel, BorderLayout.EAST);

		return card;
	}

	private void loadIcon(int itemId, JLabel iconLabel) {
		try {
			AsyncBufferedImage img = itemManager.getImage(itemId);
			img.onLoaded(() -> SwingUtilities.invokeLater(() ->
				iconLabel.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 24, 24)))
			));
		} catch (Exception ignored) {}
	}

	private String formatDate(String dateStr) {
		if (dateStr == null || dateStr.isEmpty()) return "Unknown date";
		try {
			ZonedDateTime local;
			try {
				local = ZonedDateTime.parse(dateStr)
					.withZoneSameInstant(ZoneId.systemDefault());
			} catch (DateTimeParseException e) {
				local = LocalDateTime.parse(dateStr)
					.atZone(ZoneOffset.UTC)
					.withZoneSameInstant(ZoneId.systemDefault());
			}
			return local.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
		} catch (Exception e) {
			return dateStr;
		}
	}
}

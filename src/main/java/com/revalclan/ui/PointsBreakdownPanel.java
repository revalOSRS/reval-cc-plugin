package com.revalclan.ui;

import com.revalclan.api.account.AccountResponse;
import com.revalclan.ui.constants.UIConstants;

import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

public class PointsBreakdownPanel extends JDialog {

	public PointsBreakdownPanel(String title, List<AccountResponse.PointsLogEntry> entries) {
		super((Frame) null, title + " Points Breakdown", true);

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setSize(420, 450);
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
		JPanel card = new JPanel(new BorderLayout(6, 0));
		card.setBackground(UIConstants.CARD_BG);
		card.setBorder(new EmptyBorder(4, 8, 4, 8));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		card.setPreferredSize(new Dimension(0, 38));

		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setOpaque(false);

		String desc = entry.getSourceDescription() != null ? entry.getSourceDescription() : "Unknown source";
		if (desc.length() > 55) desc = desc.substring(0, 52) + "...";

		JLabel descLabel = new JLabel(desc);
		descLabel.setFont(FontManager.getRunescapeSmallFont());
		descLabel.setForeground(UIConstants.TEXT_PRIMARY);
		descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel dateLabel = new JLabel(formatDate(entry.getCreatedAt()));
		dateLabel.setFont(FontManager.getRunescapeSmallFont());
		dateLabel.setForeground(UIConstants.TEXT_MUTED);
		dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		leftPanel.add(descLabel);
		leftPanel.add(dateLabel);

		JLabel pointsLabel = new JLabel("+" + entry.getPointsChange());
		pointsLabel.setFont(FontManager.getRunescapeBoldFont());
		pointsLabel.setForeground(UIConstants.ACCENT_GREEN);

		card.add(leftPanel, BorderLayout.CENTER);
		card.add(pointsLabel, BorderLayout.EAST);

		return card;
	}

	private String formatDate(String dateStr) {
		if (dateStr == null || dateStr.isEmpty()) return "Unknown date";
		try {
			SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			SimpleDateFormat out = new SimpleDateFormat("MMM dd, yyyy");
			return out.format(in.parse(dateStr));
		} catch (Exception e) {
			return dateStr;
		}
	}
}

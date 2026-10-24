package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CollapsibleSection extends JPanel {
	private final JPanel contentPanel;
	private final JLabel arrowLabel;
	private final JLabel iconLabel;
	private boolean expanded;

	public CollapsibleSection(String title, String subtitle, JPanel content, boolean startExpanded) {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(0, 0, 3, 0));

		this.expanded = startExpanded;
		this.contentPanel = content;
		contentPanel.setVisible(expanded);

		JPanel headerPanel = new RoundedPanel(new BorderLayout(2, 0), 6);
		headerPanel.setBackground(UIConstants.CARD_BG);
		headerPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

		JPanel leftPanel = Labels.flow();

		arrowLabel = new JLabel(new TriangleIcon(expanded, 9, UIConstants.ACCENT_GOLD));
		leftPanel.add(arrowLabel);

		iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(24, 24));
		leftPanel.add(iconLabel);

		JPanel titlePanel = new JPanel();
		titlePanel.setOpaque(false);

		JLabel titleLabel = Labels.sectionTitle(title);

		if (subtitle != null && !subtitle.isEmpty()) {
			titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
			titlePanel.add(titleLabel);
			titlePanel.add(Labels.small(subtitle, UIConstants.TEXT_SECONDARY));
		} else {
			titlePanel.setLayout(new BorderLayout());
			titlePanel.add(titleLabel, BorderLayout.CENTER);
		}

		headerPanel.add(leftPanel, BorderLayout.WEST);
		headerPanel.add(titlePanel, BorderLayout.CENTER);

		// Also sets the hand cursor.
		Clickable.onPress(headerPanel, () -> setExpanded(!expanded), UIConstants.CARD_HOVER, UIConstants.CARD_BG);

		add(headerPanel, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);
	}

	public void setIcon(Icon icon) {
		iconLabel.setIcon(icon);
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
		arrowLabel.setIcon(new TriangleIcon(expanded, 9, UIConstants.ACCENT_GOLD));
		contentPanel.setVisible(expanded);
		revalidate();
	}
}

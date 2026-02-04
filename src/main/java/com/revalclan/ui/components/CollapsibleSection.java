package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A collapsible/expandable section with a header and content area
 */
public class CollapsibleSection extends JPanel {
	private final JPanel contentPanel;
	private final JLabel arrowLabel;
	private final JLabel iconLabel;
	private final JPanel headerPanel;
	private boolean expanded;

	public CollapsibleSection(String title, String subtitle, JPanel content, boolean startExpanded) {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(0, 0, 3, 0));

		this.expanded = startExpanded;
		this.contentPanel = content;
		contentPanel.setVisible(expanded);

		// Header
		headerPanel = new JPanel(new BorderLayout(2, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(getBackground());
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2d.dispose();
			}
		};
		headerPanel.setBackground(UIConstants.HEADER_BG);
		headerPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
		headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		headerPanel.setOpaque(false);

		// Left: arrow + icon
		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		leftPanel.setOpaque(false);

		arrowLabel = new JLabel(expanded ? "▼" : "▲");
		arrowLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
		arrowLabel.setForeground(UIConstants.ACCENT_GOLD);
		leftPanel.add(arrowLabel);

		iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(24, 24));
		leftPanel.add(iconLabel);

		// Title (+ optional subtitle)
		JPanel titlePanel = new JPanel();
		titlePanel.setOpaque(false);

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
		titleLabel.setForeground(UIConstants.TEXT_PRIMARY);

		if (subtitle != null && !subtitle.isEmpty()) {
			titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
			titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			titlePanel.add(titleLabel);

			JLabel subtitleLabel = new JLabel(subtitle);
			subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
			subtitleLabel.setForeground(UIConstants.TEXT_SECONDARY);
			subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			titlePanel.add(subtitleLabel);
		} else {
			titlePanel.setLayout(new BorderLayout());
			titlePanel.add(titleLabel, BorderLayout.CENTER);
		}

		headerPanel.add(leftPanel, BorderLayout.WEST);
		headerPanel.add(titlePanel, BorderLayout.CENTER);

		// Click to toggle
		headerPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					setExpanded(!expanded);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				headerPanel.setBackground(UIConstants.HEADER_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				headerPanel.setBackground(UIConstants.HEADER_BG);
			}
		});

		add(headerPanel, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);
	}

	public void setIcon(Icon icon) {
		iconLabel.setIcon(icon);
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
		arrowLabel.setText(expanded ? "▼" : "▲");
		contentPanel.setVisible(expanded);
		revalidate();
	}

	public boolean isExpanded() {
		return expanded;
	}
}

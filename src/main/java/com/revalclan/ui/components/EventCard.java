package com.revalclan.ui.components;

import com.revalclan.api.events.EventsResponse;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.BiConsumer;

public class EventCard extends JPanel {
	private final EventsResponse.EventSummary event;
	private final boolean isActive;
	private final String registrationStatus;
	private boolean isHovered = false;

	public EventCard(EventsResponse.EventSummary event, boolean isActive, String currentPlayerName,
					 BiConsumer<String, Boolean> onRegisterAction) {
		this.event = event;
		this.isActive = isActive;
		this.registrationStatus = findRegistrationStatus(currentPlayerName);

		setLayout(new BorderLayout());
		setOpaque(false);
		setBorder(new EmptyBorder(0, 0, 12, 0));

		buildCard(onRegisterAction);

		addMouseListener(new MouseAdapter() {
			public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
			public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
		});
	}

	private String findRegistrationStatus(String playerName) {
		if (playerName == null || event.getRegistrations() == null) return null;
		return event.getRegistrations().stream()
			.filter(r -> playerName.equalsIgnoreCase(r.getOsrsNickname()))
			.findFirst()
			.map(EventsResponse.EventRegistration::getStatus)
			.orElse(null);
	}

	private Color getAccentColor() {
		return isActive ? UIConstants.ACCENT_GREEN : UIConstants.ACCENT_BLUE;
	}

	private void buildCard(BiConsumer<String, Boolean> onRegisterAction) {
		JPanel card = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				g2d.setColor(isHovered ? UIConstants.CARD_HOVER : UIConstants.CARD_BG);
				g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));

				g2d.setColor(UIConstants.BORDER_COLOR);
				g2d.setStroke(new BasicStroke(1));
				g2d.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12));

				g2d.setColor(getAccentColor());
				g2d.fillRoundRect(0, 8, 4, getHeight() - 16, 4, 4);

				g2d.dispose();
			}
		};
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(12, 16, 12, 12));

		card.add(buildHeader());
		card.add(buildContent());

		if (!isActive) {
			card.add(buildFooter(onRegisterAction));
		}

		add(card, BorderLayout.CENTER);
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setOpaque(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);

		JLabel typeBadge = createBadge(event.getEventTypeDisplay(), getAccentColor());
		typeBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel(event.getName());
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(UIConstants.TEXT_PRIMARY);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		left.add(typeBadge);
		left.add(Box.createRigidArea(new Dimension(0, 4)));
		left.add(title);

		String statusText = isActive ? "LIVE" : event.getTimeUntilStart();
		JLabel statusBadge = createBadge(statusText, getAccentColor());

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		right.setOpaque(false);
		right.add(statusBadge);

		header.add(left, BorderLayout.WEST);
		header.add(right, BorderLayout.EAST);

		return header;
	}

	private JPanel buildContent() {
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.setBorder(new EmptyBorder(8, 0, 0, 0));

		if (event.getDescription() != null && !event.getDescription().isEmpty()) {
			JLabel desc = new JLabel(event.getDescription());
			desc.setFont(FontManager.getRunescapeSmallFont());
			desc.setForeground(UIConstants.TEXT_SECONDARY);
			desc.setAlignmentX(Component.LEFT_ALIGNMENT);
			content.add(desc);
			content.add(Box.createRigidArea(new Dimension(0, 8)));
		}

		// Stats row
		int participants = event.getActiveRegistrationCount();
		JLabel stats = new JLabel("Duration: " + event.getDuration() + "  |  " +
			participants + " participant" + (participants != 1 ? "s" : ""));
		stats.setFont(FontManager.getRunescapeSmallFont());
		stats.setForeground(UIConstants.TEXT_SECONDARY);
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(stats);

		// Date row
		JLabel dates = new JLabel(event.getFormattedStartDate() + " - " + event.getFormattedEndDate());
		dates.setFont(FontManager.getRunescapeSmallFont());
		dates.setForeground(UIConstants.TEXT_MUTED);
		dates.setAlignmentX(Component.LEFT_ALIGNMENT);
		dates.setBorder(new EmptyBorder(4, 0, 0, 0));
		content.add(dates);

		return content;
	}

	private JPanel buildFooter(BiConsumer<String, Boolean> onRegisterAction) {
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		footer.setOpaque(false);
		footer.setAlignmentX(Component.LEFT_ALIGNMENT);
		footer.setBorder(new EmptyBorder(12, 0, 0, 0));
		footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

		JButton btn;
		if (registrationStatus == null) {
			btn = createButton("Register", UIConstants.ACCENT_BLUE);
			btn.addActionListener(e -> { if (onRegisterAction != null) onRegisterAction.accept(event.getId(), true); });
		} else if ("pending".equals(registrationStatus)) {
			btn = createButton("Pending", UIConstants.ACCENT_GOLD);
			btn.setToolTipText("Click to withdraw. Awaiting approval.");
			btn.addActionListener(e -> { if (onRegisterAction != null) onRegisterAction.accept(event.getId(), false); });
		} else if ("registered".equals(registrationStatus)) {
			btn = createButton("Registered", UIConstants.ACCENT_GREEN);
			btn.setToolTipText("Click to withdraw registration.");
			btn.addActionListener(e -> { if (onRegisterAction != null) onRegisterAction.accept(event.getId(), false); });
		} else {
			btn = createButton("Unknown", UIConstants.TEXT_MUTED);
			btn.setEnabled(false);
		}

		footer.add(btn);
		return footer;
	}

	private JLabel createBadge(String text, Color color) {
		JLabel badge = new JLabel(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2d.dispose();
				super.paintComponent(g);
			}
		};
		badge.setFont(FontManager.getRunescapeSmallFont());
		badge.setForeground(color);
		badge.setBorder(new EmptyBorder(3, 8, 3, 8));
		badge.setOpaque(false);
		return badge;
	}

	private JButton createButton(String text, Color color) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				Color bg = !isEnabled() ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 100)
					: getModel().isPressed() ? color.darker()
					: getModel().isRollover() ? color.brighter()
					: color;

				g2d.setColor(bg);
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2d.dispose();
				super.paintComponent(g);
			}
		};
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setForeground(UIConstants.TEXT_PRIMARY);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setFocusPainted(false);
		btn.setPreferredSize(new Dimension(110, 28));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return btn;
	}
}

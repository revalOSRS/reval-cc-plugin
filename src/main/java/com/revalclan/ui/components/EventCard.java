package com.revalclan.ui.components;

import com.revalclan.api.events.EventsResponse;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;
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
	private final boolean openable;
	private boolean isHovered = false;

	/**
	 * @param onOpen when non-null the whole card is clickable and opens the
	 *               event's detail view (boards for Leagues Bingo)
	 */
	public EventCard(EventsResponse.EventSummary event, boolean isActive, String currentPlayerName,
					 BiConsumer<String, Boolean> onRegisterAction, Runnable onOpen) {
		this.event = event;
		this.isActive = isActive;
		this.registrationStatus = findRegistrationStatus(currentPlayerName);
		this.openable = onOpen != null;

		setLayout(new BorderLayout());
		setOpaque(false);
		setBorder(new EmptyBorder(0, 0, 12, 0));

		buildCard(onRegisterAction);

		addMouseListener(new MouseAdapter() {
			public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
			public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
		});

		// Press on the outer panel: inner labels have no listeners, so clicks
		// fall through to us and the hover state never flickers.
		if (onOpen != null) {
			Clickable.onPress(this, onOpen);
		}
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

		if (openable) {
			card.add(buildOpenHint());
		}

		add(card, BorderLayout.CENTER);
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setOpaque(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

		JPanel left = Labels.column();

		JLabel typeBadge = createBadge(event.getEventTypeDisplay(), getAccentColor());
		typeBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = Labels.bold(event.getName(), UIConstants.TEXT_PRIMARY);

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
		JPanel content = Labels.column();
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.setBorder(new EmptyBorder(8, 0, 0, 0));

		if (event.getDescription() != null && !event.getDescription().isEmpty()) {
			content.add(Labels.small(event.getDescription(), UIConstants.TEXT_SECONDARY));
			content.add(Box.createRigidArea(new Dimension(0, 8)));
		}

		// Stats row
		int participants = event.getActiveRegistrationCount();
		JLabel stats = Labels.small("Duration: " + event.getDuration() + "  |  " +
			participants + " participant" + (participants != 1 ? "s" : ""), UIConstants.TEXT_SECONDARY);
		content.add(stats);

		// Date row
		JLabel dates = Labels.small(event.getFormattedStartDate() + " - " + event.getFormattedEndDate(), UIConstants.TEXT_MUTED);
		dates.setBorder(new EmptyBorder(4, 0, 0, 0));
		content.add(dates);

		return content;
	}

	private JPanel buildOpenHint() {
		JPanel hint = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		hint.setOpaque(false);
		hint.setAlignmentX(Component.LEFT_ALIGNMENT);
		hint.setBorder(new EmptyBorder(8, 0, 0, 0));
		hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		hint.add(Labels.small("View boards  >", getAccentColor()));
		return hint;
	}

	private JPanel buildFooter(BiConsumer<String, Boolean> onRegisterAction) {
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		footer.setOpaque(false);
		footer.setAlignmentX(Component.LEFT_ALIGNMENT);
		footer.setBorder(new EmptyBorder(12, 0, 0, 0));
		footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

		// A null status (not registered) falls through to "Register".
		JButton btn;
		if ("pending".equalsIgnoreCase(registrationStatus)) {
			btn = createButton("Pending", UIConstants.ACCENT_GOLD);
			btn.setToolTipText("Click to withdraw. Awaiting approval.");
			btn.addActionListener(e -> { if (onRegisterAction != null) onRegisterAction.accept(event.getId(), false); });
		} else if ("registered".equalsIgnoreCase(registrationStatus)) {
			btn = createButton("Registered", UIConstants.ACCENT_GREEN);
			btn.setToolTipText("Click to withdraw registration.");
			btn.addActionListener(e -> { if (onRegisterAction != null) onRegisterAction.accept(event.getId(), false); });
		} else if ("withdrawn".equalsIgnoreCase(registrationStatus)) {
			btn = createButton("Withdrawn", UIConstants.TEXT_MUTED);
			btn.setEnabled(false);
		} else {
			btn = createButton("Register", UIConstants.ACCENT_BLUE);
			btn.addActionListener(e -> { if (onRegisterAction != null) onRegisterAction.accept(event.getId(), true); });
		}

		footer.add(btn);
		return footer;
	}

	private JLabel createBadge(String text, Color color) {
		JLabel badge = new JLabel(text) {
			@Override
			protected void paintComponent(Graphics g) {
				RoundedPanel.fill(g, this, Colors.withAlpha(color, 30), 8);
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
		JButton btn = new AccentButton(text, color);
		btn.setPreferredSize(new Dimension(110, 28));
		return btn;
	}
}

package com.revalclan.playercards;

import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.NumberFmt;
import net.runelite.client.ui.FontManager;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Trading-card style popup showing a clan member's Reval profile. Undecorated
 * and custom painted: accent glow border, dark gradient body, a diagonal
 * holo sheen, and a fade-in on open.
 */
public class PlayerCardWindow extends JFrame {
	private static final int GLOW = 14;
	private static final int CARD_W = 300;
	private static final int CARD_H = 470;
	private static final int ARC = 20;

	private final Color accent;

	public PlayerCardWindow(PlayerCardData data, Color accentColor, ClanRankIconResolver rankIconResolver) {
		this.accent = accentColor;

		setUndecorated(true);
		setBackground(new Color(0, 0, 0, 0));
		setAlwaysOnTop(true);
		setSize(CARD_W + GLOW * 2, CARD_H + GLOW * 2);
		setLocationRelativeTo(null);

		JPanel card = buildCard(data, rankIconResolver);
		setContentPane(card);
		makeDraggable(card);

		card.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
			.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
		card.getActionMap().put("close", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				dispose();
			}
		});

		fadeIn();
	}

	private JPanel buildCard(PlayerCardData data, ClanRankIconResolver rankIconResolver) {
		JPanel card = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int x = GLOW, y = GLOW, w = CARD_W, h = CARD_H;

				// Soft accent glow radiating from the card edge
				for (int i = GLOW; i > 0; i--) {
					g2d.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
						Math.max(1, 26 - i * 2)));
					g2d.setStroke(new BasicStroke(1));
					g2d.drawRoundRect(x - i, y - i, w + i * 2, h + i * 2, ARC + i, ARC + i);
				}

				// Card body
				g2d.setPaint(new GradientPaint(x, y, new Color(0x24242f), x, y + h, new Color(0x121219)));
				g2d.fillRoundRect(x, y, w, h, ARC, ARC);

				// Accent wash behind the header
				g2d.setPaint(new GradientPaint(x, y, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 46),
					x, y + 150, new Color(0, 0, 0, 0)));
				g2d.fillRoundRect(x, y, w, h, ARC, ARC);

				// Diagonal holo sheen
				g2d.setClip(new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, ARC, ARC));
				g2d.setPaint(new GradientPaint(x, y + h, new Color(255, 255, 255, 0),
					x + w, y, new Color(255, 255, 255, 18)));
				int band = 110;
				g2d.fillPolygon(
					new int[]{x + w - band - 60, x + w - 60, x + w, x + w - band},
					new int[]{y + h, y, y, y + h}, 4);
				g2d.setClip(null);

				// Border
				g2d.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 210));
				g2d.setStroke(new BasicStroke(2f));
				g2d.drawRoundRect(x, y, w - 1, h - 1, ARC, ARC);

				g2d.dispose();
			}
		};
		card.setOpaque(false);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(new EmptyBorder(GLOW + 14, GLOW + 24, GLOW + 16, GLOW + 24));

		// Close button row
		JLabel close = new JLabel("x");
		close.setFont(FontManager.getRunescapeBoldFont());
		close.setForeground(UIConstants.TEXT_MUTED);
		close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		close.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				dispose();
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				close.setForeground(UIConstants.TEXT_PRIMARY);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				close.setForeground(UIConstants.TEXT_MUTED);
			}
		});
		JPanel closeRow = transparentRow();
		closeRow.add(Box.createHorizontalGlue());
		closeRow.add(close);
		card.add(closeRow);

		// Rank
		JLabel rankIcon = new JLabel();
		rankIcon.setPreferredSize(new Dimension(26, 26));
		rankIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
		if (rankIconResolver != null) {
			rankIconResolver.apply(data.getRankName(), rankIcon, 26);
		}
		card.add(rankIcon);
		card.add(Box.createVerticalStrut(4));
		card.add(centered(data.getRankName().toUpperCase(), FontManager.getRunescapeSmallFont(), accent));

		// Name
		card.add(Box.createVerticalStrut(8));
		JLabel name = centered(data.getPlayerName(), FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 22f), UIConstants.TEXT_PRIMARY);
		card.add(name);

		// Points
		card.add(Box.createVerticalStrut(14));
		card.add(centered(NumberFmt.group(data.getPoints()), FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 26f), UIConstants.ACCENT_GOLD));
		card.add(centered("ACTIVITY POINTS", FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));

		// Rank progress
		card.add(Box.createVerticalStrut(12));
		card.add(progressBar(data.getRankProgress()));
		card.add(Box.createVerticalStrut(4));
		String progressText = data.getNextRankName() != null
			? NumberFmt.group(data.getPointsToNext()) + " pts to " + data.getNextRankName()
			: "Max rank";
		card.add(centered(progressText, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY));

		// Stats grid
		card.add(Box.createVerticalStrut(16));
		JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.CENTER_ALIGNMENT);
		grid.setMaximumSize(new Dimension(CARD_W - 48, 130));
		grid.add(statTile(String.valueOf(data.getDrops()), "Drops"));
		grid.add(statTile(String.valueOf(data.getPets()), "Pets"));
		grid.add(statTile(String.valueOf(data.getEventsPlayed()), "Events"));
		grid.add(statTile(String.valueOf(data.getDiariesDone()), "Diary tasks"));
		card.add(grid);

		// Footer
		card.add(Box.createVerticalGlue());
		JPanel footer = transparentRow();
		footer.add(label("REVAL", FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_GOLD));
		footer.add(Box.createHorizontalGlue());
		footer.add(label("Member since " + data.getMemberSince(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));
		card.add(footer);

		return card;
	}

	private JComponent progressBar(double fraction) {
		JComponent bar = new JComponent() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(new Color(255, 255, 255, 26));
				g2d.fillRoundRect(0, 0, getWidth(), 5, 5, 5);
				int fill = (int) Math.round(getWidth() * Math.min(1.0, Math.max(0.0, fraction)));
				if (fill > 0) {
					g2d.setColor(accent);
					g2d.fillRoundRect(0, 0, fill, 5, 5, 5);
				}
				g2d.dispose();
			}
		};
		bar.setMaximumSize(new Dimension(CARD_W - 80, 5));
		bar.setPreferredSize(new Dimension(CARD_W - 80, 5));
		bar.setAlignmentX(Component.CENTER_ALIGNMENT);
		return bar;
	}

	private JPanel statTile(String value, String caption) {
		JPanel tile = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(new Color(255, 255, 255, 14));
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				g2d.dispose();
			}
		};
		tile.setOpaque(false);
		tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
		tile.setBorder(new EmptyBorder(10, 6, 10, 6));
		tile.add(Box.createVerticalGlue());
		tile.add(centered(value, FontManager.getRunescapeBoldFont(), UIConstants.TEXT_PRIMARY));
		tile.add(Box.createVerticalStrut(2));
		tile.add(centered(caption, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_MUTED));
		tile.add(Box.createVerticalGlue());
		return tile;
	}

	private JLabel centered(String text, Font font, Color color) {
		JLabel label = label(text, font, color);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		return label;
	}

	private JLabel label(String text, Font font, Color color) {
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	private JPanel transparentRow() {
		JPanel row = new JPanel();
		row.setOpaque(false);
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setAlignmentX(Component.CENTER_ALIGNMENT);
		row.setMaximumSize(new Dimension(CARD_W - 40, 24));
		return row;
	}

	private void makeDraggable(JPanel card) {
		final Point[] origin = {null};
		card.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				origin[0] = e.getPoint();
			}
		});
		card.addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (origin[0] != null) {
					Point screen = e.getLocationOnScreen();
					setLocation(screen.x - origin[0].x, screen.y - origin[0].y);
				}
			}
		});
	}

	private void fadeIn() {
		setOpacity(0f);
		Timer fade = new Timer(16, null);
		fade.addActionListener(e -> {
			float next = Math.min(1f, getOpacity() + 0.09f);
			setOpacity(next);
			if (next >= 1f) {
				fade.stop();
			}
		});
		fade.start();
	}
}

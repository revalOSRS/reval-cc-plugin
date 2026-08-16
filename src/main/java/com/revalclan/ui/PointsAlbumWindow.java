package com.revalclan.ui;

import com.revalclan.api.account.AccountResponse;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Album" window for the full points log — a searchable, filterable, paged
 * card grid (inspired by the OSRS TCG collection album). Profile stat cards
 * open it with the matching source filter pre-applied.
 */
public class PointsAlbumWindow extends JFrame {
	private static final int PAGE_SIZE = 24;
	private static final int GRID_COLUMNS = 4;
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

	/** Combo label -> filter key ("misc" = everything without a dedicated card) */
	private static final String[][] SOURCES = {
		{"All sources", null},
		{"Drops", "drop"},
		{"Pets", "pet"},
		{"Milestones", "milestone"},
		{"Diaries", "reval_diary"},
		{"Challenges", "reval_challenge"},
		{"Events", "event"},
		{"Misc", "misc"},
	};
	private static final Set<String> KNOWN_TYPES = new HashSet<>(Arrays.asList(
		"drop", "pet", "milestone", "event", "reval_diary", "reval_challenge"
	));

	private final ItemManager itemManager;
	private final List<AccountResponse.PointsLogEntry> allEntries;

	private final JComboBox<String> sourceCombo;
	private final JComboBox<String> sortCombo;
	private final JTextField searchField;
	private final JLabel summaryLabel = new JLabel();
	private final JLabel pageLabel = new JLabel();
	private final JButton prevButton;
	private final JButton nextButton;
	private final JPanel gridPanel;

	private List<AccountResponse.PointsLogEntry> filtered = new ArrayList<>();
	private int page = 0;

	public PointsAlbumWindow(String playerName, List<AccountResponse.PointsLogEntry> entries, ItemManager itemManager) {
		super("Reval - " + (playerName != null ? playerName + "'s " : "") + "Points Log");
		this.itemManager = itemManager;
		this.allEntries = entries != null ? entries : new ArrayList<>();

		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(920, 680);
		setLocationRelativeTo(null);
		getContentPane().setBackground(UIConstants.BACKGROUND);
		setLayout(new BorderLayout());

		// ==================== Control bar ====================
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(UIConstants.BACKGROUND);
		controls.setBorder(new EmptyBorder(12, 16, 8, 16));

		JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		filterRow.setOpaque(false);

		filterRow.add(mutedLabel("Source:"));
		String[] sourceLabels = new String[SOURCES.length];
		for (int i = 0; i < SOURCES.length; i++) sourceLabels[i] = SOURCES[i][0];
		sourceCombo = styledCombo(new JComboBox<>(sourceLabels));
		filterRow.add(sourceCombo);

		filterRow.add(mutedLabel("Sort:"));
		sortCombo = styledCombo(new JComboBox<>(new String[]{
			"Newest first", "Oldest first", "Highest points", "Lowest points"
		}));
		filterRow.add(sortCombo);

		filterRow.add(mutedLabel("Search:"));
		searchField = new JTextField(14);
		searchField.setFont(FontManager.getRunescapeSmallFont());
		searchField.setForeground(UIConstants.TEXT_PRIMARY);
		searchField.setBackground(UIConstants.CARD_BG);
		searchField.setCaretColor(UIConstants.TEXT_PRIMARY);
		searchField.setBorder(new EmptyBorder(4, 8, 4, 8));
		filterRow.add(searchField);

		JPanel pagingRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
		pagingRow.setOpaque(false);

		summaryLabel.setFont(FontManager.getRunescapeSmallFont());
		summaryLabel.setForeground(UIConstants.ACCENT_GOLD);

		prevButton = pagingButton("< Prev");
		nextButton = pagingButton("Next >");
		pageLabel.setFont(FontManager.getRunescapeSmallFont());
		pageLabel.setForeground(UIConstants.TEXT_SECONDARY);

		pagingRow.add(summaryLabel);
		pagingRow.add(Box.createHorizontalStrut(16));
		pagingRow.add(prevButton);
		pagingRow.add(pageLabel);
		pagingRow.add(nextButton);

		controls.add(filterRow);
		controls.add(Box.createRigidArea(new Dimension(0, 8)));
		controls.add(pagingRow);

		// ==================== Card grid ====================
		gridPanel = new JPanel(new GridLayout(0, GRID_COLUMNS, 10, 10));
		gridPanel.setBackground(UIConstants.BACKGROUND);
		gridPanel.setBorder(new EmptyBorder(8, 16, 16, 16));

		JPanel gridWrapper = new JPanel(new BorderLayout());
		gridWrapper.setBackground(UIConstants.BACKGROUND);
		gridWrapper.add(gridPanel, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(gridWrapper);
		scroll.setBorder(null);
		scroll.setBackground(UIConstants.BACKGROUND);
		scroll.getViewport().setBackground(UIConstants.BACKGROUND);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		add(controls, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);

		// ==================== Wiring ====================
		sourceCombo.addActionListener(e -> { page = 0; rebuild(); });
		sortCombo.addActionListener(e -> { page = 0; rebuild(); });
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { page = 0; rebuild(); }
			public void removeUpdate(DocumentEvent e) { page = 0; rebuild(); }
			public void changedUpdate(DocumentEvent e) { page = 0; rebuild(); }
		});
		prevButton.addActionListener(e -> { if (page > 0) { page--; rebuild(); } });
		nextButton.addActionListener(e -> { if ((page + 1) * PAGE_SIZE < filtered.size()) { page++; rebuild(); } });

		rebuild();
	}

	/** Pre-select a source filter using the profile stat-card keys. */
	public void selectSource(String statCardKey) {
		String key = statCardKey == null ? null
			: statCardKey.equals("revalDiaries") ? "reval_diary"
			: statCardKey.equals("revalChallenges") ? "reval_challenge"
			: statCardKey;
		for (int i = 0; i < SOURCES.length; i++) {
			if (key == null ? SOURCES[i][1] == null : key.equals(SOURCES[i][1])) {
				sourceCombo.setSelectedIndex(i);
				return;
			}
		}
	}

	// ==================== Pipeline ====================

	private void rebuild() {
		String key = SOURCES[sourceCombo.getSelectedIndex()][1];
		String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";

		filtered = new ArrayList<>();
		long totalPts = 0;
		for (AccountResponse.PointsLogEntry entry : allEntries) {
			if (entry.getPointsChange() == null) continue;
			String type = entry.getSourceType() != null ? entry.getSourceType().toLowerCase() : "";
			if (key != null) {
				boolean match = "misc".equals(key) ? !KNOWN_TYPES.contains(type) : key.equals(type);
				if (!match) continue;
			}
			if (!query.isEmpty()) {
				String desc = entry.getSourceDescription() != null ? entry.getSourceDescription().toLowerCase() : "";
				if (!desc.contains(query)) continue;
			}
			filtered.add(entry);
			totalPts += entry.getPointsChange();
		}

		Comparator<AccountResponse.PointsLogEntry> byDate =
			Comparator.comparing(e -> e.getCreatedAt() != null ? e.getCreatedAt() : "");
		Comparator<AccountResponse.PointsLogEntry> byPoints =
			Comparator.comparingInt(e -> e.getPointsChange() != null ? e.getPointsChange() : 0);
		switch (sortCombo.getSelectedIndex()) {
			case 1: filtered.sort(byDate); break;
			case 2: filtered.sort(byPoints.reversed()); break;
			case 3: filtered.sort(byPoints); break;
			default: filtered.sort(byDate.reversed()); break;
		}

		int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.min(page, pages - 1);
		int from = page * PAGE_SIZE;
		int to = Math.min(from + PAGE_SIZE, filtered.size());

		summaryLabel.setText(filtered.size() + " entries - " + String.format("%,d", totalPts).replace(",", " ") + " pts");
		pageLabel.setText("Page " + (page + 1) + "/" + pages + "  (" + (filtered.isEmpty() ? 0 : from + 1) + " - " + to + ")");
		prevButton.setEnabled(page > 0);
		nextButton.setEnabled(to < filtered.size());

		gridPanel.removeAll();
		for (int i = from; i < to; i++) {
			gridPanel.add(createEntryCard(filtered.get(i)));
		}
		// keep card sizes stable on partial pages
		for (int i = to - from; i < Math.min(PAGE_SIZE, GRID_COLUMNS * 2); i++) {
			JPanel filler = new JPanel();
			filler.setOpaque(false);
			gridPanel.add(filler);
		}
		gridPanel.revalidate();
		gridPanel.repaint();
	}

	// ==================== Cards ====================

	private JPanel createEntryCard(AccountResponse.PointsLogEntry entry) {
		Color accent = sourceColor(entry.getSourceType());

		JPanel card = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(UIConstants.CARD_BG);
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				g2d.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
				g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
				g2d.dispose();
			}
		};
		card.setOpaque(false);
		card.setLayout(new BorderLayout());
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		card.setPreferredSize(new Dimension(200, 132));
		if (entry.getSourceDescription() != null) {
			card.setToolTipText(entry.getSourceDescription());
		}

		// Title band
		String[] titleParts = splitDescription(entry.getSourceDescription());
		JLabel title = new JLabel(truncate(titleParts[0], 24), SwingConstants.CENTER);
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(UIConstants.TEXT_PRIMARY);
		card.add(title, BorderLayout.NORTH);

		// Center: icon + origin line
		JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		center.setOpaque(false);

		center.add(Box.createVerticalGlue());
		JLabel icon = new JLabel();
		icon.setAlignmentX(Component.CENTER_ALIGNMENT);
		icon.setPreferredSize(new Dimension(36, 36));
		icon.setMaximumSize(new Dimension(36, 36));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (entry.getItemId() != null && itemManager != null) {
			loadItemIcon(entry.getItemId(), icon);
		} else {
			icon.setIcon(new SourceBadgeIcon(accent, sourceLabel(entry.getSourceType())));
		}
		center.add(icon);

		JLabel origin = new JLabel(truncate(titleParts[1], 28), SwingConstants.CENTER);
		origin.setFont(FontManager.getRunescapeSmallFont());
		origin.setForeground(UIConstants.TEXT_SECONDARY);
		origin.setAlignmentX(Component.CENTER_ALIGNMENT);
		center.add(Box.createRigidArea(new Dimension(0, 3)));
		center.add(origin);
		center.add(Box.createVerticalGlue());

		card.add(center, BorderLayout.CENTER);

		// Footer: points + date
		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);

		int pts = entry.getPointsChange() != null ? entry.getPointsChange() : 0;
		JLabel points = new JLabel((pts >= 0 ? "+" : "") + pts + " pts");
		points.setFont(FontManager.getRunescapeBoldFont());
		points.setForeground(pts >= 0 ? UIConstants.ACCENT_GOLD : UIConstants.ERROR_COLOR);

		JLabel date = new JLabel(formatDate(entry.getCreatedAt()));
		date.setFont(FontManager.getRunescapeSmallFont());
		date.setForeground(UIConstants.TEXT_MUTED);

		footer.add(points, BorderLayout.WEST);
		footer.add(date, BorderLayout.EAST);
		card.add(footer, BorderLayout.SOUTH);

		return card;
	}

	/** [what, where-from] from descriptions like "Drop: Elder venator fang from Maggot King (KC: 152)" */
	private String[] splitDescription(String desc) {
		if (desc == null || desc.isEmpty()) return new String[]{"Unknown", " "};
		String body = desc;
		int colon = body.indexOf(": ");
		if (colon > 0 && colon < 20) body = body.substring(colon + 2);
		int from = body.lastIndexOf(" from ");
		if (from > 0) {
			return new String[]{body.substring(0, from), body.substring(from + 6)};
		}
		return new String[]{body, " "};
	}

	private String truncate(String text, int max) {
		if (text == null) return " ";
		return text.length() <= max ? text : text.substring(0, max - 2) + "..";
	}

	private void loadItemIcon(int itemId, JLabel target) {
		AsyncBufferedImage img = itemManager.getImage(itemId);
		Runnable apply = () -> SwingUtilities.invokeLater(() ->
			target.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 36, 36))));
		img.onLoaded(apply);
		apply.run();
	}

	private String formatDate(String iso) {
		if (iso == null) return "";
		try {
			return ZonedDateTime.parse(iso).withZoneSameInstant(ZoneId.systemDefault()).format(DATE_FMT);
		} catch (Exception e) {
			try {
				return java.time.LocalDateTime.parse(iso.replace(" ", "T").replaceAll("Z$", ""))
					.format(DATE_FMT);
			} catch (Exception ignored) {
				return iso.length() > 10 ? iso.substring(0, 10) : iso;
			}
		}
	}

	private Color sourceColor(String sourceType) {
		String t = sourceType != null ? sourceType.toLowerCase() : "";
		switch (t) {
			case "drop": return UIConstants.ACCENT_GREEN;
			case "pet": return UIConstants.ACCENT_PURPLE;
			case "milestone": return UIConstants.ACCENT_BLUE;
			case "reval_diary": return UIConstants.ACCENT_GOLD;
			case "reval_challenge": return UIConstants.ACCENT_GREEN;
			case "event": return UIConstants.ACCENT_BLUE;
			default: return UIConstants.TEXT_SECONDARY;
		}
	}

	private String sourceLabel(String sourceType) {
		String t = sourceType != null ? sourceType.toLowerCase() : "";
		switch (t) {
			case "drop": return "D";
			case "pet": return "P";
			case "milestone": return "M";
			case "reval_diary": return "DI";
			case "reval_challenge": return "C";
			case "event": return "E";
			default: return "?";
		}
	}

	// ==================== Small UI helpers ====================

	private JLabel mutedLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(UIConstants.TEXT_SECONDARY);
		return label;
	}

	private JComboBox<String> styledCombo(JComboBox<String> combo) {
		combo.setFont(FontManager.getRunescapeSmallFont());
		combo.setForeground(UIConstants.TEXT_PRIMARY);
		combo.setBackground(UIConstants.CARD_BG);
		combo.setFocusable(false);
		return combo;
	}

	private JButton pagingButton(String text) {
		JButton btn = new JButton(text);
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setForeground(UIConstants.TEXT_PRIMARY);
		btn.setBackground(UIConstants.CARD_BG);
		btn.setFocusPainted(false);
		btn.setBorder(new EmptyBorder(4, 10, 4, 10));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return btn;
	}

	/** Round badge with the source initial, for entries without an item icon */
	private static class SourceBadgeIcon implements Icon {
		private static final int SIZE = 32;
		private final Color color;
		private final String text;

		SourceBadgeIcon(Color color, String text) {
			this.color = color;
			this.text = text;
		}

		@Override
		public int getIconWidth() {
			return SIZE;
		}

		@Override
		public int getIconHeight() {
			return SIZE;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
			g2d.fillOval(x, y, SIZE, SIZE);
			g2d.setColor(color);
			g2d.drawOval(x, y, SIZE - 1, SIZE - 1);
			g2d.setFont(FontManager.getRunescapeBoldFont());
			FontMetrics fm = g2d.getFontMetrics();
			int tx = x + (SIZE - fm.stringWidth(text)) / 2;
			int ty = y + (SIZE + fm.getAscent() - fm.getDescent()) / 2;
			g2d.drawString(text, tx, ty);
			g2d.dispose();
		}
	}
}

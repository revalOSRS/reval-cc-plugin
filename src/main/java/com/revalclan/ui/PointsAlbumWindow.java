package com.revalclan.ui;

import com.revalclan.api.account.AccountResponse;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.NumberFmt;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	/** Point sources: combo label, backend source_type filter, and card accent/badge. */
	private enum SourceKind {
		ALL("All sources", null, UIConstants.TEXT_SECONDARY, "?"),
		DROP("Drops", "drop", UIConstants.ACCENT_GREEN, "D"),
		PET("Pets", "pet", UIConstants.ACCENT_PURPLE, "P"),
		MILESTONE("Milestones", "milestone", UIConstants.ACCENT_BLUE, "M"),
		DIARY("Diaries", "reval_diary", UIConstants.ACCENT_GOLD, "DI"),
		CHALLENGE("Challenges", "reval_challenge", UIConstants.ACCENT_GREEN, "C"),
		EVENT("Events", "event", UIConstants.ACCENT_BLUE, "E"),
		MISC("Misc", "misc", UIConstants.TEXT_SECONDARY, "?");

		final String label;
		final String key;
		final Color color;
		final String badge;

		SourceKind(String label, String key, Color color, String badge) {
			this.label = label;
			this.key = key;
			this.color = color;
			this.badge = badge;
		}

		/** The kind a raw source_type renders as; MISC for anything without a dedicated entry. */
		static SourceKind of(String sourceType) {
			String t = sourceType != null ? sourceType.toLowerCase() : "";
			for (SourceKind kind : values()) {
				if (t.equals(kind.key)) {
					return kind;
				}
			}
			return MISC;
		}

		boolean matches(String sourceType) {
			if (key == null) {
				return true;
			}
			if (this == MISC) {
				return of(sourceType) == MISC;
			}
			return key.equals(sourceType);
		}
	}

	/** One rendered card: a single log entry, or several grouped by title */
	private static class CardData {
		String title = "Unknown";
		String origin = " ";
		Integer itemId;
		String sourceType;
		int points;
		int count;
		String latestDate = "";
	}

	private final ItemManager itemManager;
	private final List<AccountResponse.PointsLogEntry> allEntries;

	private final JComboBox<String> sourceCombo;
	private final JComboBox<String> sortCombo;
	private final JTextField searchField;
	private final JCheckBox groupToggle;
	private final JLabel summaryLabel = new JLabel();
	private final JLabel pageLabel = new JLabel();
	private final JButton prevButton;
	private final JButton nextButton;
	private final JPanel gridPanel;

	private List<CardData> filtered = new ArrayList<>();
	private int page = 0;

	public PointsAlbumWindow(String playerName, List<AccountResponse.PointsLogEntry> entries, ItemManager itemManager) {
		super("Reval - " + (playerName != null ? playerName + "'s " : "") + "Points Log");
		this.itemManager = itemManager;
		this.allEntries = entries != null ? entries : new ArrayList<>();

		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(920, 700);
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
		SourceKind[] kinds = SourceKind.values();
		String[] sourceLabels = new String[kinds.length];
		for (int i = 0; i < kinds.length; i++) sourceLabels[i] = kinds[i].label;
		sourceCombo = styledCombo(new JComboBox<>(sourceLabels));
		filterRow.add(sourceCombo);

		filterRow.add(mutedLabel("Sort:"));
		sortCombo = styledCombo(new JComboBox<>(new String[]{
			"Newest first", "Oldest first", "Highest points", "Lowest points"
		}));
		filterRow.add(sortCombo);

		filterRow.add(mutedLabel("Search:"));
		searchField = new JTextField(12);
		searchField.setFont(FontManager.getRunescapeSmallFont());
		searchField.setForeground(UIConstants.TEXT_PRIMARY);
		searchField.setBackground(UIConstants.CARD_BG);
		searchField.setCaretColor(UIConstants.TEXT_PRIMARY);
		searchField.setBorder(new EmptyBorder(4, 8, 4, 8));
		filterRow.add(searchField);

		groupToggle = new JCheckBox("Group");
		groupToggle.setFont(FontManager.getRunescapeSmallFont());
		groupToggle.setForeground(UIConstants.TEXT_SECONDARY);
		groupToggle.setOpaque(false);
		groupToggle.setFocusPainted(false);
		groupToggle.setToolTipText("Combine repeat entries of the same source into one card");
		filterRow.add(groupToggle);

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
		groupToggle.addActionListener(e -> { page = 0; rebuild(); });
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { page = 0; rebuild(); }
			public void removeUpdate(DocumentEvent e) { page = 0; rebuild(); }
			public void changedUpdate(DocumentEvent e) { page = 0; rebuild(); }
		});
		prevButton.addActionListener(e -> { if (page > 0) { page--; rebuild(); } });
		nextButton.addActionListener(e -> { if ((page + 1) * PAGE_SIZE < filtered.size()) { page++; rebuild(); } });

		rebuild();
	}

	/** Pre-select a source filter by its backend source_type key (null = all). */
	public void selectSource(String sourceKey) {
		SourceKind[] kinds = SourceKind.values();
		for (int i = 0; i < kinds.length; i++) {
			if (sourceKey == null ? kinds[i].key == null : sourceKey.equals(kinds[i].key)) {
				sourceCombo.setSelectedIndex(i);
				return;
			}
		}
	}

	// ==================== Pipeline ====================

	private void rebuild() {
		SourceKind source = SourceKind.values()[sourceCombo.getSelectedIndex()];
		String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";

		List<CardData> cards = new ArrayList<>();
		long totalPts = 0;
		int totalEntries = 0;
		Map<String, CardData> grouped = new LinkedHashMap<>();

		for (AccountResponse.PointsLogEntry entry : allEntries) {
			if (entry.getPointsChange() == null) continue;
			String type = entry.getSourceType() != null ? entry.getSourceType().toLowerCase() : "";
			if (!source.matches(type)) continue;
			if (!query.isEmpty()) {
				String desc = entry.getSourceDescription() != null ? entry.getSourceDescription().toLowerCase() : "";
				if (!desc.contains(query)) continue;
			}

			String[] parts = splitDescription(entry.getSourceDescription());
			totalPts += entry.getPointsChange();
			totalEntries++;

			if (groupToggle.isSelected()) {
				String groupKey = parts[0].toLowerCase() + "|" + type;
				CardData card = grouped.get(groupKey);
				if (card == null) {
					card = new CardData();
					card.title = parts[0];
					card.origin = stripKc(parts[1]);
					card.itemId = entry.getItemId();
					card.sourceType = entry.getSourceType();
					grouped.put(groupKey, card);
					cards.add(card);
				} else if (!card.origin.equals(stripKc(parts[1]))) {
					card.origin = "Various";
				}
				if (card.itemId == null) card.itemId = entry.getItemId();
				card.points += entry.getPointsChange();
				card.count++;
				String date = entry.getCreatedAt() != null ? entry.getCreatedAt() : "";
				if (date.compareTo(card.latestDate) > 0) card.latestDate = date;
			} else {
				CardData card = new CardData();
				card.title = parts[0];
				card.origin = parts[1];
				card.itemId = entry.getItemId();
				card.sourceType = entry.getSourceType();
				card.points = entry.getPointsChange();
				card.count = 1;
				card.latestDate = entry.getCreatedAt() != null ? entry.getCreatedAt() : "";
				cards.add(card);
			}
		}

		Comparator<CardData> byDate = Comparator.comparing(c -> c.latestDate);
		Comparator<CardData> byPoints = Comparator.comparingInt(c -> c.points);
		switch (sortCombo.getSelectedIndex()) {
			case 1: cards.sort(byDate); break;
			case 2: cards.sort(byPoints.reversed()); break;
			case 3: cards.sort(byPoints); break;
			default: cards.sort(byDate.reversed()); break;
		}
		filtered = cards;

		int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.min(page, pages - 1);
		int from = page * PAGE_SIZE;
		int to = Math.min(from + PAGE_SIZE, filtered.size());

		String entryWord = groupToggle.isSelected()
			? filtered.size() + " sources (" + totalEntries + " entries)"
			: totalEntries + " entries";
		summaryLabel.setText(entryWord + " - " + NumberFmt.group(totalPts) + " pts");
		pageLabel.setText("Page " + (page + 1) + "/" + pages + "  (" + (filtered.isEmpty() ? 0 : from + 1) + " - " + to + ")");
		prevButton.setEnabled(page > 0);
		nextButton.setEnabled(to < filtered.size());

		gridPanel.removeAll();
		for (int i = from; i < to; i++) {
			gridPanel.add(createEntryCard(filtered.get(i)));
		}
		// GridLayout(0, n) re-derives the column count from the row count, so a
		// partial last row shrinks every column; pad it to keep 4 equal columns
		int remainder = (to - from) % GRID_COLUMNS;
		int fillers = (to - from) == 0 ? GRID_COLUMNS : (remainder == 0 ? 0 : GRID_COLUMNS - remainder);
		for (int i = 0; i < fillers; i++) {
			JPanel filler = new JPanel();
			filler.setOpaque(false);
			gridPanel.add(filler);
		}
		gridPanel.revalidate();
		gridPanel.repaint();
	}

	// ==================== Cards ====================

	private JPanel createEntryCard(CardData card) {
		SourceKind kind = SourceKind.of(card.sourceType);
		Color accent = kind.color;

		JPanel panel = new JPanel() {
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
		panel.setOpaque(false);
		panel.setLayout(new BorderLayout());
		panel.setBorder(new EmptyBorder(8, 10, 8, 10));
		panel.setPreferredSize(new Dimension(200, 150));

		// Title: wraps so the full text is always visible, centered per line
		WrapLabel title = new WrapLabel(card.title, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_PRIMARY, 172);
		panel.add(title, BorderLayout.NORTH);

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
		if (card.itemId != null && itemManager != null) {
			loadItemIcon(card.itemId, icon);
		} else {
			icon.setIcon(new SourceBadgeIcon(accent, kind.badge));
		}
		center.add(icon);

		WrapLabel origin = new WrapLabel(card.origin, FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY, 172);
		origin.setAlignmentX(Component.CENTER_ALIGNMENT);
		center.add(Box.createRigidArea(new Dimension(0, 3)));
		center.add(origin);
		center.add(Box.createVerticalGlue());

		panel.add(center, BorderLayout.CENTER);

		// Footer: points (+ count when grouped) + date
		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);

		JPanel pointsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		pointsPanel.setOpaque(false);

		JLabel points = new JLabel((card.points >= 0 ? "+" : "") + card.points + " pts");
		points.setFont(FontManager.getRunescapeBoldFont());
		points.setForeground(card.points >= 0 ? UIConstants.ACCENT_GOLD : UIConstants.ERROR_COLOR);
		pointsPanel.add(points);

		if (card.count > 1) {
			JLabel count = new JLabel("x" + card.count);
			count.setFont(FontManager.getRunescapeSmallFont());
			count.setForeground(UIConstants.TEXT_SECONDARY);
			pointsPanel.add(count);
		}

		JLabel date = new JLabel(formatDate(card.latestDate));
		date.setFont(FontManager.getRunescapeSmallFont());
		date.setForeground(UIConstants.TEXT_MUTED);

		footer.add(pointsPanel, BorderLayout.WEST);
		footer.add(date, BorderLayout.EAST);
		panel.add(footer, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Prefixes the backend's points-log description builders emit ("Drop: X from Y",
	 * "Milestone: ..."). They repeat what the card's badge already shows, so they are
	 * stripped from titles; anything else ("200M XP:", boss names) is meaningful and kept.
	 */
	private static final Set<String> STRIP_PREFIXES = new HashSet<>(Arrays.asList(
		"drop", "pet", "new pet", "duplicate pet", "milestone", "xp milestone",
		"loyalty", "event", "manual", "misc", "diary", "challenge", "achievement",
		"admin adjustment", "combat achievement upgrade"
	));

	/** [what, where-from] from descriptions like "Drop: Elder venator fang from Maggot King (KC: 152)" */
	private String[] splitDescription(String desc) {
		if (desc == null || desc.isEmpty()) return new String[]{"Unknown", " "};
		// The RuneScape font has no glyphs for these; keep card text ASCII
		String body = desc.replace(" \u2192 ", " -> ").replace("\u2192", "->").replace("\u2022", "-");
		int colon = body.indexOf(": ");
		if (colon > 0 && STRIP_PREFIXES.contains(body.substring(0, colon).toLowerCase())) {
			body = body.substring(colon + 2);
		}
		// Collapse "X: A -> X: B" upgrade chains into "X: A -> B"
		body = body.replaceAll("^(.+?): (.+?) -> \\1: (.+)$", "$1: $2 -> $3");
		int from = body.lastIndexOf(" from ");
		if (from > 0) {
			return new String[]{body.substring(0, from), body.substring(from + 6)};
		}
		return new String[]{body, " "};
	}

	private String stripKc(String origin) {
		if (origin == null) return " ";
		return origin.replaceAll(" \\(KC: [^)]*\\)$", "");
	}

	private void loadItemIcon(int itemId, JLabel target) {
		AsyncBufferedImage img = itemManager.getImage(itemId);
		Runnable apply = () -> SwingUtilities.invokeLater(() ->
			target.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 36, 36))));
		img.onLoaded(apply);
		apply.run();
	}

	private String formatDate(String iso) {
		if (iso == null || iso.isEmpty()) return "";
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

	/**
	 * Word-wrapped, line-centered text component. Swing's HTML width handling
	 * wraps inconsistently with the RuneScape font, so lines are computed and
	 * painted manually.
	 */
	private static class WrapLabel extends JComponent {
		private final List<String> lines = new ArrayList<>();
		private final Font font;
		private final int lineHeight;
		private final int wrapWidth;

		WrapLabel(String text, Font font, Color color, int wrapWidth) {
			this.font = font;
			this.wrapWidth = wrapWidth;
			setForeground(color);
			FontMetrics fm = getFontMetrics(font);
			this.lineHeight = fm.getHeight();
			wrap(text != null ? text.trim() : "", fm);
		}

		private void wrap(String text, FontMetrics fm) {
			if (text.isEmpty()) {
				lines.add(" ");
				return;
			}
			StringBuilder line = new StringBuilder();
			for (String word : text.split(" ")) {
				String candidate = line.length() == 0 ? word : line + " " + word;
				if (fm.stringWidth(candidate) <= wrapWidth || line.length() == 0) {
					line = new StringBuilder(candidate);
				} else {
					lines.add(line.toString());
					line = new StringBuilder(word);
				}
			}
			if (line.length() > 0) {
				lines.add(line.toString());
			}
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(wrapWidth, lines.size() * lineHeight + 2);
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setFont(font);
			g2d.setColor(getForeground());
			FontMetrics fm = g2d.getFontMetrics();
			int y = fm.getAscent();
			for (String line : lines) {
				int x = (getWidth() - fm.stringWidth(line)) / 2;
				g2d.drawString(line, Math.max(0, x), y);
				y += lineHeight;
			}
			g2d.dispose();
		}
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

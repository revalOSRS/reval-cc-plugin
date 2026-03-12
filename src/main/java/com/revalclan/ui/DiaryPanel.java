package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.diaries.DiariesResponse;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.*;

@Slf4j
public class DiaryPanel extends JPanel {

	private RevalApiService apiService;
	private Client client;
	private UIAssetLoader assetLoader;

	private final JPanel contentPanel;
	private final CardLayout cardLayout;
	private final JPanel mainContainer;

	private java.util.List<DiariesResponse.Diary> allDiaries = new ArrayList<>();
	private DiariesResponse.Diary selectedDiary = null;
	private final Set<String> expandedTiers = new HashSet<>();
	private RefreshButton refreshButton;

	public DiaryPanel() {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		cardLayout = new CardLayout();
		mainContainer = new JPanel(cardLayout);
		mainContainer.setBackground(UIConstants.BACKGROUND);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(UIConstants.BACKGROUND);
		contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

		mainContainer.add(wrapScrollable(contentPanel), "LIST");
		add(mainContainer, BorderLayout.CENTER);
		showNotLoggedIn();
	}

	public void init(RevalApiService apiService, Client client, UIAssetLoader assetLoader) {
		this.apiService = apiService;
		this.client = client;
		this.assetLoader = assetLoader;
		showNotLoggedIn();
	}

	public void onLoggedIn() { loadData(); }
	public void onLoggedOut() { SwingUtilities.invokeLater(this::showNotLoggedIn); }
	public void refresh() { loadData(); }

	private void loadData() {
		if (apiService == null) return;
		if (refreshButton != null) refreshButton.setLoading(true);

		Long accountHash = null;
		if (client != null) {
			long hash = client.getAccountHash();
			if (hash != -1) accountHash = hash;
		}

		apiService.fetchDiaries(accountHash,
			response -> SwingUtilities.invokeLater(() -> {
				if (refreshButton != null) refreshButton.setLoading(false);
				allDiaries = (response != null && response.getData() != null && response.getData().getDiaries() != null)
					? response.getData().getDiaries() : new ArrayList<>();
				buildUI();
				if (selectedDiary != null) {
					allDiaries.stream()
						.filter(d -> d.getId() != null && d.getId().equals(selectedDiary.getId()))
						.findFirst()
						.ifPresent(this::showDiaryDetail);
				}
			}),
			error -> SwingUtilities.invokeLater(() -> {
				if (refreshButton != null) refreshButton.setLoading(false);
				allDiaries = new ArrayList<>();
				buildUI();
			})
		);
	}

	private void showNotLoggedIn() {
		contentPanel.removeAll();
		contentPanel.add(new LoginPrompt("Clan Diaries"));
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void buildUI() {
		contentPanel.removeAll();

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.BACKGROUND);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		header.add(new PanelTitle("CLAN DIARIES"), BorderLayout.WEST);
		refreshButton = new RefreshButton(this::refresh);
		header.add(refreshButton, BorderLayout.EAST);

		contentPanel.add(header);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));

		if (allDiaries.isEmpty()) {
			JLabel empty = new JLabel("No diaries available");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(UIConstants.TEXT_SECONDARY);
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(empty);
		} else {
			JPanel grid = new JPanel();
			grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
			grid.setOpaque(false);
			grid.setAlignmentX(Component.LEFT_ALIGNMENT);

			for (int i = 0; i < allDiaries.size(); i++) {
				JPanel card = createDiaryListCard(allDiaries.get(i));
				card.setAlignmentX(Component.LEFT_ALIGNMENT);
				grid.add(card);
				if (i < allDiaries.size() - 1) grid.add(Box.createRigidArea(new Dimension(0, 12)));
			}
			contentPanel.add(grid);
		}

		contentPanel.add(Box.createVerticalGlue());
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	// ── Diary List Card ──

	private JPanel createDiaryListCard(DiariesResponse.Diary diary) {
		final boolean[] isHovered = {false};

		JPanel card = new JPanel(new BorderLayout(12, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(isHovered[0] ? UIConstants.CARD_HOVER : UIConstants.CARD_BG);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				g2.setColor(getCategoryColor(diary.getCategory()));
				g2.fillRoundRect(0, 4, 4, getHeight() - 8, 2, 2);
				g2.dispose();
			}
		};
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(16, 16, 16, 16));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setOpaque(false);

		JLabel diaryName = new JLabel(diary.getName());
		diaryName.setFont(FontManager.getRunescapeBoldFont());
		diaryName.setForeground(UIConstants.TEXT_PRIMARY);
		diaryName.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel categoryLabel = new JLabel(formatCategoryName(diary.getCategory()));
		categoryLabel.setFont(FontManager.getRunescapeSmallFont());
		categoryLabel.setForeground(UIConstants.TEXT_SECONDARY);
		categoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		leftPanel.add(diaryName);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		leftPanel.add(categoryLabel);

		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setOpaque(false);

		int[] stats = countTasks(diary);
		JLabel statsLabel = new JLabel(stats[0] + "/" + stats[1] + " tasks");
		statsLabel.setFont(FontManager.getRunescapeBoldFont());
		statsLabel.setForeground(UIConstants.ACCENT_GOLD);
		statsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

		JLabel arrowLabel = new JLabel("\u2192");
		arrowLabel.setFont(FontManager.getRunescapeSmallFont());
		arrowLabel.setForeground(UIConstants.TEXT_SECONDARY);
		arrowLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

		rightPanel.add(statsLabel);
		rightPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		rightPanel.add(arrowLabel);

		card.add(leftPanel, BorderLayout.WEST);
		card.add(rightPanel, BorderLayout.EAST);

		card.addMouseListener(new MouseAdapter() {
			public void mouseEntered(MouseEvent e) { isHovered[0] = true; card.repaint(); }
			public void mouseExited(MouseEvent e) { isHovered[0] = false; card.repaint(); }
			public void mouseClicked(MouseEvent e) { showDiaryDetail(diary); }
		});

		return card;
	}

	// ── Diary Detail ──

	private void showDiaryDetail(DiariesResponse.Diary diary) {
		selectedDiary = diary;

		JPanel detailPanel = new JPanel();
		detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
		detailPanel.setBackground(UIConstants.BACKGROUND);
		detailPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

		BackButton backButton = new BackButton("< Back", () -> {
			cardLayout.show(mainContainer, "LIST");
			selectedDiary = null;
		});

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(UIConstants.BACKGROUND);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.add(new PanelTitle(diary.getName()), BorderLayout.CENTER);
		titleRow.add(new RefreshButton(this::loadData), BorderLayout.EAST);

		JPanel headerContainer = new JPanel();
		headerContainer.setLayout(new BoxLayout(headerContainer, BoxLayout.Y_AXIS));
		headerContainer.setBackground(UIConstants.BACKGROUND);
		headerContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerContainer.add(backButton);
		headerContainer.add(Box.createRigidArea(new Dimension(0, 8)));
		headerContainer.add(titleRow);

		detailPanel.add(headerContainer);
		detailPanel.add(Box.createRigidArea(new Dimension(0, 12)));

		JPanel statsPanel = createDiaryStats(diary);
		statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		detailPanel.add(statsPanel);
		detailPanel.add(Box.createRigidArea(new Dimension(0, 16)));

		if (diary.getTiers() != null && !diary.getTiers().isEmpty()) {
			for (int i = 0; i < diary.getTiers().size(); i++) {
				DiariesResponse.Diary.DiaryTier apiTier = diary.getTiers().get(i);
				log.debug("[DiaryStats] tier={}, completionBonus={}, tasksCompleted={}, tasksTotal={}, isComplete={}",
					apiTier.getTier(), apiTier.getCompletionBonus(),
					apiTier.getTasksCompleted(), apiTier.getTasksTotal(), apiTier.getIsComplete());
				DiaryTier tier = parseTier(apiTier.getTier());
				if (tier == null) continue;

				java.util.List<DiariesResponse.Diary.DiaryTask> activeTasks = getActiveTasks(apiTier);
				JPanel tierSection = createTierSection(tier, apiTier, activeTasks, diary.getId() + "_" + i);
				tierSection.setAlignmentX(Component.LEFT_ALIGNMENT);
				detailPanel.add(tierSection);
				if (i < diary.getTiers().size() - 1) detailPanel.add(Box.createRigidArea(new Dimension(0, 8)));
			}
		} else {
			JLabel emptyLabel = new JLabel("No tiers available");
			emptyLabel.setFont(FontManager.getRunescapeSmallFont());
			emptyLabel.setForeground(UIConstants.TEXT_MUTED);
			emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			emptyLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
			detailPanel.add(emptyLabel);
		}

		detailPanel.add(Box.createVerticalGlue());

		// Remove old detail view, keep list view (index 0)
		while (mainContainer.getComponentCount() > 1) mainContainer.remove(1);
		mainContainer.add(wrapScrollable(detailPanel), "DETAIL");
		cardLayout.show(mainContainer, "DETAIL");
	}

	// ── Diary Stats Card ──

	private JPanel createDiaryStats(DiariesResponse.Diary diary) {
		int completedTasks = 0, totalTasks = 0;
		int earnedPoints = 0, totalPoints = diary.getFullCompletionBonus();

		if (diary.getTiers() != null) {
			for (DiariesResponse.Diary.DiaryTier tier : diary.getTiers()) {
				totalPoints += tier.getCompletionBonus();
				Integer tc = tier.getTasksCompleted();
				Integer tt = tier.getTasksTotal();
				if (tt != null) totalTasks += tt;
				if (tc != null) completedTasks += tc;
				if (tier.getIsComplete() != null && tier.getIsComplete()) {
					earnedPoints += tier.getCompletionBonus();
				}
			}
		}
		if (totalTasks > 0 && completedTasks == totalTasks) earnedPoints += diary.getFullCompletionBonus();

		log.debug("[DiaryStats] diary={}, tasks={}/{}, points={}/{}",
			diary.getName(), completedTasks, totalTasks, earnedPoints, totalPoints);

		double progress = totalTasks > 0 ? (double) completedTasks / totalTasks : 0;

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setBorder(new EmptyBorder(12, 12, 12, 12));
		content.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setOpaque(false);

		JLabel progressLabel = new JLabel("Progress");
		progressLabel.setFont(FontManager.getRunescapeBoldFont());
		progressLabel.setForeground(UIConstants.TEXT_PRIMARY);

		JLabel statsLabel = new JLabel(completedTasks + "/" + totalTasks + " tasks \u2022 " + earnedPoints + "/" + totalPoints + " pts");
		statsLabel.setFont(FontManager.getRunescapeSmallFont());
		statsLabel.setForeground(UIConstants.TEXT_SECONDARY);

		headerRow.add(progressLabel, BorderLayout.WEST);
		headerRow.add(statsLabel, BorderLayout.EAST);

		JProgressBar progressBar = new JProgressBar(0, 100);
		progressBar.setValue((int) (progress * 100));
		progressBar.setBackground(UIConstants.PROGRESS_BG);
		progressBar.setForeground(progress >= 1.0 ? UIConstants.SUCCESS_COLOR : UIConstants.TIER_MEDIUM);
		progressBar.setBorderPainted(false);
		progressBar.setPreferredSize(new Dimension(0, 8));
		progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));

		content.add(headerRow);
		content.add(Box.createRigidArea(new Dimension(0, 8)));
		content.add(progressBar);

		return roundedWrapper(content);
	}

	// ── Tier Section ──

	private JPanel createTierSection(DiaryTier tier, DiariesResponse.Diary.DiaryTier apiTier,
									 java.util.List<DiariesResponse.Diary.DiaryTask> activeTasks, String tierKey) {
		JPanel tierSection = new JPanel();
		tierSection.setLayout(new BoxLayout(tierSection, BoxLayout.Y_AXIS));
		tierSection.setOpaque(false);

		final boolean[] expanded = {expandedTiers.contains(tierKey)};
		final JPanel[] headerRef = {createTierHeader(tier, apiTier, activeTasks, expanded[0])};
		headerRef[0].setAlignmentX(Component.LEFT_ALIGNMENT);
		headerRef[0].setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JPanel tasksContainer = new JPanel();
		tasksContainer.setLayout(new BoxLayout(tasksContainer, BoxLayout.Y_AXIS));
		tasksContainer.setOpaque(false);
		tasksContainer.setBorder(new EmptyBorder(8, 0, 0, 0));
		tasksContainer.setVisible(expanded[0]);
		tasksContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (activeTasks.isEmpty()) {
			JLabel empty = new JLabel("No tasks in this tier");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(UIConstants.TEXT_MUTED);
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			empty.setBorder(new EmptyBorder(4, 12, 4, 12));
			tasksContainer.add(empty);
		} else {
			for (DiariesResponse.Diary.DiaryTask task : activeTasks) {
				JPanel taskCard = createTaskCard(task, tier.color);
				taskCard.setAlignmentX(Component.LEFT_ALIGNMENT);
				tasksContainer.add(taskCard);
				tasksContainer.add(Box.createRigidArea(new Dimension(0, 6)));
			}
		}

		tierSection.add(headerRef[0]);
		tierSection.add(tasksContainer);

		headerRef[0].addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				expanded[0] = !expanded[0];
				tasksContainer.setVisible(expanded[0]);
				if (expanded[0]) expandedTiers.add(tierKey);
				else expandedTiers.remove(tierKey);

				tierSection.remove(headerRef[0]);
				headerRef[0] = createTierHeader(tier, apiTier, activeTasks, expanded[0]);
				headerRef[0].setAlignmentX(Component.LEFT_ALIGNMENT);
				headerRef[0].setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				headerRef[0].addMouseListener(this);
				tierSection.add(headerRef[0], 0);
				tierSection.revalidate();
				tierSection.repaint();
			}
		});

		return tierSection;
	}

	private JPanel createTierHeader(DiaryTier tier, DiariesResponse.Diary.DiaryTier apiTier,
									java.util.List<DiariesResponse.Diary.DiaryTask> tasks, boolean isExpanded) {
		long completed = tasks.stream()
			.filter(t -> t.getProgress() != null && t.getProgress().isCompleted()).count();
		int total = tasks.size();
		boolean tierComplete = total > 0 && completed == total;

		JPanel header = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(tierComplete ? UIConstants.TASK_COMPLETE : UIConstants.CARD_BG);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
				g2.setColor(tier.color);
				g2.fillRoundRect(0, 4, 4, getHeight() - 8, 2, 2);
				g2.dispose();
			}
		};
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(12, 12, 12, 12));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setOpaque(false);

		JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		nameRow.setOpaque(false);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel tierName = new JLabel(tier.displayName);
		tierName.setFont(FontManager.getRunescapeBoldFont());
		tierName.setForeground(tier.color);
		nameRow.add(tierName);

		if (tierComplete) {
			ImageIcon icon = assetLoader != null ? assetLoader.getIcon("checkmark.png", 16) : null;
			JLabel checkIcon = icon != null ? new JLabel(icon) : new JLabel(" \u2713");
			if (icon == null) {
				checkIcon.setFont(FontManager.getRunescapeBoldFont());
				checkIcon.setForeground(UIConstants.TIER_EASY);
			}
			nameRow.add(Box.createRigidArea(new Dimension(6, 0)));
			nameRow.add(checkIcon);
		}

		String pointsText = tierComplete
			? apiTier.getCompletionBonus() + " pts earned"
			: apiTier.getCompletionBonus() + " pts available";

		JLabel progressText = new JLabel(completed + "/" + total + " tasks \u2022 " + pointsText);
		progressText.setFont(FontManager.getRunescapeSmallFont());
		progressText.setForeground(UIConstants.TEXT_SECONDARY);
		progressText.setAlignmentX(Component.LEFT_ALIGNMENT);

		leftPanel.add(nameRow);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		leftPanel.add(progressText);

		JLabel expandArrow = new JLabel(isExpanded ? "\u25B2" : "\u25BC");
		expandArrow.setFont(FontManager.getRunescapeSmallFont());
		expandArrow.setForeground(UIConstants.TEXT_SECONDARY);

		header.add(leftPanel, BorderLayout.WEST);
		header.add(expandArrow, BorderLayout.EAST);
		return header;
	}

	// ── Task Card ──

	private JPanel createTaskCard(DiariesResponse.Diary.DiaryTask task, Color tierColor) {
		boolean isComplete = task.getProgress() != null && task.getProgress().isCompleted();

		int current = 0, target = 1;
		double progress = 0.0;
		int progressPercent = 0;

		if (task.getProgress() != null) {
			boolean parsed = false;
			Object progressDataObj = task.getProgress().getProgressData();
			if (progressDataObj != null) {
				try {
					@SuppressWarnings("unchecked")
					Map<String, Object> progressData = (Map<String, Object>) progressDataObj;
					Object currentObj = progressData.get("current");
					Object targetObj = progressData.get("target");
					if (currentObj instanceof Number) current = ((Number) currentObj).intValue();
					if (targetObj instanceof Number) target = ((Number) targetObj).intValue();
					if (target > 0) {
						progress = Math.min(1.0, (double) current / target);
						progressPercent = (int) (progress * 100);
						parsed = true;
					}
				} catch (Exception ignored) {}
			}
			if (!parsed) {
				progressPercent = task.getProgress().getProgressPercent();
				progress = progressPercent / 100.0;
			}
		}

		final double finalProgress = progress;
		final boolean finalComplete = isComplete;

		JPanel card = new JPanel(new BorderLayout(12, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(finalComplete ? UIConstants.TASK_COMPLETE : UIConstants.CARD_BG);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
				if (finalComplete) {
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
					g2.setColor(UIConstants.SUCCESS_COLOR);
					g2.setStroke(new BasicStroke(2));
					g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 6, 6));
				}
				g2.dispose();
			}
		};
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(12, 12, 12, 12));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		mainPanel.setOpaque(false);

		JLabel titleLabel = new JLabel(task.getName());
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(isComplete ? UIConstants.SUCCESS_COLOR : UIConstants.TIER_ELITE);
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JTextArea descArea = new JTextArea(task.getDescription() != null ? task.getDescription() : "No description");
		descArea.setFont(FontManager.getRunescapeSmallFont());
		descArea.setForeground(UIConstants.TEXT_SECONDARY);
		descArea.setEditable(false);
		descArea.setFocusable(false);
		descArea.setWrapStyleWord(true);
		descArea.setLineWrap(true);
		descArea.setOpaque(false);
		descArea.setBorder(null);
		descArea.setAlignmentX(Component.LEFT_ALIGNMENT);

		mainPanel.add(titleLabel);
		mainPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		mainPanel.add(descArea);

		JPanel progressPanel = createProgressBar(finalProgress, current, target, progressPercent, isComplete, tierColor);
		progressPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		mainPanel.add(Box.createRigidArea(new Dimension(0, 6)));
		mainPanel.add(progressPanel);

		if (task.getHint() != null && !task.getHint().isEmpty()) {
			JTextArea hintArea = new JTextArea("Hint: " + task.getHint());
			hintArea.setFont(FontManager.getRunescapeSmallFont());
			hintArea.setForeground(UIConstants.TEXT_MUTED);
			hintArea.setEditable(false);
			hintArea.setFocusable(false);
			hintArea.setWrapStyleWord(true);
			hintArea.setLineWrap(true);
			hintArea.setOpaque(false);
			hintArea.setBorder(null);
			hintArea.setAlignmentX(Component.LEFT_ALIGNMENT);
			mainPanel.add(Box.createRigidArea(new Dimension(0, 4)));
			mainPanel.add(hintArea);
		}

		card.add(mainPanel, BorderLayout.CENTER);
		return card;
	}

	// ── Progress Bar ──

	private JPanel createProgressBar(double progress, int current, int target,
									 int progressPercent, boolean completed, Color tierColor) {
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setOpaque(false);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

		JProgressBar bar = new JProgressBar(0, 100);
		bar.setValue((int) (Math.min(1.0, Math.max(0.0, progress)) * 100));
		bar.setBackground(UIConstants.PROGRESS_BG);
		bar.setForeground(completed ? UIConstants.SUCCESS_COLOR : tierColor);
		bar.setBorderPainted(false);
		bar.setPreferredSize(new Dimension(0, 6));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));

		JLabel label = new JLabel(formatProgress(current, target, progressPercent, completed));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(completed ? UIConstants.SUCCESS_COLOR : UIConstants.TEXT_SECONDARY);

		panel.add(bar, BorderLayout.CENTER);
		panel.add(label, BorderLayout.EAST);
		return panel;
	}

	private String formatProgress(int current, int target, int progressPercent, boolean completed) {
		if (completed) return "Complete!";
		if (target > 1 && current >= 0) {
			if (target >= 1_000_000) return String.format("%.1fM/%.1fM", current / 1_000_000.0, target / 1_000_000.0);
			if (target >= 1_000) {
				String currentStr = current >= 1_000 ? String.format("%.1fK", current / 1_000.0) : String.valueOf(current);
				return currentStr + "/" + String.format("%.1fK", target / 1_000.0);
			}
			return current + "/" + target;
		}
		return progressPercent + "%";
	}

	// ── Helpers ──

	private JScrollPane wrapScrollable(JPanel content) {
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = super.getPreferredSize();
				if (getParent() != null) size.width = getParent().getWidth();
				return size;
			}
		};
		wrapper.setBackground(UIConstants.BACKGROUND);
		wrapper.add(content, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(wrapper);
		scrollPane.setBackground(UIConstants.BACKGROUND);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);
		return scrollPane;
	}

	private JPanel roundedWrapper(JPanel content) {
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(UIConstants.CARD_BG);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				g2.dispose();
			}
		};
		wrapper.setOpaque(false);
		wrapper.add(content, BorderLayout.CENTER);
		return wrapper;
	}

	private java.util.List<DiariesResponse.Diary.DiaryTask> getActiveTasks(DiariesResponse.Diary.DiaryTier tier) {
		java.util.List<DiariesResponse.Diary.DiaryTask> active = new ArrayList<>();
		if (tier.getTasks() != null) {
			for (DiariesResponse.Diary.DiaryTask task : tier.getTasks()) {
				if (!task.isHidden() && task.isActive()) active.add(task);
			}
		}
		return active;
	}

	private int[] countTasks(DiariesResponse.Diary diary) {
		int total = 0, completed = 0;
		if (diary.getTiers() != null) {
			for (DiariesResponse.Diary.DiaryTier tier : diary.getTiers()) {
				if (tier.getTasksTotal() != null) total += tier.getTasksTotal();
				if (tier.getTasksCompleted() != null) completed += tier.getTasksCompleted();
			}
		}
		return new int[]{completed, total};
	}

	private Color getCategoryColor(String category) {
		if (category == null) return UIConstants.ACCENT_GOLD;
		switch (category.toLowerCase()) {
			case "combat": return UIConstants.TYPE_COMBAT;
			case "skilling": return UIConstants.TYPE_SKILLING;
			case "collection": return UIConstants.TYPE_COLLECTION;
			case "social": return UIConstants.TYPE_SOCIAL;
			case "exploration": return UIConstants.TYPE_EXPLORATION;
			case "boss": return UIConstants.TYPE_BOSS;
			default: return UIConstants.ACCENT_GOLD;
		}
	}

	private String formatCategoryName(String category) {
		if (category == null || category.isEmpty()) return "Uncategorized";
		return category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase().replace("_", " ");
	}

	private enum DiaryTier {
		EASY("Easy", UIConstants.TIER_EASY),
		MEDIUM("Medium", UIConstants.TIER_MEDIUM),
		HARD("Hard", UIConstants.TIER_HARD),
		ELITE("Elite", UIConstants.TIER_ELITE),
		MASTER("Master", UIConstants.TIER_MASTER),
		GRANDMASTER("Grandmaster", UIConstants.TIER_GRANDMASTER);

		final String displayName;
		final Color color;

		DiaryTier(String displayName, Color color) {
			this.displayName = displayName;
			this.color = color;
		}
	}

	private DiaryTier parseTier(String tier) {
		if (tier == null) return null;
		try { return DiaryTier.valueOf(tier.toUpperCase()); }
		catch (IllegalArgumentException e) { return null; }
	}
}

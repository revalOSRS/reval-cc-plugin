package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.diaries.DiariesResponse;
import com.revalclan.ui.components.ArrowIcon;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.CheckIcon;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.components.Labels;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.components.RoundedPanel;
import com.revalclan.ui.components.TriangleIcon;
import com.revalclan.ui.components.ScrollWrap;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;

@Slf4j
public class DiaryPanel extends JPanel {

	private RevalApiService apiService;
	private Client client;
	private UIAssetLoader assetLoader;

	private final JPanel contentPanel;
	private final CardLayout cardLayout;
	private final JPanel mainContainer;

	private List<DiariesResponse.Diary> allDiaries = new ArrayList<>();
	private DiariesResponse.Diary selectedDiary = null;
	private final Set<String> expandedTiers = new HashSet<>();
	private RefreshButton refreshButton;

	public DiaryPanel() {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		cardLayout = new CardLayout();
		mainContainer = new JPanel(cardLayout);
		mainContainer.setBackground(UIConstants.BACKGROUND);

		contentPanel = pageBody();
		mainContainer.add(ScrollWrap.of(contentPanel), "LIST");
		add(mainContainer, BorderLayout.CENTER);
		showNotLoggedIn();
	}

	public void init(RevalApiService apiService, Client client, UIAssetLoader assetLoader) {
		this.apiService = apiService;
		this.client = client;
		this.assetLoader = assetLoader;
		showNotLoggedIn();
	}

	public void onLoggedOut() { showNotLoggedIn(); }

	public void refresh() {
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
			contentPanel.add(Labels.small("No diaries available", UIConstants.TEXT_SECONDARY));
		} else {
			JPanel grid = Labels.column();
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
				RoundedPanel.fill(g, this, isHovered[0] ? UIConstants.CARD_HOVER : UIConstants.CARD_BG, 8);
				paintAccentBar(g, this, getCategoryColor(diary.getCategory()));
			}
		};
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(16, 16, 16, 16));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

		JPanel leftPanel = Labels.column();
		leftPanel.add(Labels.bold(diary.getName(), UIConstants.TEXT_PRIMARY));
		leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		leftPanel.add(Labels.small(formatCategoryName(diary.getCategory()), UIConstants.TEXT_SECONDARY));

		JPanel rightPanel = Labels.column();

		int[] stats = countTasks(diary);
		JLabel statsLabel = Labels.bold(stats[0] + "/" + stats[1] + " tasks", UIConstants.ACCENT_GOLD);
		statsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

		JLabel arrowLabel = new JLabel(new ArrowIcon(11, UIConstants.TEXT_SECONDARY));
		arrowLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

		rightPanel.add(statsLabel);
		rightPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		rightPanel.add(arrowLabel);

		card.add(leftPanel, BorderLayout.WEST);
		card.add(rightPanel, BorderLayout.EAST);

		// onPress also sets the hand cursor
		Clickable.onPress(card, () -> showDiaryDetail(diary), h -> isHovered[0] = h);

		return card;
	}

	// ── Diary Detail ──

	private void showDiaryDetail(DiariesResponse.Diary diary) {
		selectedDiary = diary;

		JPanel detailPanel = pageBody();

		BackButton backButton = new BackButton("< Back", () -> {
			cardLayout.show(mainContainer, "LIST");
			selectedDiary = null;
		});

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(UIConstants.BACKGROUND);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.add(new PanelTitle(diary.getName()), BorderLayout.CENTER);
		titleRow.add(new RefreshButton(this::refresh), BorderLayout.EAST);

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

				List<DiariesResponse.Diary.DiaryTask> activeTasks = getActiveTasks(apiTier);
				JPanel tierSection = createTierSection(tier, apiTier, activeTasks, diary.getId() + "_" + i);
				tierSection.setAlignmentX(Component.LEFT_ALIGNMENT);
				detailPanel.add(tierSection);
				if (i < diary.getTiers().size() - 1) detailPanel.add(Box.createRigidArea(new Dimension(0, 8)));
			}
		} else {
			JLabel emptyLabel = Labels.small("No tiers available", UIConstants.TEXT_MUTED);
			emptyLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
			detailPanel.add(emptyLabel);
		}

		detailPanel.add(Box.createVerticalGlue());

		// Remove old detail view, keep list view (index 0)
		while (mainContainer.getComponentCount() > 1) mainContainer.remove(1);
		mainContainer.add(ScrollWrap.of(detailPanel), "DETAIL");
		cardLayout.show(mainContainer, "DETAIL");
	}

	// ── Diary Stats Card ──

	private JPanel createDiaryStats(DiariesResponse.Diary diary) {
		int[] tasks = countTasks(diary);
		int completedTasks = tasks[0], totalTasks = tasks[1];
		int earnedPoints = 0, totalPoints = diary.getFullCompletionBonus();

		if (diary.getTiers() != null) {
			for (DiariesResponse.Diary.DiaryTier tier : diary.getTiers()) {
				totalPoints += tier.getCompletionBonus();
				if (Boolean.TRUE.equals(tier.getIsComplete())) {
					earnedPoints += tier.getCompletionBonus();
				}
			}
		}
		if (totalTasks > 0 && completedTasks == totalTasks) earnedPoints += diary.getFullCompletionBonus();

		log.debug("[DiaryStats] diary={}, tasks={}/{}, points={}/{}",
			diary.getName(), completedTasks, totalTasks, earnedPoints, totalPoints);

		double progress = totalTasks > 0 ? (double) completedTasks / totalTasks : 0;

		JPanel content = Labels.column();
		content.setBorder(new EmptyBorder(12, 12, 12, 12));
		content.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setOpaque(false);
		headerRow.add(Labels.sectionTitle("Progress"), BorderLayout.WEST);
		headerRow.add(Labels.small(completedTasks + "/" + totalTasks + " tasks - " + earnedPoints + "/" + totalPoints + " pts",
			UIConstants.TEXT_SECONDARY), BorderLayout.EAST);

		content.add(headerRow);
		content.add(Box.createRigidArea(new Dimension(0, 8)));
		content.add(progressBar((int) (progress * 100), progress >= 1.0 ? UIConstants.SUCCESS_COLOR : UIConstants.TIER_MEDIUM, 8));

		return roundedWrapper(content);
	}

	// ── Tier Section ──

	private JPanel createTierSection(DiaryTier tier, DiariesResponse.Diary.DiaryTier apiTier,
									 List<DiariesResponse.Diary.DiaryTask> activeTasks, String tierKey) {
		JPanel tierSection = Labels.column();

		final boolean[] expanded = {expandedTiers.contains(tierKey)};
		final JPanel[] headerRef = {createTierHeader(tier, apiTier, activeTasks, expanded[0])};

		JPanel tasksContainer = Labels.column();
		tasksContainer.setBorder(new EmptyBorder(8, 0, 0, 0));
		tasksContainer.setVisible(expanded[0]);
		tasksContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (activeTasks.isEmpty()) {
			JLabel empty = Labels.small("No tasks in this tier", UIConstants.TEXT_MUTED);
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

		Runnable[] toggle = new Runnable[1];
		toggle[0] = () -> {
			expanded[0] = !expanded[0];
			tasksContainer.setVisible(expanded[0]);
			if (expanded[0]) expandedTiers.add(tierKey);
			else expandedTiers.remove(tierKey);

			tierSection.remove(headerRef[0]);
			headerRef[0] = createTierHeader(tier, apiTier, activeTasks, expanded[0]);
			Clickable.onPress(headerRef[0], toggle[0]);
			tierSection.add(headerRef[0], 0);
			tierSection.revalidate();
			tierSection.repaint();
		};
		// onPress also sets the hand cursor
		Clickable.onPress(headerRef[0], toggle[0]);

		return tierSection;
	}

	private JPanel createTierHeader(DiaryTier tier, DiariesResponse.Diary.DiaryTier apiTier,
									List<DiariesResponse.Diary.DiaryTask> tasks, boolean isExpanded) {
		long completed = tasks.stream()
			.filter(t -> t.getProgress() != null && t.getProgress().isCompleted()).count();
		int total = tasks.size();
		boolean tierComplete = total > 0 && completed == total;

		JPanel header = new JPanel(new BorderLayout(8, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				RoundedPanel.fill(g, this, tierComplete ? UIConstants.TASK_COMPLETE : UIConstants.CARD_BG, 6);
				paintAccentBar(g, this, tier.color);
			}
		};
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(12, 12, 12, 12));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel leftPanel = Labels.column();

		JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		nameRow.setOpaque(false);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameRow.add(Labels.bold(tier.displayName, tier.color));

		if (tierComplete) {
			ImageIcon icon = assetLoader != null ? assetLoader.getIcon("checkmark.png", 16) : null;
			JLabel checkIcon = new JLabel(icon != null ? icon : new CheckIcon(14, UIConstants.TIER_EASY));
			nameRow.add(Box.createRigidArea(new Dimension(6, 0)));
			nameRow.add(checkIcon);
		}

		String pointsText = tierComplete
			? apiTier.getCompletionBonus() + " pts earned"
			: apiTier.getCompletionBonus() + " pts available";

		leftPanel.add(nameRow);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		leftPanel.add(Labels.small(completed + "/" + total + " tasks - " + pointsText, UIConstants.TEXT_SECONDARY));

		JLabel expandArrow = new JLabel(new TriangleIcon(!isExpanded, 9, UIConstants.TEXT_SECONDARY));

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

		JPanel card = new JPanel(new BorderLayout(12, 0)) {
			@Override
			protected void paintComponent(Graphics g) {
				RoundedPanel.fill(g, this, isComplete ? UIConstants.TASK_COMPLETE : UIConstants.CARD_BG, 6);
				if (isComplete) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
					g2.setColor(UIConstants.SUCCESS_COLOR);
					g2.setStroke(new BasicStroke(2));
					g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 6, 6));
					g2.dispose();
				}
			}
		};
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(12, 12, 12, 12));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		JPanel mainPanel = Labels.column();
		mainPanel.add(Labels.bold(task.getName(), isComplete ? UIConstants.SUCCESS_COLOR : UIConstants.TIER_ELITE));
		mainPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		mainPanel.add(textArea(task.getDescription() != null ? task.getDescription() : "No description", UIConstants.TEXT_SECONDARY));
		mainPanel.add(Box.createRigidArea(new Dimension(0, 6)));
		mainPanel.add(createProgressBar(progress, current, target, progressPercent, isComplete, tierColor));

		if (task.getHint() != null && !task.getHint().isEmpty()) {
			mainPanel.add(Box.createRigidArea(new Dimension(0, 4)));
			mainPanel.add(textArea("Hint: " + task.getHint(), UIConstants.TEXT_MUTED));
		}

		card.add(mainPanel, BorderLayout.CENTER);
		return card;
	}

	// ── Progress Bar ──

	private JPanel createProgressBar(double progress, int current, int target,
									 int progressPercent, boolean completed, Color tierColor) {
		JPanel panel = Labels.row();
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		panel.add(progressBar((int) (Math.min(1.0, Math.max(0.0, progress)) * 100),
			completed ? UIConstants.SUCCESS_COLOR : tierColor, 6), BorderLayout.CENTER);
		panel.add(Labels.small(formatProgress(current, target, progressPercent, completed),
			completed ? UIConstants.SUCCESS_COLOR : UIConstants.TEXT_SECONDARY), BorderLayout.EAST);
		return panel;
	}

	private static JProgressBar progressBar(int value, Color color, int height) {
		JProgressBar bar = new JProgressBar(0, 100);
		bar.setValue(value);
		bar.setBackground(UIConstants.PROGRESS_BG);
		bar.setForeground(color);
		bar.setBorderPainted(false);
		bar.setPreferredSize(new Dimension(0, height));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return bar;
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

	/** Padded vertical body of the list and detail views. */
	private static JPanel pageBody() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(UIConstants.BACKGROUND);
		panel.setBorder(new EmptyBorder(8, 8, 8, 8));
		return panel;
	}

	private JPanel roundedWrapper(JPanel content) {
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				RoundedPanel.fill(g, this, UIConstants.CARD_BG, 8);
			}
		};
		wrapper.setOpaque(false);
		wrapper.add(content, BorderLayout.CENTER);
		return wrapper;
	}

	/** Thin rounded accent bar down a card's left edge. */
	private static void paintAccentBar(Graphics g, Component c, Color color) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(color);
		g2.fillRoundRect(0, 4, 4, c.getHeight() - 8, 2, 2);
		g2.dispose();
	}

	private static JTextArea textArea(String text, Color color) {
		JTextArea area = new JTextArea(text);
		area.setFont(FontManager.getRunescapeSmallFont());
		area.setForeground(color);
		area.setEditable(false);
		area.setFocusable(false);
		area.setWrapStyleWord(true);
		area.setLineWrap(true);
		area.setOpaque(false);
		area.setBorder(null);
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		return area;
	}

	private List<DiariesResponse.Diary.DiaryTask> getActiveTasks(DiariesResponse.Diary.DiaryTier tier) {
		List<DiariesResponse.Diary.DiaryTask> active = new ArrayList<>();
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

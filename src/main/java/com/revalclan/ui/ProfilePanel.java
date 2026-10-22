package com.revalclan.ui;

import com.revalclan.RevalClanConfig;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.components.ChecklistItem;
import com.revalclan.ui.components.Labels;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.components.RoundedPanel;
import com.revalclan.ui.components.ScrollWrap;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.ui.components.ArrowIcon;
import com.revalclan.ui.components.BlockButton;
import com.revalclan.ui.components.Clickable;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.NumberFmt;
import com.revalclan.util.UIAssetLoader;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class ProfilePanel extends JPanel {
	private final JPanel contentPanel;
	private final GridBagConstraints gbc;
	private int gridY = 0;

	private RevalApiService apiService;
	private Client client;
	private UIAssetLoader assetLoader;
	private RevalClanConfig config;
	private ItemManager itemManager;
	private ClanRankIconResolver rankIconResolver;
	private Runnable onOpenRanks;
	private Runnable onSyncGuide;
	private Consumer<AccountResponse.AccountData> onAccountLoaded;

	private AccountResponse.AccountData currentAccount;
	private PointsAlbumWindow albumWindow;
	private List<PointsResponse.Rank> ranks;
	private PointsResponse.PointsData pointsData;
	private List<AccountResponse.PointsLogEntry> pointsLog;
	private boolean isLoading = false;

	public ProfilePanel() {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		contentPanel = new JPanel(new GridBagLayout());
		contentPanel.setBackground(UIConstants.BACKGROUND);
		contentPanel.setBorder(new EmptyBorder(4, 2, 4, 2));

		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(2, 0, 2, 0);

		JScrollPane scrollPane = ScrollWrap.of(contentPanel);

		showNotLoggedIn();
		add(scrollPane, BorderLayout.CENTER);
	}

	private void addComponent(JComponent comp) {
		gbc.gridy = gridY++;
		contentPanel.add(comp, gbc);
	}

	private void addSpacing(int height) {
		gbc.gridy = gridY++;
		contentPanel.add(Box.createVerticalStrut(height), gbc);
	}

	/** A profile section followed by the standard gap. */
	private void addSection(JComponent section) {
		addComponent(section);
		addSpacing(6);
	}

	public void init(RevalApiService apiService, Client client, UIAssetLoader assetLoader, RevalClanConfig config,
					 ItemManager itemManager, ClanRankIconResolver rankIconResolver) {
		this.apiService = apiService;
		this.client = client;
		this.assetLoader = assetLoader;
		this.config = config;
		this.itemManager = itemManager;
		this.rankIconResolver = rankIconResolver;
	}

	/** Where the rank-up bar navigates (the Ranking side-panel view) */
	public void setOnOpenRanks(Runnable onOpenRanks) {
		this.onOpenRanks = onOpenRanks;
	}

	/** Arms the in-game collection log sync guide (points album button) */
	public void setOnSyncGuide(Runnable onSyncGuide) {
		this.onSyncGuide = onSyncGuide;
	}

	public void setOnAccountLoaded(Consumer<AccountResponse.AccountData> callback) {
		this.onAccountLoaded = callback;
	}

	private void fetchRanks() {
		if (apiService == null) return;
		apiService.fetchPoints(
			response -> {
				if (response.getData() != null) {
					pointsData = response.getData();
					if (response.getData().getRanks() != null) {
						ranks = response.getData().getRanks();
					}
					if (currentAccount != null) {
						SwingUtilities.invokeLater(this::buildProfile);
					}
				}
			},
			error -> {}
		);
	}

	public void onLoggedOut() {
		showNotLoggedIn();
	}

	public void loadCurrentAccount() {
		loadCurrentAccount(false);
	}

	public void loadCurrentAccount(boolean retry) {
		if (client == null || apiService == null) {
			showNotLoggedIn();
			return;
		}

		long accountHash = client.getAccountHash();
		if (accountHash == -1) {
			if (retry) {
				showError("Not logged into RuneLite account");
			} else {
				Timer timer = new Timer(2000, e -> loadCurrentAccount(true));
				timer.setRepeats(false);
				timer.start();
				showLoading();
			}
			return;
		}
		loadAccount(accountHash);
	}

	public void loadAccount(long accountHash) {
		if (isLoading) return;
		isLoading = true;
		showLoading();
		if (pointsData == null) fetchRanks();

		apiService.fetchAccount(accountHash,
			response -> {
				isLoading = false;
				SwingUtilities.invokeLater(() -> {
					currentAccount = response.getData();
					if (currentAccount != null) {
						pointsLog = currentAccount.getPointsLog();
						if (onAccountLoaded != null) onAccountLoaded.accept(currentAccount);
					}
					if (pointsData != null) buildProfile();
				});
			},
			error -> {
				isLoading = false;
				SwingUtilities.invokeLater(() -> showError(error.getMessage() != null ? error.getMessage() : "Failed to fetch account data"));
			}
		);
	}

	public void loadAccountById(int osrsAccountId) {
		if (isLoading) return;
		isLoading = true;
		showLoading();
		if (pointsData == null) fetchRanks();

		apiService.fetchAccountById(osrsAccountId,
			response -> {
				isLoading = false;
				SwingUtilities.invokeLater(() -> {
					currentAccount = response.getData();
					if (currentAccount != null) pointsLog = currentAccount.getPointsLog();
					if (pointsData != null) buildProfile();
				});
			},
			error -> {
				isLoading = false;
				SwingUtilities.invokeLater(() -> showError(error.getMessage() != null ? error.getMessage() : "Player not found"));
			}
		);
	}

	public void refresh() {
		if (apiService != null && client != null) {
			apiService.clearAccountCache();
			loadCurrentAccount();
		}
	}

	/**
	 * Rebuilds the profile UI from cached data without re-fetching from the API.
	 * Used when config changes (e.g. hide completed items toggle).
	 */
	public void rebuild() {
		if (currentAccount != null && pointsData != null) {
			SwingUtilities.invokeLater(this::buildProfile);
		}
	}

	public boolean isAccountLoaded() {
		return currentAccount != null && currentAccount.getOsrsAccount() != null;
	}

	private void showNotLoggedIn() {
		contentPanel.removeAll();
		gridY = 0;
		addComponent(new LoginPrompt("Profile"));
		revalidateAndRepaint();
	}

	private void showLoading() {
		contentPanel.removeAll();
		gridY = 0;

		JPanel placeholder = createCenteredPanel();
		placeholder.setBorder(new EmptyBorder(50, 20, 20, 20));
		placeholder.add(centerAligned(Labels.small("Loading profile...", UIConstants.TEXT_SECONDARY)));
		addComponent(placeholder);
		revalidateAndRepaint();
	}

	private void showError(String message) {
		contentPanel.removeAll();
		gridY = 0;

		JPanel placeholder = createCenteredPanel();
		placeholder.setBorder(new EmptyBorder(30, 20, 20, 20));
		placeholder.add(centerAligned(Labels.bold("!", UIConstants.ERROR_COLOR)));
		placeholder.add(Box.createRigidArea(new Dimension(0, 6)));
		placeholder.add(centerAligned(Labels.small(message, UIConstants.ERROR_COLOR)));
		placeholder.add(Box.createRigidArea(new Dimension(0, 6)));
		placeholder.add(centerAligned(Labels.small("Make sure you're in the Reval clan", UIConstants.TEXT_SECONDARY)));

		addComponent(placeholder);
		revalidateAndRepaint();
	}

	private void buildProfile() {
		contentPanel.removeAll();
		gridY = 0;

		if (currentAccount == null || currentAccount.getOsrsAccount() == null) {
			showError("No profile data");
			return;
		}

		AccountResponse.OsrsAccount account = currentAccount.getOsrsAccount();

		addSection(buildHeaderSection(account));

		if (currentAccount.getPointsBreakdown() != null) {
			addSection(buildPointsSection(currentAccount.getPointsBreakdown()));
		}

		addSection(buildStatsSection(account));
		addSection(buildMilestonesSection(currentAccount.getMilestones()));
		addSection(buildCombatAchievementsSection());
		addSection(buildCollectionLogSection());
		addSection(buildItemChecklistSection("Monkey Backpacks", "MONKEY_BACKPACKS"));
		addSection(buildItemChecklistSection("ToA Capes", "TOA_CAPES"));
		addSection(buildItemChecklistSection("ToB Capes", "TOB_CAPES"));
		addComponent(buildItemChecklistSection("CM Capes", "CM_CAPES"));
		addSpacing(16);

		// Add filler to absorb extra space and keep content at top
		gbc.gridy = gridY;
		gbc.weighty = 1.0;
		contentPanel.add(Box.createGlue(), gbc);

		revalidateAndRepaint();
	}

	private JPanel buildHeaderSection(AccountResponse.OsrsAccount account) {
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(UIConstants.CARD_BG);
		header.setBorder(new EmptyBorder(10, 12, 10, 12));

		// Top row: rank icon + nickname on the left, points on the right
		JPanel topRow = new JPanel(new BorderLayout(8, 0));
		topRow.setOpaque(false);
		topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		namePanel.setOpaque(false);

		JLabel rankIcon = new JLabel();
		rankIcon.setPreferredSize(new Dimension(20, 20));
		rankIcon.setToolTipText(getRankDisplayName(account.getClanRank()));
		if (rankIconResolver != null && account.getClanRank() != null) {
			rankIconResolver.apply(account.getClanRank(), rankIcon, 20);
		}
		namePanel.add(rankIcon);
		namePanel.add(Labels.bold(account.getOsrsNickname(), UIConstants.TEXT_PRIMARY));

		JPanel pointsDisplay = Labels.column();

		int points = account.getActivityPoints() != null ? account.getActivityPoints() : 0;
		JLabel pointsValue = Labels.bold(NumberFmt.group(points), UIConstants.ACCENT_GOLD);
		pointsValue.setAlignmentX(Component.RIGHT_ALIGNMENT);

		JLabel pointsLabel = Labels.small("Reval Points", UIConstants.TEXT_SECONDARY);
		pointsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

		pointsDisplay.add(pointsValue);
		pointsDisplay.add(pointsLabel);

		topRow.add(namePanel, BorderLayout.WEST);
		topRow.add(pointsDisplay, BorderLayout.EAST);
		header.add(topRow);

		JPanel rankProgress = buildRankProgressBar(account);
		if (rankProgress != null) {
			header.add(Box.createRigidArea(new Dimension(0, 8)));
			rankProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
			header.add(rankProgress);
		}

		// Leaderboard profiles have no client to refresh from
		if (client != null) {
			header.add(Box.createRigidArea(new Dimension(0, 8)));
			JButton refreshButton = buildRefreshProfileButton();
			refreshButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			header.add(refreshButton);
		}

		if (onSyncGuide != null) {
			header.add(Box.createRigidArea(new Dimension(0, 6)));
			JButton syncButton = buildSyncGuideButton();
			syncButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			header.add(syncButton);
		}

		return wrapInRoundedPanel(header);
	}

	/** Arms the in-game collection log sync guide */
	private JButton buildSyncGuideButton() {
		JButton btn = new BlockButton("Sync missing points", UIConstants.TEXT_SECONDARY, 24);
		btn.setToolTipText("Highlights the Sync Reval button in your in-game Collection Log");
		btn.addActionListener(e -> {
			if (onSyncGuide != null) onSyncGuide.run();
			btn.setText("Check your Collection Log in-game");
			Timer reset = new Timer(4000, ev -> btn.setText("Sync missing points"));
			reset.setRepeats(false);
			reset.start();
		});
		return btn;
	}

	/** Full-width block button so the refresh action stands out */
	private JButton buildRefreshProfileButton() {
		JButton btn = new BlockButton("Refresh Profile", UIConstants.panelAccent(), 26);
		btn.addActionListener(e -> {
			btn.setText("Refreshing...");
			btn.setEnabled(false);
			refresh();
		});
		return btn;
	}

	private JPanel buildRankProgressBar(AccountResponse.OsrsAccount account) {
		if (ranks == null || ranks.isEmpty()) return null;

		int currentPoints = account.getActivityPoints() != null ? account.getActivityPoints() : 0;
		String currentRank = account.getClanRank();

		PointsResponse.Rank nextRank = null;
		int previousRankPoints = 0;

		List<PointsResponse.Rank> sortedRanks = new ArrayList<>(ranks);
		sortedRanks.sort(Comparator.comparingInt(PointsResponse.Rank::getPointsRequired));

		for (int i = 0; i < sortedRanks.size(); i++) {
			PointsResponse.Rank rank = sortedRanks.get(i);
			if (rank.getName().equalsIgnoreCase(currentRank) ||
				(rank.getDisplayName() != null && rank.getDisplayName().equalsIgnoreCase(currentRank))) {
				previousRankPoints = rank.getPointsRequired();
				if (i + 1 < sortedRanks.size()) nextRank = sortedRanks.get(i + 1);
				break;
			}
		}

		if (nextRank == null) {
			for (PointsResponse.Rank rank : sortedRanks) {
				if (rank.getPointsRequired() > currentPoints) {
					nextRank = rank;
					break;
				}
				previousRankPoints = rank.getPointsRequired();
			}
		}

		if (nextRank == null) {
			JPanel maxRank = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			maxRank.setOpaque(false);
			maxRank.add(Labels.small("Max Rank!", UIConstants.ACCENT_GOLD));
			return maxRank;
		}

		int pointsNeeded = nextRank.getPointsRequired() - previousRankPoints;
		int pointsProgress = currentPoints - previousRankPoints;
		double progress = Math.min(1.0, Math.max(0.0, pointsNeeded > 0 ? (double) pointsProgress / pointsNeeded : 0));
		int pointsRemaining = nextRank.getPointsRequired() - currentPoints;
		boolean needsRankUp = pointsRemaining < 0;

		// Clickable sub-panel: hover highlight, opens the ranks page on the website
		final boolean[] hovered = { false };
		JPanel progressPanel = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				RoundedPanel.fill(g, this, hovered[0] ? UIConstants.CARD_HOVER : UIConstants.BACKGROUND, 6);
				super.paintComponent(g);
			}
		};
		progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
		progressPanel.setOpaque(false);
		progressPanel.setBorder(new EmptyBorder(6, 8, 8, 8));
		if (onOpenRanks != null) {
			progressPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			progressPanel.setToolTipText("View all ranks");
		}

		JPanel labelRow = new JPanel(new BorderLayout(4, 0));
		labelRow.setOpaque(false);
		labelRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel labelLeft = Labels.flow();
		labelLeft.add(Labels.small(NumberFmt.group(pointsRemaining) + " pts to", UIConstants.TEXT_SECONDARY));

		JLabel nextRankIcon = new JLabel();
		nextRankIcon.setPreferredSize(new Dimension(14, 14));
		if (rankIconResolver != null) {
			rankIconResolver.apply(nextRank.getName(), nextRankIcon, 14);
		}
		labelLeft.add(nextRankIcon);

		String nextRankName = nextRank.getDisplayName() != null ? nextRank.getDisplayName() : nextRank.getName();
		labelLeft.add(Labels.small(nextRankName, UIConstants.ACCENT_GOLD));

		if (needsRankUp) {
			ImageIcon infoIcon = assetLoader != null ? assetLoader.getIcon("info.png", 12) : null;
			if (infoIcon != null) {
				JLabel infoIconLabel = new JLabel(infoIcon);
				infoIconLabel.setToolTipText("Waiting for a staff member to give you the correct rank");
				labelLeft.add(infoIconLabel);
			}
		}

		labelRow.add(labelLeft, BorderLayout.WEST);
		labelRow.add(new JLabel(new ArrowIcon(11, UIConstants.TEXT_MUTED)), BorderLayout.EAST);

		JProgressBar bar = new JProgressBar(0, 100);
		bar.setValue((int) (progress * 100));
		bar.setBackground(UIConstants.PROGRESS_BG);
		bar.setForeground(UIConstants.panelAccent());
		bar.setBorderPainted(false);
		bar.setPreferredSize(new Dimension(100, 5));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);

		progressPanel.add(labelRow);
		progressPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		progressPanel.add(bar);
		progressPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, progressPanel.getPreferredSize().height));

		if (onOpenRanks != null) {
			Clickable.onPress(progressPanel, onOpenRanks, h -> hovered[0] = h);
		}

		return progressPanel;
	}

	private JPanel buildPointsSection(AccountResponse.PointsBreakdown breakdown) {
		JPanel section = Labels.column();

		JPanel labelHeader = new JPanel(new BorderLayout());
		labelHeader.setOpaque(false);
		labelHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
		labelHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		labelHeader.add(Labels.bold("Points from:", UIConstants.ACCENT_GOLD), BorderLayout.WEST);

		section.add(labelHeader);
		section.add(Box.createRigidArea(new Dimension(0, 6)));

		section.add(statRow(
			createStatCard(formatNumber(breakdown.getDrops()), "Drops", UIConstants.ACCENT_GREEN, "drop"),
			createStatCard(formatNumber(breakdown.getPets()), "Pets", UIConstants.ACCENT_PURPLE, "pet"),
			createStatCard(formatNumber(breakdown.getMilestones()), "Milestones", UIConstants.ACCENT_BLUE, "milestone")));
		section.add(Box.createRigidArea(new Dimension(0, 4)));
		section.add(statRow(
			createStatCard(formatNumber(breakdown.getRevalDiaries()), "Diaries", UIConstants.ACCENT_GOLD, "reval_diary"),
			createStatCard(formatNumber(breakdown.getRevalChallenges()), "Challenges", UIConstants.ACCENT_GREEN, "reval_challenge"),
			createStatCard(formatNumber(breakdown.getEvents()), "Events", UIConstants.ACCENT_BLUE, "event")));
		section.add(Box.createRigidArea(new Dimension(0, 4)));

		long miscPoints = breakdown.getTotal()
			- breakdown.getDrops() - breakdown.getPets() - breakdown.getMilestones()
			- breakdown.getEvents() - breakdown.getRevalDiaries() - breakdown.getRevalChallenges();
		section.add(statRow(createStatCard(formatNumber(miscPoints), "Misc", UIConstants.TEXT_SECONDARY, "misc")));

		return section;
	}

	private JPanel buildStatsSection(AccountResponse.OsrsAccount account) {
		return statRow(
			createStatCard(formatDecimal(account.getEhp() != null ? account.getEhp() : 0.0), "EHP", UIConstants.ACCENT_GREEN, null),
			createStatCard(formatDecimal(account.getEhb() != null ? account.getEhb() : 0.0), "EHB", UIConstants.ACCENT_BLUE, null));
	}

	private JPanel buildMilestonesSection(List<AccountResponse.Milestone> completedMilestones) {
		boolean hideCompleted = config != null && config.hideCompletedItems();
		JPanel list = Labels.column();
		List<PointsResponse.PointSource> definitions = pointSources("MILESTONES");

		int itemCount = 0;
		if (definitions.isEmpty() && completedMilestones != null) {
			if (!hideCompleted) {
				for (AccountResponse.Milestone m : completedMilestones) {
					addChecklistItem(list, m.getDescription(), true, m.getPointsAwarded());
					itemCount++;
				}
			}
		} else {
			itemCount = addMilestoneItems(list, definitions, hideCompleted);
		}
		return checklistCard("Milestones", list, itemCount == 0);
	}

	private JPanel buildCombatAchievementsSection() {
		return buildTierSection("Combat Achievements", "COMBAT_ACHIEVEMENTS",
			currentAccount != null ? currentAccount.getCombatAchievementPoints() : 0);
	}

	private JPanel buildCollectionLogSection() {
		return buildTierSection("Collection Log", "COLLECTION_LOG",
			currentAccount != null ? currentAccount.getCollectionLogUniqueObtained() : 0);
	}

	private JPanel buildItemChecklistSection(String titleText, String sourceKey) {
		boolean hideCompleted = config != null && config.hideCompletedItems();
		JPanel list = Labels.column();
		List<PointsResponse.PointSource> definitions = pointSources(sourceKey);

		if (definitions.isEmpty()) {
			list.add(Labels.small("No items available", UIConstants.TEXT_SECONDARY));
			return checklistCard(titleText, list, false);
		}
		int itemCount = addMilestoneItems(list, definitions, hideCompleted);
		return checklistCard(titleText, list, itemCount == 0);
	}

	private JPanel buildTierSection(String titleText, String sourceKey, Integer currentProgress) {
		boolean hideCompleted = config != null && config.hideCompletedItems();
		JPanel list = Labels.column();

		int progress = currentProgress != null ? currentProgress : 0;
		List<PointsResponse.PointSource> tiers = pointSources(sourceKey);
		tiers.sort(Comparator.comparingInt(t -> t.getThreshold() != null ? t.getThreshold() : 0));

		if (tiers.isEmpty()) {
			list.add(Labels.small("No tiers available", UIConstants.TEXT_SECONDARY));
			return checklistCard(titleText, list, false);
		}
		int itemCount = 0;
		int previousPoints = 0;
		for (PointsResponse.PointSource tier : tiers) {
			boolean completed = tier.getThreshold() != null && progress >= tier.getThreshold();
			// Tier points are running totals; reaching a tier only adds the gap to the tier below
			int addedPoints = Math.max(0, tier.getPointsValue() - previousPoints);
			previousPoints = Math.max(previousPoints, tier.getPointsValue());
			if (hideCompleted && completed) continue;
			String desc = tier.getDescription() != null ? tier.getDescription() : tier.getName();
			addChecklistItem(list, desc, completed, addedPoints);
			itemCount++;
		}
		return checklistCard(titleText, list, itemCount == 0);
	}

	/** One category of the points config, or an empty list. */
	private List<PointsResponse.PointSource> pointSources(String key) {
		List<PointsResponse.PointSource> sources = pointsData != null && pointsData.getPointSources() != null
			? pointsData.getPointSources().get(key) : null;
		return sources != null ? sources : new ArrayList<>();
	}

	/** Adds a row per point source, completed once the account has achieved that milestone. Returns the rows added. */
	private int addMilestoneItems(JPanel list, List<PointsResponse.PointSource> definitions, boolean hideCompleted) {
		Map<String, AccountResponse.Milestone> completedMap = new HashMap<>();
		if (currentAccount != null && currentAccount.getMilestones() != null) {
			for (AccountResponse.Milestone m : currentAccount.getMilestones()) {
				if (m.getType() != null) completedMap.put(m.getType(), m);
			}
		}

		int itemCount = 0;
		for (PointsResponse.PointSource def : definitions) {
			AccountResponse.Milestone completed = completedMap.get(def.getId());
			boolean isCompleted = completed != null && completed.getAchievedAt() != null && !completed.getAchievedAt().isEmpty();
			if (hideCompleted && isCompleted) continue;
			String desc = def.getDescription() != null ? def.getDescription() : def.getName();
			Integer pts = isCompleted && completed.getPointsAwarded() != null ? completed.getPointsAwarded() : def.getPointsValue();
			addChecklistItem(list, desc, isCompleted, pts);
			itemCount++;
		}
		return itemCount;
	}

	private void addChecklistItem(JPanel list, String desc, boolean completed, Integer points) {
		list.add(new ChecklistItem(desc, completed, points, assetLoader));
		list.add(Box.createRigidArea(new Dimension(0, 4)));
	}

	/** Rounded card with the title above the checklist; allHidden (no rows were added) appends the "All ... completed!" note. */
	private JPanel checklistCard(String titleText, JPanel list, boolean allHidden) {
		if (allHidden) list.add(Labels.small("All " + titleText.toLowerCase() + " completed!", UIConstants.ACCENT_GREEN));

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(UIConstants.CARD_BG);
		wrapper.setBorder(new EmptyBorder(10, 12, 10, 12));

		JLabel title = Labels.sectionTitle(titleText);
		title.setBorder(new EmptyBorder(0, 0, 8, 0));

		wrapper.add(title, BorderLayout.NORTH);
		wrapper.add(list, BorderLayout.CENTER);
		return wrapInRoundedPanel(wrapper);
	}

	private JPanel createStatCard(String value, String label, Color accentColor, String sourceType) {
		Color valueColor = accentColor;
		if (UIConstants.isHalloweenTheme() && !UIConstants.TEXT_SECONDARY.equals(accentColor)) {
			valueColor = sourceType != null ? UIConstants.HALLOWEEN_ACCENT : UIConstants.HALLOWEEN_STAT_GREEN;
		}
		JPanel card = new JPanel() {
			private boolean hovered = false;
			{
				setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
				setOpaque(false);
				setBorder(new EmptyBorder(8, 8, 8, 8));

				if (sourceType != null) {
					Clickable.onPress(this, () -> showPointsBreakdown(sourceType), h -> hovered = h);
				}
			}
			@Override
			protected void paintComponent(Graphics g) {
				paintCard(g, this, hovered ? UIConstants.CARD_BG.brighter() : UIConstants.CARD_BG, 6);
			}
		};

		card.add(centerAligned(Labels.bold(value, valueColor)));
		card.add(centerAligned(Labels.small(label, UIConstants.TEXT_SECONDARY)));
		return card;
	}

	/** Equal-width stat cards side by side. */
	private static JPanel statRow(JPanel... cards) {
		JPanel row = new JPanel(new GridLayout(1, cards.length, 4, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (JPanel card : cards) row.add(card);
		return row;
	}

	private JPanel createCenteredPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(UIConstants.BACKGROUND);
		return panel;
	}

	private JPanel wrapInRoundedPanel(JPanel content) {
		if (UIConstants.isHalloweenTheme()) content.setOpaque(false);
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				paintCard(g, this, UIConstants.CARD_BG, 8);
			}
		};
		wrapper.setOpaque(false);
		wrapper.add(content, BorderLayout.CENTER);
		return wrapper;
	}

	/** Rounded card background, outlined in the Halloween theme. Colours are read at paint time. */
	private static void paintCard(Graphics g, Component c, Color fill, int arc) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(fill);
		g2d.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
		if (UIConstants.isHalloweenTheme()) {
			g2d.setColor(UIConstants.BORDER_COLOR);
			g2d.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, arc, arc);
		}
		g2d.dispose();
	}

	/** Centres a label in a vertical BoxLayout. */
	private static JLabel centerAligned(JLabel label) {
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		return label;
	}

	private void revalidateAndRepaint() {
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private String formatNumber(long num) {
		if (num >= 1_000_000) return new DecimalFormat("#.#M").format(num / 1_000_000.0);
		if (num >= 1_000) return new DecimalFormat("#.#K").format(num / 1_000.0);
		return String.valueOf(num);
	}

	private String formatDecimal(double num) {
		return new DecimalFormat("#,##0.0").format(num);
	}

	private String getRankDisplayName(String rankName) {
		if (ranks != null && rankName != null) {
			for (PointsResponse.Rank rank : ranks) {
				if (rank.getName().equalsIgnoreCase(rankName)) {
					return rank.getDisplayName();
				}
			}
		}
		return rankName;
	}

	private void showPointsBreakdown(String sourceType) {
		if (pointsLog == null || pointsLog.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No points log data available.", "Points Log", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		if (albumWindow != null) {
			albumWindow.dispose();
		}
		String playerName = (currentAccount != null && currentAccount.getOsrsAccount() != null)
			? currentAccount.getOsrsAccount().getOsrsNickname() : null;
		albumWindow = new PointsAlbumWindow(playerName, pointsLog, itemManager);
		albumWindow.selectSource(sourceType);
		albumWindow.setVisible(true);
		albumWindow.toFront();
	}

	/** Closes the points album if open; called when the plugin shuts down. */
	public void disposeAlbum() {
		if (albumWindow != null) {
			albumWindow.dispose();
			albumWindow = null;
		}
	}
}

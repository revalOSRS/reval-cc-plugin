package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.components.ChecklistItem;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class ProfilePanel extends JPanel {
	private final JPanel contentPanel;
	private final GridBagConstraints gbc;
	private int gridY = 0;

	private RevalApiService apiService;
	private Client client;
	private UIAssetLoader assetLoader;

	private AccountResponse.AccountData currentAccount;
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

		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = super.getPreferredSize();
				if (getParent() != null) size.width = getParent().getWidth();
				return size;
			}
		};
		wrapper.setBackground(UIConstants.BACKGROUND);
		wrapper.add(contentPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(wrapper);
		scrollPane.setBackground(UIConstants.BACKGROUND);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);

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

	public void init(RevalApiService apiService, Client client, UIAssetLoader assetLoader) {
		this.apiService = apiService;
		this.client = client;
		this.assetLoader = assetLoader;
		fetchRanks();
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
		SwingUtilities.invokeLater(this::showNotLoggedIn);
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

		apiService.fetchAccount(accountHash,
			response -> {
				isLoading = false;
				SwingUtilities.invokeLater(() -> {
					currentAccount = response.getData();
					if (currentAccount != null) pointsLog = currentAccount.getPointsLog();
					if (pointsData != null) buildProfile();
					if (ranks == null || ranks.isEmpty() || pointsData == null) fetchRanks();
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

		apiService.fetchAccountById(osrsAccountId,
			response -> {
				isLoading = false;
				SwingUtilities.invokeLater(() -> {
					currentAccount = response.getData();
					if (currentAccount != null) pointsLog = currentAccount.getPointsLog();
					if (pointsData != null) buildProfile();
					if (ranks == null || ranks.isEmpty() || pointsData == null) fetchRanks();
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

	public String getClanRank() {
		return (currentAccount != null && currentAccount.getOsrsAccount() != null) 
			? currentAccount.getOsrsAccount().getClanRank() : null;
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

		JLabel loading = new JLabel("Loading profile...");
		loading.setFont(FontManager.getRunescapeSmallFont());
		loading.setForeground(UIConstants.TEXT_SECONDARY);
		loading.setAlignmentX(Component.CENTER_ALIGNMENT);

		placeholder.add(loading);
		addComponent(placeholder);
		revalidateAndRepaint();
	}

	private void showError(String message) {
		contentPanel.removeAll();
		gridY = 0;

		JPanel placeholder = createCenteredPanel();
		placeholder.setBorder(new EmptyBorder(30, 20, 20, 20));

		JLabel errorIcon = new JLabel("⚠");
		errorIcon.setFont(FontManager.getRunescapeBoldFont());
		errorIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel errorLabel = new JLabel("<html><center>" + message + "</center></html>");
		errorLabel.setFont(FontManager.getRunescapeSmallFont());
		errorLabel.setForeground(UIConstants.ERROR_COLOR);
		errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel hint = new JLabel("Make sure you're in the Reval clan");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(UIConstants.TEXT_SECONDARY);
		hint.setAlignmentX(Component.CENTER_ALIGNMENT);

		placeholder.add(errorIcon);
		placeholder.add(Box.createRigidArea(new Dimension(0, 6)));
		placeholder.add(errorLabel);
		placeholder.add(Box.createRigidArea(new Dimension(0, 6)));
		placeholder.add(hint);

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

		addComponent(buildHeaderSection(account));
		addSpacing(6);

		if (currentAccount.getPointsBreakdown() != null) {
			gbc.gridy = gridY++;
			gbc.anchor = GridBagConstraints.NORTHWEST;
			contentPanel.add(buildPointsSection(currentAccount.getPointsBreakdown()), gbc);
			gbc.anchor = GridBagConstraints.NORTH;
			addSpacing(6);
		}

		addComponent(buildStatsSection(account));
		addSpacing(6);
		addComponent(buildMilestonesSection(currentAccount.getMilestones()));
		addSpacing(6);
		addComponent(buildCombatAchievementsSection());
		addSpacing(6);
		addComponent(buildCollectionLogSection());
		addSpacing(16);

		revalidateAndRepaint();
	}

	private JPanel buildHeaderSection(AccountResponse.OsrsAccount account) {
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setBackground(UIConstants.CARD_BG);
		header.setBorder(new EmptyBorder(10, 12, 10, 12));

		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setOpaque(false);

		JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		nameRow.setOpaque(false);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel nameLabel = new JLabel(account.getOsrsNickname());
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
		nameRow.add(nameLabel);

		JLabel rankLabel = new JLabel(getRankDisplayName(account.getClanRank()));
		rankLabel.setFont(FontManager.getRunescapeBoldFont());
		rankLabel.setForeground(UIConstants.ACCENT_GOLD);
		rankLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		leftPanel.add(nameRow);
		leftPanel.add(rankLabel);

		JPanel rankProgress = buildRankProgressBar(account);
		if (rankProgress != null) {
			leftPanel.add(Box.createRigidArea(new Dimension(0, 6)));
			rankProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
			leftPanel.add(rankProgress);
		}

		JPanel pointsPanel = new JPanel();
		pointsPanel.setLayout(new BoxLayout(pointsPanel, BoxLayout.Y_AXIS));
		pointsPanel.setOpaque(false);

		int points = account.getActivityPoints() != null ? account.getActivityPoints() : 0;
		JLabel pointsValue = new JLabel(String.format("%,d", points).replace(",", " "));
		pointsValue.setFont(FontManager.getRunescapeBoldFont());
		pointsValue.setForeground(UIConstants.ACCENT_GOLD);
		pointsValue.setAlignmentX(Component.RIGHT_ALIGNMENT);

		JLabel pointsLabel = new JLabel("Reval Points");
		pointsLabel.setFont(FontManager.getRunescapeSmallFont());
		pointsLabel.setForeground(UIConstants.TEXT_SECONDARY);
		pointsLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

		pointsPanel.add(pointsValue);
		pointsPanel.add(pointsLabel);

		header.add(leftPanel, BorderLayout.CENTER);
		header.add(pointsPanel, BorderLayout.EAST);

		return wrapInRoundedPanel(header);
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
			JLabel maxLabel = new JLabel("Max Rank!");
			maxLabel.setFont(FontManager.getRunescapeSmallFont());
			maxLabel.setForeground(UIConstants.ACCENT_GOLD);
			maxRank.add(maxLabel);
			return maxRank;
		}

		int pointsNeeded = nextRank.getPointsRequired() - previousRankPoints;
		int pointsProgress = currentPoints - previousRankPoints;
		double progress = Math.min(1.0, Math.max(0.0, pointsNeeded > 0 ? (double) pointsProgress / pointsNeeded : 0));
		int pointsRemaining = nextRank.getPointsRequired() - currentPoints;
		boolean needsRankUp = pointsRemaining < 0;

		JPanel progressPanel = new JPanel();
		progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
		progressPanel.setOpaque(false);

		JPanel labelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		labelRow.setOpaque(false);
		labelRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		String nextRankName = nextRank.getDisplayName() != null ? nextRank.getDisplayName() : nextRank.getName();
		JLabel progressLabel = new JLabel(pointsRemaining + " pts to " + nextRankName);
		progressLabel.setFont(FontManager.getRunescapeSmallFont());
		progressLabel.setForeground(UIConstants.TEXT_SECONDARY);
		labelRow.add(progressLabel);

		if (needsRankUp) {
			ImageIcon infoIcon = assetLoader != null ? assetLoader.getIcon("info.png", 12) : null;
			JLabel infoIconLabel = infoIcon != null ? new JLabel(infoIcon) : new JLabel("ℹ");
			if (infoIcon == null) {
				infoIconLabel.setFont(FontManager.getRunescapeSmallFont());
				infoIconLabel.setForeground(UIConstants.TEXT_SECONDARY);
			}
			infoIconLabel.setToolTipText("Waiting for a staff member to give you the correct rank");
			infoIconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			labelRow.add(infoIconLabel);
		}

		JProgressBar bar = new JProgressBar(0, 100);
		bar.setValue((int) (progress * 100));
		bar.setBackground(UIConstants.PROGRESS_BG);
		bar.setForeground(UIConstants.ACCENT_GOLD);
		bar.setBorderPainted(false);
		bar.setPreferredSize(new Dimension(100, 4));
		bar.setMaximumSize(new Dimension(120, 4));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);

		progressPanel.add(labelRow);
		progressPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		progressPanel.add(bar);

		return progressPanel;
	}

	private JPanel buildPointsSection(AccountResponse.PointsBreakdown breakdown) {
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setOpaque(false);
		section.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel labelHeader = new JPanel(new BorderLayout());
		labelHeader.setOpaque(false);
		labelHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

		JLabel pointsFromLabel = new JLabel("Points from:");
		pointsFromLabel.setFont(FontManager.getRunescapeBoldFont());
		pointsFromLabel.setForeground(UIConstants.ACCENT_GOLD);
		labelHeader.add(pointsFromLabel, BorderLayout.WEST);

		section.add(labelHeader);
		section.add(Box.createRigidArea(new Dimension(0, 6)));

		JPanel topRow = new JPanel(new GridLayout(1, 3, 4, 0));
		topRow.setOpaque(false);
		topRow.add(createStatCard(formatNumber(breakdown.getDrops()), "Drops", UIConstants.ACCENT_GREEN, "drop"));
		topRow.add(createStatCard(formatNumber(breakdown.getPets()), "Pets", UIConstants.ACCENT_PURPLE, "pet"));
		topRow.add(createStatCard(formatNumber(breakdown.getMilestones()), "Milestones", UIConstants.ACCENT_BLUE, "milestone"));

		JPanel bottomRow = new JPanel(new GridLayout(1, 3, 4, 0));
		bottomRow.setOpaque(false);
		bottomRow.add(createStatCard(formatNumber(breakdown.getRevalDiaries()), "Diaries", UIConstants.ACCENT_GOLD, "revalDiaries"));
		bottomRow.add(createStatCard(formatNumber(breakdown.getRevalChallenges()), "Challenges", UIConstants.ACCENT_GREEN, "revalChallenges"));
		bottomRow.add(createStatCard(formatNumber(breakdown.getEvents()), "Events", UIConstants.ACCENT_BLUE, "event"));

		section.add(topRow);
		section.add(Box.createRigidArea(new Dimension(0, 4)));
		section.add(bottomRow);

		return section;
	}

	private JPanel buildStatsSection(AccountResponse.OsrsAccount account) {
		JPanel section = new JPanel(new GridLayout(1, 2, 4, 4));
		section.setOpaque(false);
		section.add(createStatCard(formatDecimal(account.getEhp() != null ? account.getEhp() : 0.0), "EHP", UIConstants.ACCENT_GREEN, null));
		section.add(createStatCard(formatDecimal(account.getEhb() != null ? account.getEhb() : 0.0), "EHB", UIConstants.ACCENT_BLUE, null));
		return section;
	}

	private JPanel buildMilestonesSection(List<AccountResponse.Milestone> completedMilestones) {
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(UIConstants.CARD_BG);
		wrapper.setBorder(new EmptyBorder(10, 12, 10, 12));

		JLabel title = new JLabel("Milestones");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(UIConstants.TEXT_PRIMARY);
		title.setBorder(new EmptyBorder(0, 0, 8, 0));

		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);

		List<PointsResponse.PointSource> definitions = new ArrayList<>();
		if (pointsData != null && pointsData.getPointSources() != null) {
			List<PointsResponse.PointSource> sources = pointsData.getPointSources().get("MILESTONES");
			if (sources != null) definitions = sources;
		}

		Map<String, AccountResponse.Milestone> completedMap = new HashMap<>();
		if (completedMilestones != null) {
			for (AccountResponse.Milestone m : completedMilestones) {
				if (m.getType() != null) completedMap.put(m.getType(), m);
			}
		}

		if (definitions.isEmpty() && completedMilestones != null) {
			for (AccountResponse.Milestone m : completedMilestones) {
				list.add(new ChecklistItem(m.getDescription(), true, m.getPointsAwarded(), assetLoader));
				list.add(Box.createRigidArea(new Dimension(0, 4)));
			}
		} else {
			for (PointsResponse.PointSource def : definitions) {
				AccountResponse.Milestone completed = completedMap.get(def.getId());
				boolean isCompleted = completed != null && completed.getAchievedAt() != null && !completed.getAchievedAt().isEmpty();
				String desc = def.getDescription() != null ? def.getDescription() : def.getName();
				Integer pts = isCompleted && completed.getPointsAwarded() != null ? completed.getPointsAwarded() : def.getPointsValue();
				list.add(new ChecklistItem(desc, isCompleted, pts, assetLoader));
				list.add(Box.createRigidArea(new Dimension(0, 4)));
			}
		}

		wrapper.add(title, BorderLayout.NORTH);
		wrapper.add(list, BorderLayout.CENTER);
		return wrapInRoundedPanel(wrapper);
	}

	private JPanel buildCombatAchievementsSection() {
		return buildTierSection("Combat Achievements", "COMBAT_ACHIEVEMENTS",
			currentAccount != null ? currentAccount.getCombatAchievementPoints() : 0);
	}

	private JPanel buildCollectionLogSection() {
		return buildTierSection("Collection Log", "COLLECTION_LOG",
			currentAccount != null ? currentAccount.getCollectionLogUniqueObtained() : 0);
	}

	private JPanel buildTierSection(String titleText, String sourceKey, Integer currentProgress) {
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(UIConstants.CARD_BG);
		wrapper.setBorder(new EmptyBorder(10, 12, 10, 12));

		JLabel title = new JLabel(titleText);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(UIConstants.TEXT_PRIMARY);
		title.setBorder(new EmptyBorder(0, 0, 8, 0));

		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);

		int progress = currentProgress != null ? currentProgress : 0;
		List<PointsResponse.PointSource> tiers = new ArrayList<>();

		if (pointsData != null && pointsData.getPointSources() != null) {
			List<PointsResponse.PointSource> sources = pointsData.getPointSources().get(sourceKey);
			if (sources != null) {
				tiers = sources;
				tiers.sort(Comparator.comparingInt(t -> t.getThreshold() != null ? t.getThreshold() : 0));
			}
		}

		if (tiers.isEmpty()) {
			JLabel empty = new JLabel("No tiers available");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(UIConstants.TEXT_SECONDARY);
			list.add(empty);
		} else {
			for (PointsResponse.PointSource tier : tiers) {
				boolean completed = tier.getThreshold() != null && progress >= tier.getThreshold();
				String desc = tier.getDescription() != null ? tier.getDescription() : tier.getName();
				list.add(new ChecklistItem(desc, completed, tier.getPointsValue(), assetLoader));
				list.add(Box.createRigidArea(new Dimension(0, 4)));
			}
		}

		wrapper.add(title, BorderLayout.NORTH);
		wrapper.add(list, BorderLayout.CENTER);
		return wrapInRoundedPanel(wrapper);
	}

	private JPanel createStatCard(String value, String label, Color accentColor, String sourceType) {
		JPanel card = new JPanel() {
			private boolean hovered = false;
			{
				setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
				setOpaque(false);
				setBorder(new EmptyBorder(8, 8, 8, 8));

				if (sourceType != null) {
					addMouseListener(new MouseAdapter() {
						@Override
						public void mouseClicked(MouseEvent e) { showPointsBreakdown(sourceType, label); }
						@Override
						public void mouseEntered(MouseEvent e) { hovered = true; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); repaint(); }
						@Override
						public void mouseExited(MouseEvent e) { hovered = false; setCursor(Cursor.getDefaultCursor()); repaint(); }
					});
				}
			}
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color bg = hovered ? UIConstants.CARD_BG.brighter() : UIConstants.CARD_BG;
				g2d.setColor(bg);
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
				g2d.dispose();
			}
		};

		JLabel valueLabel = new JLabel(value);
		valueLabel.setFont(FontManager.getRunescapeBoldFont());
		valueLabel.setForeground(accentColor != null ? accentColor : UIConstants.TEXT_PRIMARY);
		valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel nameLabel = new JLabel(label);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(UIConstants.TEXT_SECONDARY);
		nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		card.add(valueLabel);
		card.add(nameLabel);
		return card;
	}

	private JPanel createCenteredPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(UIConstants.BACKGROUND);
		return panel;
	}

	private JPanel wrapInRoundedPanel(JPanel content) {
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(UIConstants.CARD_BG);
				g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g2d.dispose();
			}
		};
		wrapper.setOpaque(false);
		wrapper.add(content, BorderLayout.CENTER);
		return wrapper;
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

	private void showPointsBreakdown(String sourceType, String title) {
		if (pointsLog == null || pointsLog.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No points log data available.", "Points Breakdown", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		List<AccountResponse.PointsLogEntry> filtered = new ArrayList<>();
		String filterType = sourceType.equals("revalDiaries") ? "reval_diary" 
			: sourceType.equals("revalChallenges") ? "reval_challenge" : sourceType;

		for (AccountResponse.PointsLogEntry entry : pointsLog) {
			if (entry.getSourceType() != null && entry.getSourceType().equalsIgnoreCase(filterType)) {
				filtered.add(entry);
			}
		}

		filtered.sort((a, b) -> {
			if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
			return b.getCreatedAt().compareTo(a.getCreatedAt());
		});

		new PointsBreakdownPanel(title, filtered).setVisible(true);
	}
}

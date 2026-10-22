package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.competitions.*;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.components.Labels;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.components.RoundedPanel;
import com.revalclan.ui.components.ScrollWrap;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;
import com.revalclan.util.DateTimeUtil;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

import com.revalclan.ui.components.LoginPrompt;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CompetitionsPanel extends JPanel {
	private static final Color MEDAL_GOLD = new Color(255, 215, 0);
	private static final Color MEDAL_SILVER = new Color(198, 198, 198);
	private static final Color MEDAL_BRONZE = new Color(205, 127, 80);

	private RevalApiService apiService;
	private Client client;
	private ItemManager itemManager;
	private RefreshButton refreshButton;

	private final CardLayout cardLayout;
	private final JPanel cardContainer;
	private final JPanel contentPanel;
	private final JPanel detailViewPanel;

	private List<VotesResponse.Vote> activeVotes = new ArrayList<>();
	private List<CompetitionsResponse.Competition> activeCompetitions = new ArrayList<>();
	private List<CompetitionsResponse.Competition> scheduledCompetitions = new ArrayList<>();
	private Map<String, String> myVotes = new HashMap<>();

	private int loadGeneration;
	private int raceGeneration;
	private FeaturedRaceResponse.Race featuredRace;
	private boolean raceLoading;
	private boolean memberLoading;
	private boolean memberContentAvailable;
	private String selectedRaceId;
	private BufferedImage raceArtwork;
	private String raceArtworkUrl;
	private boolean artworkLoading;
	private final Map<String, BufferedImage> objectiveArtwork = new HashMap<>();
	private final Map<String, List<Consumer<BufferedImage>>> objectiveArtworkCallbacks = new HashMap<>();

	public CompetitionsPanel() {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		cardLayout = new CardLayout();
		cardContainer = new JPanel(cardLayout);
		cardContainer.setBackground(UIConstants.BACKGROUND);

		// List view
		JPanel listViewPanel = new JPanel(new BorderLayout());
		listViewPanel.setBackground(UIConstants.BACKGROUND);
		listViewPanel.add(createHeader(), BorderLayout.NORTH);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(UIConstants.BACKGROUND);
		contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

		listViewPanel.add(ScrollWrap.of(contentPanel), BorderLayout.CENTER);

		// Detail view
		detailViewPanel = new JPanel(new BorderLayout());
		detailViewPanel.setBackground(UIConstants.BACKGROUND);

		cardContainer.add(listViewPanel, "LIST");
		cardContainer.add(detailViewPanel, "DETAIL");
		add(cardContainer, BorderLayout.CENTER);
	}

	private JPanel createHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.CARD_BG);
		header.setBorder(new EmptyBorder(10, 12, 10, 12));
		header.add(new PanelTitle("Competitions", UIConstants.TEXT_PRIMARY), BorderLayout.WEST);
		refreshButton = new RefreshButton(this::refresh);
		header.add(refreshButton, BorderLayout.EAST);
		return header;
	}

	public void init(RevalApiService apiService, Client client, ItemManager itemManager) {
		this.apiService = apiService;
		this.client = client;
		this.itemManager = itemManager;
	}

	public void onLoggedOut() {
		loadGeneration++;
		myVotes.clear();
		activeVotes = new ArrayList<>();
		activeCompetitions = new ArrayList<>();
		scheduledCompetitions = new ArrayList<>();
		memberLoading = false;
		updateLoading();
		memberContentAvailable = false;
		selectedRaceId = null;
		showListView();
		buildContent();
	}

	public void refresh() {
		refreshRace();
		loadData(false);
	}

	private void updateLoading() {
		refreshButton.setLoading(raceLoading || memberLoading);
	}

	/** Public data is useful before release and does not require a game login. */
	public void refreshRace() {
		if (apiService == null || raceLoading) return;
		final int generation = ++raceGeneration;
		raceLoading = true;
		updateLoading();
		apiService.fetchFeaturedRace(response -> SwingUtilities.invokeLater(() -> {
			if (generation != raceGeneration) return;
			raceLoading = false;
			updateLoading();
			featuredRace = response.getData() == null ? null : response.getData().getRace();
			objectiveArtwork.clear();
			refreshArtwork();
			renderRace();
		}), error -> SwingUtilities.invokeLater(() -> {
			if (generation != raceGeneration) return;
			raceLoading = false;
			updateLoading();
			buildContent();
		}));
	}

	private void refreshArtwork() {
		String url = featuredRace == null ? null : featuredRace.getImageUrl();
		url = url == null || url.trim().isEmpty() ? null : url.trim();
		if (!Objects.equals(url, raceArtworkUrl)) {
			raceArtworkUrl = url;
			raceArtwork = null;
			artworkLoading = false;
		}
		if (url == null || raceArtwork != null || artworkLoading) return;
		artworkLoading = true;
		final String requestedUrl = url;
		apiService.fetchCompetitionArtwork(url, image -> SwingUtilities.invokeLater(() -> {
			if (!Objects.equals(requestedUrl, raceArtworkUrl)) return;
			artworkLoading = false;
			raceArtwork = image;
			renderRace();
		}), error -> SwingUtilities.invokeLater(() -> {
			if (Objects.equals(requestedUrl, raceArtworkUrl)) artworkLoading = false;
		}));
	}

	/** Cache optional task artwork across the overview and detail card. Callbacks run on the EDT. */
	private void loadObjectiveArtwork(String imageUrl, Consumer<BufferedImage> onSuccess) {
		String url = imageUrl.trim();
		BufferedImage cached = objectiveArtwork.get(url);
		if (cached != null) {
			onSuccess.accept(cached);
			return;
		}
		List<Consumer<BufferedImage>> callbacks = objectiveArtworkCallbacks.get(url);
		if (callbacks != null) {
			callbacks.add(onSuccess);
			return;
		}
		callbacks = new ArrayList<>();
		callbacks.add(onSuccess);
		objectiveArtworkCallbacks.put(url, callbacks);
		apiService.fetchCompetitionArtwork(url, image -> SwingUtilities.invokeLater(() -> {
			objectiveArtwork.put(url, image);
			List<Consumer<BufferedImage>> waiting = objectiveArtworkCallbacks.remove(url);
			if (waiting != null) waiting.forEach(callback -> callback.accept(image));
		}), error -> SwingUtilities.invokeLater(() -> objectiveArtworkCallbacks.remove(url)));
	}

	private void renderRace() {
		buildContent();
		if (selectedRaceId == null) return;
		if (featuredRace != null && selectedRaceId.equals(featuredRace.getId())) {
			showRaceDetails(featuredRace);
		} else {
			selectedRaceId = null;
			showListView();
		}
	}

	private void loadData(boolean showLoadingState) {
		if (apiService == null) return;
		if (client == null || client.getAccountHash() == -1) {
			onLoggedOut();
			return;
		}
		final int generation = ++loadGeneration;
		memberContentAvailable = true;
		if (showLoadingState) showLoading();
		memberLoading = true;
		updateLoading();

		// Each request stores its result on the EDT unless a newer load started; the content rebuilds once all three are in
		final boolean[] loaded = {false, false, false};
		BiConsumer<Integer, Runnable> onResult = (slot, store) -> SwingUtilities.invokeLater(() -> {
			if (generation != loadGeneration) return;
			store.run();
			loaded[slot] = true;
			if (loaded[0] && loaded[1] && loaded[2]) {
				memberLoading = false;
				updateLoading();
				buildContent();
			}
		});

		apiService.fetchVotes(
			response -> onResult.accept(0, () -> {
				activeVotes = response.getData() != null && response.getData().getVotes() != null
					? response.getData().getVotes() : new ArrayList<>();
				fetchMyVotes(activeVotes, generation);
			}),
			error -> onResult.accept(0, () -> activeVotes = new ArrayList<>())
		);

		apiService.fetchActiveCompetitions(
			response -> onResult.accept(1, () -> activeCompetitions = competitionList(response)),
			error -> onResult.accept(1, () -> activeCompetitions = new ArrayList<>())
		);

		apiService.fetchScheduledCompetitions(
			response -> onResult.accept(2, () -> scheduledCompetitions = competitionList(response)),
			error -> onResult.accept(2, () -> scheduledCompetitions = new ArrayList<>())
		);
	}

	private static List<CompetitionsResponse.Competition> competitionList(CompetitionsResponse response) {
		return response.getData() != null && response.getData().getCompetitions() != null
			? response.getData().getCompetitions() : new ArrayList<>();
	}

	private void fetchMyVotes(List<VotesResponse.Vote> votes, int generation) {
		if (client == null || client.getAccountHash() == -1) return;
		myVotes.clear();
		for (VotesResponse.Vote vote : votes) {
			apiService.fetchMyVote(vote.getId(), client.getAccountHash(),
				response -> SwingUtilities.invokeLater(() -> {
					if (generation != loadGeneration) return;
					if (response.getData() != null && response.getData().getHasVoted() && response.getData().getOptionId() != null) {
						myVotes.put(vote.getId(), response.getData().getOptionId());
						buildContent();
					}
				}),
				error -> {}
			);
		}
	}

	private void showLoading() {
		contentPanel.removeAll();
		contentPanel.add(Box.createVerticalStrut(30));
		contentPanel.add(statusLabel("Loading competitions...", UIConstants.TEXT_SECONDARY));
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void buildContent() {
		contentPanel.removeAll();
		boolean hasContent = false;
		if (featuredRace != null) {
			hasContent = true;
			addSection("Race events");
			contentPanel.add(new RaceEventCard(featuredRace, raceArtwork, itemManager, this::loadObjectiveArtwork, false, () -> showRaceDetails(featuredRace)));
			contentPanel.add(Box.createVerticalStrut(12));
		}

		if (!activeVotes.isEmpty()) {
			hasContent = true;
			addSection("Active Votes");
			for (VotesResponse.Vote vote : activeVotes) {
				contentPanel.add(createVoteCard(vote));
				contentPanel.add(Box.createVerticalStrut(8));
			}
			contentPanel.add(Box.createVerticalStrut(12));
		}

		if (!activeCompetitions.isEmpty()) {
			hasContent = true;
			addSection("Active Competitions");
			for (CompetitionsResponse.Competition comp : activeCompetitions) {
				contentPanel.add(createCompetitionCard(comp, true));
				contentPanel.add(Box.createVerticalStrut(8));
			}
			contentPanel.add(Box.createVerticalStrut(12));
		}

		if (!scheduledCompetitions.isEmpty()) {
			hasContent = true;
			addSection("Upcoming Competitions");
			for (CompetitionsResponse.Competition comp : scheduledCompetitions) {
				contentPanel.add(createCompetitionCard(comp, false));
				contentPanel.add(Box.createVerticalStrut(8));
			}
		}

		if (!memberContentAvailable && featuredRace == null) {
			contentPanel.add(new LoginPrompt("Competitions"));
		} else if (!hasContent) {
			contentPanel.add(Box.createVerticalStrut(30));
			contentPanel.add(statusLabel("No active competitions or votes", UIConstants.TEXT_SECONDARY));
		}

		contentPanel.revalidate();
		contentPanel.repaint();


	}

	private void showRaceDetails(FeaturedRaceResponse.Race race) {
		selectedRaceId = race.getId();
		detailViewPanel.removeAll();
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(UIConstants.CARD_BG);
		top.setBorder(new EmptyBorder(8, 8, 8, 8));
		top.add(new BackButton("< Competitions", () -> {
			selectedRaceId = null;
			showListView();
		}), BorderLayout.WEST);
		top.add(new RefreshButton(this::refreshRace), BorderLayout.EAST);
		detailViewPanel.add(top, BorderLayout.NORTH);
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(UIConstants.BACKGROUND);
		body.setBorder(new EmptyBorder(8, 8, 8, 8));
		body.add(new RaceEventCard(race, raceArtwork, itemManager, this::loadObjectiveArtwork, true, null));
		detailViewPanel.add(ScrollWrap.of(body), BorderLayout.CENTER);
		cardLayout.show(cardContainer, "DETAIL");
		detailViewPanel.revalidate();
		detailViewPanel.repaint();
	}

	private void addSection(String text) {
		contentPanel.add(Labels.sectionTitle(text));
		contentPanel.add(Box.createVerticalStrut(6));
	}

	private JPanel createVoteCard(VotesResponse.Vote vote) {
		JPanel card = createCard();

		card.add(Labels.bold(vote.getTitle(), UIConstants.ACCENT_GOLD));
		if (vote.getDescription() != null && !vote.getDescription().isEmpty()) {
			card.add(Box.createVerticalStrut(4));
			card.add(Labels.small(vote.getDescription(), UIConstants.TEXT_SECONDARY));
		}

		card.add(Box.createVerticalStrut(6));
		card.add(Labels.small("Ends in " + formatTimeRemaining(vote.getVoteEndDate()), UIConstants.TEXT_MUTED));
		card.add(Labels.small(vote.getTotalVotes() + " total votes", UIConstants.TEXT_MUTED));

		if (vote.getOptions() != null && !vote.getOptions().isEmpty()) {
			card.add(Box.createVerticalStrut(10));
			String myVotedId = myVotes.get(vote.getId());
			for (VotesResponse.VoteOption option : vote.getOptions()) {
				boolean isMyVote = option.getId() != null && option.getId().equals(myVotedId);
				card.add(createVoteOptionRow(vote, option, isMyVote));
				card.add(Box.createVerticalStrut(4));
			}
		}

		return roundedWrapper(card);
	}

	private JPanel createVoteOptionRow(VotesResponse.Vote vote, VotesResponse.VoteOption option, boolean isMyVote) {
		Color defaultBg = isMyVote ? Colors.withAlpha(UIConstants.ACCENT_BLUE, 40) : UIConstants.BACKGROUND;

		JPanel row = roundedPanel(defaultBg, isMyVote ? UIConstants.ACCENT_BLUE : null);
		row.setLayout(new BorderLayout(8, 0));
		row.setBorder(new EmptyBorder(8, 10, 8, 10));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		row.add(Labels.small(option.getTemplateName(), isMyVote ? UIConstants.ACCENT_BLUE : UIConstants.TEXT_PRIMARY), BorderLayout.WEST);

		int pct = vote.getTotalVotes() > 0 ? (int) Math.round((option.getVoteCount() * 100.0) / vote.getTotalVotes()) : 0;
		row.add(Labels.small(option.getVoteCount() + " (" + pct + "%)", UIConstants.ACCENT_BLUE), BorderLayout.EAST);

		Clickable.onPress(row, () -> castVote(vote.getId(), option.getId()), UIConstants.CARD_HOVER, defaultBg);

		return row;
	}

	private void castVote(String voteId, String optionId) {
		if (apiService == null || client == null || client.getAccountHash() == -1) {
			JOptionPane.showMessageDialog(this, "Please log in to vote", "Not Logged In", JOptionPane.WARNING_MESSAGE);
			return;
		}

		apiService.castVote(voteId, optionId, client.getAccountHash(),
			response -> SwingUtilities.invokeLater(() -> {
				String msg = response.getData() != null && response.getData().getPreviousOptionId() != null
					? "Vote changed!" : "Vote cast!";
				JOptionPane.showMessageDialog(this, msg, "Voted", JOptionPane.INFORMATION_MESSAGE);
				refresh();
			}),
			error -> SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(this, "Failed: " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE))
		);
	}

	private JPanel createCompetitionCard(CompetitionsResponse.Competition comp, boolean isActive) {
		JPanel card = createCard();

		card.add(Labels.bold(comp.getName(), isActive ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_PRIMARY));
		if (comp.getDescription() != null && !comp.getDescription().isEmpty()) {
			card.add(Box.createVerticalStrut(4));
			card.add(Labels.small(comp.getDescription(), UIConstants.TEXT_SECONDARY));
		}

		card.add(Box.createVerticalStrut(6));
		String timeText = isActive ? "Ends in " + formatTimeRemaining(comp.getEndDate())
			: "Starts in " + formatTimeRemaining(comp.getStartDate());
		card.add(Labels.label(timeText, FontManager.getRunescapeFont(), isActive ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_PRIMARY));

		if (comp.getParticipantCount() != null && comp.getParticipantCount() > 0) {
			card.add(Labels.small(comp.getParticipantCount() + " participants", UIConstants.TEXT_MUTED));
		}

		if (isActive && comp.getLeaderboard() != null && !comp.getLeaderboard().isEmpty()) {
			card.add(Box.createVerticalStrut(8));
			card.add(createLeaderboardPreview(comp.getLeaderboard(), comp.getCompetitionType()));
		}

		if (comp.getRewardConfig() != null && !comp.getRewardConfig().isEmpty()) {
			card.add(Box.createVerticalStrut(8));
			JSeparator divider = new JSeparator(SwingConstants.HORIZONTAL);
			divider.setForeground(UIConstants.BACKGROUND);
			divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
			divider.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(divider);
			card.add(Box.createVerticalStrut(8));
			card.add(Labels.small("PRIZES", UIConstants.TEXT_MUTED));
			card.add(Box.createVerticalStrut(4));
			card.add(createRewardsGrid(comp.getRewardConfig()));
		}

		Clickable.onPress(card, () -> showCompetitionDetails(comp), UIConstants.CARD_HOVER, UIConstants.CARD_BG);

		return roundedWrapper(card);
	}

	private JPanel createLeaderboardPreview(List<CompetitionsResponse.LeaderboardEntry> leaderboard, String type) {
		JPanel preview = Labels.column();
		preview.setAlignmentX(Component.LEFT_ALIGNMENT);

		String suffix = getValueSuffix(type);
		for (int i = 0; i < Math.min(3, leaderboard.size()); i++) {
			CompetitionsResponse.LeaderboardEntry entry = leaderboard.get(i);
			Integer value = entry.getCurrentValue() != null ? entry.getCurrentValue() : entry.getFinalValue();

			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

			JPanel nameCell = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			nameCell.setOpaque(false);
			Color placeColor = i == 0 ? MEDAL_GOLD : i == 1 ? MEDAL_SILVER : MEDAL_BRONZE;
			nameCell.add(Labels.small((i + 1) + ". ", placeColor));
			nameCell.add(Labels.small(entry.getOsrsNickname(), UIConstants.TEXT_PRIMARY));
			row.add(nameCell, BorderLayout.WEST);
			row.add(Labels.bold(value != null ? formatValue(value) + suffix : "-", UIConstants.ACCENT_BLUE), BorderLayout.EAST);
			preview.add(row);
		}

		return preview;
	}

	private void showCompetitionDetails(CompetitionsResponse.Competition comp) {
		detailViewPanel.removeAll();

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.CARD_BG);
		header.setBorder(new EmptyBorder(8, 10, 8, 10));
		header.add(new BackButton("< Back", this::showListView), BorderLayout.WEST);
		header.add(Labels.bold(comp.getName(), UIConstants.ACCENT_GOLD), BorderLayout.EAST);
		detailViewPanel.add(header, BorderLayout.NORTH);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(UIConstants.BACKGROUND);
		content.setBorder(new EmptyBorder(12, 12, 12, 12));
		content.add(Box.createVerticalStrut(20));
		content.add(statusLabel("Loading leaderboard...", UIConstants.TEXT_SECONDARY));

		detailViewPanel.add(ScrollWrap.of(content), BorderLayout.CENTER);
		detailViewPanel.revalidate();
		detailViewPanel.repaint();
		cardLayout.show(cardContainer, "DETAIL");

		apiService.fetchCompetitionDetails(comp.getId(),
			response -> SwingUtilities.invokeLater(() -> buildDetailsContent(content, response.getData())),
			error -> SwingUtilities.invokeLater(() -> {
				content.removeAll();
				content.add(Box.createVerticalStrut(20));
				content.add(statusLabel("Failed to load: " + error.getMessage(), UIConstants.ERROR_COLOR));
				content.revalidate();
				content.repaint();
			})
		);
	}

	private void buildDetailsContent(JPanel content, CompetitionsResponse.Competition comp) {
		content.removeAll();

		if (comp == null) {
			content.add(statusLabel("Competition not found", UIConstants.ERROR_COLOR));
			content.revalidate();
			content.repaint();
			return;
		}

		if (comp.getDescription() != null) {
			content.add(Labels.small(comp.getDescription(), UIConstants.TEXT_SECONDARY));
			content.add(Box.createVerticalStrut(8));
		}

		boolean isActive = "active".equalsIgnoreCase(comp.getStatus());
		String timeText = isActive ? "Ends in " + formatTimeRemaining(comp.getEndDate())
			: "Starts in " + formatTimeRemaining(comp.getStartDate());
		content.add(Labels.label(timeText, FontManager.getRunescapeFont(), UIConstants.TEXT_PRIMARY));

		if (comp.getParticipantCount() != null) {
			content.add(Labels.small(comp.getParticipantCount() + " participants", UIConstants.TEXT_MUTED));
		}

		content.add(Box.createVerticalStrut(12));
		content.add(Labels.sectionTitle("Leaderboard"));
		content.add(Box.createVerticalStrut(6));

		if (comp.getLeaderboard() != null && !comp.getLeaderboard().isEmpty()) {
			for (int i = 0; i < comp.getLeaderboard().size(); i++) {
				content.add(createLeaderboardRow(comp.getLeaderboard().get(i), i + 1, comp.getCompetitionType()));
				content.add(Box.createVerticalStrut(2));
			}
		} else {
			content.add(Labels.small("No participants yet", UIConstants.TEXT_MUTED));
		}

		if (comp.getSideQuests() != null && !comp.getSideQuests().isEmpty()) {
			content.add(Box.createVerticalStrut(16));
			content.add(Labels.sectionTitle("Side Quests"));
			content.add(Box.createVerticalStrut(6));
			for (CompetitionsResponse.SideQuest sq : comp.getSideQuests()) {
				content.add(createSideQuestCard(sq));
				content.add(Box.createVerticalStrut(8));
			}
		}

		content.revalidate();
		content.repaint();
	}

	private JPanel createLeaderboardRow(CompetitionsResponse.LeaderboardEntry entry, int rank, String type) {
		String playerName = client != null && client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		boolean isCurrentPlayer = playerName != null && entry.getOsrsNickname() != null
			&& entry.getOsrsNickname().equalsIgnoreCase(playerName);

		JPanel row = roundedPanel(UIConstants.CARD_BG, isCurrentPlayer ? UIConstants.ACCENT_BLUE : null);
		row.setLayout(new BorderLayout(4, 0));
		row.setBorder(new EmptyBorder(6, 10, 6, 10));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

		Color rankColor = rank == 1 ? MEDAL_GOLD
			: rank == 2 ? MEDAL_SILVER
			: rank == 3 ? MEDAL_BRONZE
			: UIConstants.TEXT_SECONDARY;

		JLabel rankLabel = Labels.bold("#" + rank, rankColor);
		rankLabel.setPreferredSize(new Dimension(42, 20));

		Integer value = entry.getCurrentValue() != null ? entry.getCurrentValue() : entry.getFinalValue();

		row.add(rankLabel, BorderLayout.WEST);
		row.add(Labels.small(entry.getOsrsNickname(), isCurrentPlayer ? UIConstants.ACCENT_BLUE : UIConstants.TEXT_PRIMARY), BorderLayout.CENTER);
		row.add(Labels.bold(value != null ? formatValue(value) + getValueSuffix(type) : "-", UIConstants.ACCENT_GOLD), BorderLayout.EAST);

		return row;
	}

	private JPanel createSideQuestCard(CompetitionsResponse.SideQuest sq) {
		JPanel card = createCard();
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

		card.add(Labels.bold(sq.getName(), UIConstants.ACCENT_PURPLE));
		if (sq.getDescription() != null) {
			card.add(Labels.small(sq.getDescription(), UIConstants.TEXT_SECONDARY));
		}

		if (sq.getLeaderboard() != null && !sq.getLeaderboard().isEmpty()) {
			card.add(Box.createVerticalStrut(6));
			String suffix = getValueSuffix(sq.getSideQuestType());
			for (int i = 0; i < Math.min(3, sq.getLeaderboard().size()); i++) {
				CompetitionsResponse.LeaderboardEntry entry = sq.getLeaderboard().get(i);
				card.add(Labels.small((i + 1) + ". " + entry.getOsrsNickname() + " - " + formatValue(entry.getCurrentValue()) + suffix,
					UIConstants.TEXT_PRIMARY));
			}
		}

		return roundedWrapper(card);
	}

	private void showListView() {
		cardLayout.show(cardContainer, "LIST");
	}

	// ── UI Helpers ──────────────────────────────────────────────────────

	/** Small status line centred in the vertical content panels. */
	private static JLabel statusLabel(String text, Color color) {
		JLabel label = Labels.small(text, color);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		return label;
	}

	/** Creates a card panel with BoxLayout.Y_AXIS. */
	private JPanel createCard() {
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(UIConstants.CARD_BG);
		card.setBorder(new EmptyBorder(12, 14, 12, 14));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
		return card;
	}

	/** Non-opaque panel painting its (live) background rounded, with an optional 2px border. */
	private JPanel roundedPanel(Color bg, Color borderColor) {
		JPanel panel = new RoundedPanel(4) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (borderColor != null) {
					Graphics2D g2d = (Graphics2D) g.create();
					g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2d.setColor(borderColor);
					g2d.setStroke(new BasicStroke(2));
					g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 4, 4);
					g2d.dispose();
				}
			}
		};
		panel.setBackground(bg);
		return panel;
	}

	/** Wraps an inner card panel with a rounded-corner outer wrapper. */
	private JPanel roundedWrapper(JPanel inner) {
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				RoundedPanel.fill(g, this, inner.getBackground(), 8);
			}
		};
		wrapper.setOpaque(false);
		wrapper.add(inner, BorderLayout.CENTER);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.setMaximumSize(inner.getMaximumSize());
		return wrapper;
	}

	// ── Formatting ──────────────────────────────────────────────────────

	private String formatTimeRemaining(String dateStr) {
		if (dateStr == null) return "Unknown";
		try {
			Instant target = DateTimeUtil.parseToInstant(dateStr);
			Instant now = Instant.now();
			long days = ChronoUnit.DAYS.between(now, target);
			long hours = ChronoUnit.HOURS.between(now, target) % 24;
			if (days < 0 || (days == 0 && hours < 0)) return "Ended";
			if (days > 0) return days + "d " + hours + "h";
			return hours + "h " + (ChronoUnit.MINUTES.between(now, target) % 60) + "m";
		} catch (Exception e) {
			return dateStr;
		}
	}

	/** Prize breakdown rows: place on the left, points on the right */
	private JPanel createRewardsGrid(Map<String, Integer> rewards) {
		JPanel grid = new JPanel(new GridLayout(0, 1, 0, 3));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);

		addRewardCell(grid, "1st", rewards.get("1"), MEDAL_GOLD);
		addRewardCell(grid, "2nd", rewards.get("2"), MEDAL_SILVER);
		addRewardCell(grid, "3rd", rewards.get("3"), MEDAL_BRONZE);
		addRewardCell(grid, "4-10th", rewards.get("top10"), UIConstants.TEXT_SECONDARY);

		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		return grid;
	}

	private void addRewardCell(JPanel grid, String place, Integer points, Color placeColor) {
		if (points == null) return;

		JPanel cell = new JPanel(new BorderLayout());
		cell.setOpaque(false);
		cell.add(Labels.small(place, placeColor), BorderLayout.WEST);
		cell.add(Labels.small(points + " pts", UIConstants.ACCENT_GOLD), BorderLayout.EAST);
		grid.add(cell);
	}

	private String formatValue(Integer value) {
		if (value == null) return "-";
		if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
		if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
		return String.valueOf(value);
	}

	private String getValueSuffix(String type) {
		if (type == null) return "";
		String t = type.toLowerCase();
		if (t.contains("xp") || t.contains("experience") || t.contains("skill")) return " XP";
		if (t.contains("kc") || t.contains("kill") || t.contains("boss") || t.contains("npc")) return " KC";
		return "";
	}
}

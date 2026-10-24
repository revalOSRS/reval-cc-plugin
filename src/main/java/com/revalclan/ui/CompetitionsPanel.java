package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.competitions.*;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.BlockButton;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.components.Labels;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.components.RoundedPanel;
import com.revalclan.ui.components.ScrollWrap;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.Colors;
import com.revalclan.util.DateTimeUtil;
import com.revalclan.util.RequestScope;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CompetitionsPanel extends JPanel {
	private static final Pattern TOP_RANGE = Pattern.compile("^top(\\d+)$");
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
	private int detailGeneration;
	private FeaturedRaceResponse.Race featuredRace;
	private boolean raceLoading;
	private boolean memberLoading;
	private boolean memberContentAvailable;
	private String selectedRaceId;
	private BufferedImage raceArtwork;
	private RaceEventCard raceDetailCard;
	private String raceArtworkUrl;
	private boolean artworkLoading;
	private final RequestScope lifetime = new RequestScope();
	private final Map<String, BufferedImage> objectiveArtwork = new HashMap<>();
	private final Set<String> objectiveArtworkLoading = new HashSet<>();
	private static final int MAX_OBJECTIVE_ARTWORK = 8;

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
		if (!lifetime.isActive()) return;
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
		if (!lifetime.isActive()) return;
		refreshRace();
		loadData();
	}

	private void onEdt(Runnable callback) { SwingUtilities.invokeLater(lifetime.guard(callback)); }

	private void updateLoading() {
		refreshButton.setLoading(raceLoading || memberLoading);
	}

	/** Public data is useful before release and does not require a game login. */
	public void refreshRace() {
		if (!lifetime.isActive() || apiService == null || raceLoading) return;
		final int generation = ++raceGeneration;
		raceLoading = true;
		updateLoading();
		apiService.fetchFeaturedRace(response -> onEdt(() -> {
			if (generation != raceGeneration) return;
			raceLoading = false;
			updateLoading();
			featuredRace = response.getData() == null ? null : response.getData().getRace();
			objectiveArtwork.clear();
			refreshArtwork();
			renderRace();
		}), error -> onEdt(() -> {
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
		apiService.fetchCompetitionArtwork(url, image -> onEdt(() -> {
			if (!Objects.equals(requestedUrl, raceArtworkUrl)) return;
			artworkLoading = false;
			raceArtwork = image;
			if (!swapRaceDetailArtwork()) renderRace();
		}), error -> onEdt(() -> {
			if (Objects.equals(requestedUrl, raceArtworkUrl)) artworkLoading = false;
		}));
	}

	/** One request per optional image; render current cards rather than retaining callbacks to replaced cards. */
	private void loadObjectiveArtwork(String imageUrl, Consumer<BufferedImage> onSuccess) {
		if (!lifetime.isActive()) return;
		String url = imageUrl.trim();
		if (objectiveArtwork.containsKey(url)) {
			BufferedImage cached = objectiveArtwork.get(url);
			if (cached != null) onSuccess.accept(cached);
			return;
		}
		if (objectiveArtwork.size() + objectiveArtworkLoading.size() >= MAX_OBJECTIVE_ARTWORK
			|| !objectiveArtworkLoading.add(url)) return;
		apiService.fetchCompetitionArtwork(url, image -> onEdt(() -> {
			objectiveArtworkLoading.remove(url);
			if (usesObjectiveArtwork(url)) objectiveArtwork.put(url, image);
			if (!swapRaceDetailArtwork()) buildContent();
		}), error -> onEdt(() -> {
			objectiveArtworkLoading.remove(url);
			// A failed URL keeps its fallback until the next explicit race refresh.
			if (usesObjectiveArtwork(url)) objectiveArtwork.put(url, null);
			if (!swapRaceDetailArtwork()) buildContent();
		}));
	}

	private boolean usesObjectiveArtwork(String url) {
		return featuredRace != null && featuredRace.getObjectives() != null
			&& featuredRace.getObjectives().stream().anyMatch(objective -> objective != null
				&& objective.getImageUrl() != null && url.equals(objective.getImageUrl().trim()));
	}

	/** Terminal: queued EDT replies must not rebuild a detached panel or start more requests. */
	public void shutDown() {
		lifetime.cancel();
		loadGeneration++;
		raceGeneration++;
		detailGeneration++;
		objectiveArtwork.clear();
		objectiveArtworkLoading.clear();
		activeVotes = new ArrayList<>();
		activeCompetitions = new ArrayList<>();
		scheduledCompetitions = new ArrayList<>();
		myVotes.clear();
		featuredRace = null;
		raceArtwork = null;
		raceArtworkUrl = null;
		raceDetailCard = null;
		selectedRaceId = null;
		raceLoading = memberLoading = artworkLoading = false;
		contentPanel.removeAll();
		detailViewPanel.removeAll();
		updateLoading();
	}

	/**
	 * With the race detail open, put the banner into its card without rebuilding the detail:
	 * a rebuild would fetch the side quests again and reset the scroll position.
	 */
	private boolean swapRaceDetailArtwork() {
		if (raceDetailCard == null || featuredRace == null || selectedRaceId == null
			|| !selectedRaceId.equals(featuredRace.getId())) return false;
		Container parent = raceDetailCard.getParent();
		if (parent == null) return false;
		buildContent();
		int index = Arrays.asList(parent.getComponents()).indexOf(raceDetailCard);
		raceDetailCard = new RaceEventCard(featuredRace, raceArtwork, itemManager, this::loadObjectiveArtwork, true, null);
		parent.remove(index);
		parent.add(raceDetailCard, index);
		parent.revalidate();
		parent.repaint();
		return true;
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

	private void loadData() {
		if (!lifetime.isActive() || apiService == null) return;
		if (client == null || client.getAccountHash() == -1) {
			onLoggedOut();
			return;
		}
		final int generation = ++loadGeneration;
		memberContentAvailable = true;
		memberLoading = true;
		updateLoading();

		// Each request stores its result on the EDT unless a newer load started; the content rebuilds once all three are in
		final boolean[] loaded = {false, false, false};
		BiConsumer<Integer, Runnable> onResult = (slot, store) -> onEdt(() -> {
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
				response -> onEdt(() -> {
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

		hasContent |= addCompetitions("Active Competitions", activeCompetitions, true);
		hasContent |= addCompetitions("Upcoming Competitions", scheduledCompetitions, false);

		if (!memberContentAvailable && featuredRace == null) {
			contentPanel.add(new LoginPrompt("Competitions"));
		} else if (!hasContent) {
			contentPanel.add(Box.createVerticalStrut(30));
			contentPanel.add(statusLabel("No active competitions or votes", UIConstants.TEXT_SECONDARY));
		}

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	/** The public race already renders its linked KC competition, including all standings. */
	private boolean addCompetitions(String title, List<CompetitionsResponse.Competition> competitions, boolean active) {
		String featuredId = featuredRace != null && featuredRace.getKc() != null ? featuredRace.getKc().getId() : null;
		boolean added = false;
		for (CompetitionsResponse.Competition comp : competitions) {
			if (featuredId != null && featuredId.equals(comp.getId())) continue;
			if (!added) addSection(title);
			contentPanel.add(createCompetitionCard(comp, active, 5));
			contentPanel.add(Box.createVerticalStrut(8));
			added = true;
		}
		if (added) contentPanel.add(Box.createVerticalStrut(12));
		return added;
	}

	private void showRaceDetails(FeaturedRaceResponse.Race race) {
		selectedRaceId = race.getId();
		int generation = ++detailGeneration;
		JPanel body = detailBody(this::refreshRace);
		raceDetailCard = new RaceEventCard(race, raceArtwork, itemManager, this::loadObjectiveArtwork, true, null);
		body.add(raceDetailCard);
		// Side quests are member data, absent from the public race feed. Keep them reachable here.
		if (memberContentAvailable && race.getKc() != null && race.getKc().getId() != null) {
			JPanel extras = Labels.column();
			body.add(extras);
			Runnable unavailable = () -> {
				if (generation != detailGeneration) return;
				extras.add(Labels.small("Side quests unavailable", UIConstants.TEXT_MUTED));
				JButton retry = new RefreshButton(() -> showRaceDetails(race));
				retry.setText("Retry side quests");
				extras.add(retry);
				extras.revalidate();
				extras.repaint();
			};
			apiService.fetchCompetitionDetails(race.getKc().getId(), response -> onEdt(() -> {
				if (generation != detailGeneration) return;
				if (response.getData() == null) unavailable.run();
				else addSideQuests(extras, response.getData().getSideQuests());
				extras.revalidate();
				extras.repaint();
			}), error -> onEdt(unavailable));
		}
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
			response -> onEdt(() -> {
				String msg = response.getData() != null && response.getData().getPreviousOptionId() != null
					? "Vote changed!" : "Vote cast!";
				JOptionPane.showMessageDialog(this, msg, "Voted", JOptionPane.INFORMATION_MESSAGE);
				refresh();
			}),
			error -> onEdt(() ->
				JOptionPane.showMessageDialog(this, "Failed: " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE))
		);
	}

	/** The overview and full view use the same card and table; only the row limit changes. */
	private JPanel createCompetitionCard(CompetitionsResponse.Competition comp, boolean active, int rowLimit) {
		JPanel card = createCard();
		card.setBorder(new EmptyBorder(10, 8, 10, 8));
		card.add(Labels.wrapped(comp.getName(), FontManager.getRunescapeBoldFont(),
			active ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_PRIMARY, 165));
		if (comp.getDescription() != null && !comp.getDescription().isEmpty()) {
			card.add(Box.createVerticalStrut(4));
			card.add(Labels.wrapped(comp.getDescription(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY, 165));
		}
		card.add(Box.createVerticalStrut(6));
		String time = "completed".equalsIgnoreCase(comp.getStatus()) ? "Completed" : "cancelled".equalsIgnoreCase(comp.getStatus()) ? "Cancelled"
			: (active ? "Ends in " : "Starts in ") + formatTimeRemaining(active ? comp.getEndDate() : comp.getStartDate());
		card.add(Labels.small(time,
			active ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_PRIMARY));
		if (comp.getParticipantCount() != null && comp.getParticipantCount() > 0) {
			card.add(Labels.small(comp.getParticipantCount() + " participants", UIConstants.TEXT_MUTED));
		}
		if (comp.getRewardConfig() != null && !comp.getRewardConfig().isEmpty()) {
			card.add(Box.createVerticalStrut(8));
			card.add(Labels.small("PRIZES", UIConstants.TEXT_MUTED));
			card.add(createRewardsGrid(comp.getRewardConfig()));
		}
		// In the overview, upcoming competitions have no standings yet: no table and no full view
		boolean hasStandings = rowLimit != 5 || active || (comp.getLeaderboard() != null && !comp.getLeaderboard().isEmpty());
		if (hasStandings) {
			String metric = metric(comp.getCompetitionType());
			card.add(Box.createVerticalStrut(10));
			card.add(Labels.sectionTitle(rowLimit == 5 ? "Top 5 " + metric : metric + " leaderboard"));
			card.add(Box.createVerticalStrut(6));
			card.add(standings(comp.getLeaderboard(), comp.getCompetitionType(), comp.getRewardConfig(), rowLimit));
		}
		if (rowLimit != 5) {
			addSideQuests(card, comp.getSideQuests());
		} else if (hasStandings) {
			JButton full = new BlockButton("Full leaderboard >", UIConstants.ACCENT_GOLD, 24);
			full.setAlignmentX(Component.LEFT_ALIGNMENT);
			full.addActionListener(e -> showCompetitionDetails(comp, active));
			card.add(Box.createVerticalStrut(10));
			card.add(full);
		}
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return roundedWrapper(card);
	}

	private void showCompetitionDetails(CompetitionsResponse.Competition summary, boolean active) {
		selectedRaceId = null;
		int generation = ++detailGeneration;
		JPanel body = detailBody(() -> showCompetitionDetails(summary, active));
		body.add(statusLabel("Loading leaderboard...", UIConstants.TEXT_SECONDARY));
		apiService.fetchCompetitionDetails(summary.getId(), response -> onEdt(() -> {
			if (generation != detailGeneration) return;
			body.removeAll();
			CompetitionsResponse.Competition comp = response.getData();
			if (comp == null) body.add(statusLabel("Competition not found", UIConstants.ERROR_COLOR));
			else body.add(createCompetitionCard(comp, comp.getStatus() == null ? active : "active".equalsIgnoreCase(comp.getStatus()), Integer.MAX_VALUE));
			body.revalidate();
			body.repaint();
		}), error -> onEdt(() -> {
			if (generation != detailGeneration) return;
			body.removeAll();
			body.add(statusLabel("Failed to load: " + error.getMessage(), UIConstants.ERROR_COLOR));
			body.revalidate();
			body.repaint();
		}));
	}

	/** Back/refresh stay separate from the wrapped title, even for long competition names. */
	private JPanel detailBody(Runnable refresh) {
		detailViewPanel.removeAll();
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.CARD_BG);
		header.setBorder(new EmptyBorder(8, 8, 8, 8));
		header.add(new BackButton("< Competitions", this::showListView), BorderLayout.WEST);
		header.add(new RefreshButton(refresh), BorderLayout.EAST);
		detailViewPanel.add(header, BorderLayout.NORTH);
		JPanel body = Labels.column();
		body.setBorder(new EmptyBorder(8, 8, 8, 8));
		detailViewPanel.add(ScrollWrap.of(body), BorderLayout.CENTER);
		cardLayout.show(cardContainer, "DETAIL");
		detailViewPanel.revalidate();
		detailViewPanel.repaint();
		return body;
	}

	private void addSideQuests(JPanel parent, List<CompetitionsResponse.SideQuest> quests) {
		if (quests == null || quests.isEmpty()) return;
		parent.add(Box.createVerticalStrut(12));
		parent.add(Labels.sectionTitle("Side Quests"));
		for (CompetitionsResponse.SideQuest quest : quests) {
			parent.add(Box.createVerticalStrut(8));
			parent.add(Labels.wrapped(quest.getName(), FontManager.getRunescapeBoldFont(), UIConstants.ACCENT_PURPLE, 165));
			if (quest.getDescription() != null) {
				parent.add(Labels.wrapped(quest.getDescription(), FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY, 165));
			}
			parent.add(standings(quest.getLeaderboard(), quest.getSideQuestType(), quest.getRewardConfig(), Integer.MAX_VALUE));
		}
	}

	private CompetitionStandings standings(List<CompetitionsResponse.LeaderboardEntry> leaderboard,
			String type, Map<String, Integer> rewards, int limit) {
		List<CompetitionStandings.Entry> entries = new ArrayList<>();
		if (leaderboard != null) for (int i = 0; i < Math.min(limit, leaderboard.size()); i++) {
			CompetitionsResponse.LeaderboardEntry player = leaderboard.get(i);
			int rank = player.getRank() != null && player.getRank() > 0 ? player.getRank() : i + 1;
			Long value = player.getCurrentValue() != null ? player.getCurrentValue() : player.getFinalValue();
			entries.add(new CompetitionStandings.Entry(rank, player.getOsrsNickname(), value,
				value != null && value > 0 ? placementPoints(rank, rewards) : 0));
		}
		String viewer = client != null && client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		return new CompetitionStandings(metric(type), entries, "No participants yet", viewer);
	}

	static String metric(String type) {
		switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
			case "skill_xp": return "XP";
			case "boss_kills": case "npc_kills": return "KC";
			case "loot_value": return "GP";
			case "collection_log": return "Slots";
			case "minigame_rounds": return "Rounds";
			case "clue_scrolls": return "Clues";
			default: return "Value";
		}
	}

	/** Mirrors the backend: nonzero exact rank first, then the first matching top-N range. */
	static int placementPoints(int rank, Map<String, Integer> rewards) {
		if (rewards == null) return 0;
		Integer exact = rewards.get(String.valueOf(rank));
		if (exact != null && exact != 0) return Math.max(0, exact);
		for (Map.Entry<String, Integer> reward : rewards.entrySet()) {
			Matcher range = TOP_RANGE.matcher(reward.getKey());
			if (range.matches() && rank <= Double.parseDouble(range.group(1))) {
				return reward.getValue() == null ? 0 : Math.max(0, reward.getValue());
			}
		}
		return 0;
	}

	private void showListView() {
		selectedRaceId = null;
		detailGeneration++;
		detailViewPanel.removeAll();
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

		rewards.forEach((rank, points) -> {
			if (points == null) return;
			String place = "1".equals(rank) ? "1st" : "2".equals(rank) ? "2nd" : "3".equals(rank) ? "3rd"
				: rank.startsWith("top") ? "Top " + rank.substring(3) : "#" + rank;
			Color color = "1".equals(rank) ? MEDAL_GOLD : "2".equals(rank) ? MEDAL_SILVER
				: "3".equals(rank) ? MEDAL_BRONZE : UIConstants.TEXT_SECONDARY;
			JPanel cell = Labels.row();
			cell.add(Labels.small(place, color), BorderLayout.WEST);
			cell.add(Labels.small(points + " pts", UIConstants.ACCENT_GOLD), BorderLayout.EAST);
			grid.add(cell);
		});

		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		return grid;
	}

}

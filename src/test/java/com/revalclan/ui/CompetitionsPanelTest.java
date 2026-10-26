package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.competitions.*;
import net.runelite.api.Client;
import net.runelite.api.Player;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CompetitionsPanelTest {
    @Test public void onlyTheLinkedKcCompetitionIsHidden() throws Exception {
        FakeApi api = new FakeApi();
        api.race = race("event-id", "kc-id");
        api.active = Arrays.asList(competition("kc-id", "boss_kills", 8), competition("event-id", "skill_xp", 8));
        api.scheduled = Arrays.asList(competition("kc-id", "boss_kills", 0), competition("other-race", "boss_kills", 0));
        CompetitionsPanel panel = panel(api);
        assertEquals(1, find(panel, RaceEventCard.class, c -> true).size());
        // The race card and the unrelated active competition have standings; the upcoming
        // "other-race" stays listed but gets no table or full view until it has entries.
        assertEquals(2, find(panel, CompetitionStandings.class, c -> true).size());
        assertEquals(1, buttons(panel, "Full leaderboard >").size());
        assertEquals(1, find(panel, JLabel.class, l -> String.valueOf(l.getText()).contains(">other-race<")).size());
        assertTrue(find(panel, JLabel.class, l -> String.valueOf(l.getText()).contains(">kc-id<")).isEmpty());
    }

    @Test public void absentLinkedIdDoesNotHideCompetitions() throws Exception {
        FakeApi api = new FakeApi();
        api.race = race("event-id", null);
        api.active = Collections.singletonList(competition("event-id", "boss_kills", 6));
        CompetitionsPanel panel = panel(api);
        assertEquals(1, buttons(panel, "Full leaderboard >").size());
    }

    @Test public void weeklyPreviewAndFullViewShareTheTableAndRetainSideQuests() throws Exception {
        FakeApi api = new FakeApi();
        CompetitionsResponse.Competition comp = competition("SOTW", "skill_xp", 12);
        comp.getLeaderboard().get(0).setCurrentValue(3_000_000_000L);
        comp.getLeaderboard().get(1).setRank(1);
        comp.setRewardConfig(Collections.singletonMap("1", 300));
        api.active = Collections.singletonList(comp);
        CompetitionsPanel panel = panel(api);
        assertEquals(5, rows(panel).size());
        assertEquals(2, labels(panel, "+300").size());
        assertEquals(1, find(panel, JLabel.class, l -> "3 000 000 000".equals(l.getToolTipText())).size());
        click(panel, "Full leaderboard >");
        assertEquals(Collections.singletonList("SOTW"), api.detailIds);
        CompetitionsResponse.SideQuest quest = new CompetitionsResponse.SideQuest();
        quest.setName("Bonus task");
        quest.setSideQuestType("boss_kills");
        quest.setLeaderboard(Collections.singletonList(comp.getLeaderboard().get(0)));
        quest.setRewardConfig(Collections.singletonMap("1", 25));
        comp.setSideQuests(Collections.singletonList(quest));
        reply(api, 0, comp);
        assertEquals(13, rows(panel).size());
        assertFalse(labels(panel, "XP").isEmpty());
        assertFalse(labels(panel, "KC").isEmpty());
        assertFalse(labels(panel, "Side Quests").isEmpty());
        assertEquals(1, labels(panel, "+25").size());
        assertTrue(buttons(panel, "Full leaderboard >").isEmpty());
        assertEquals(1, buttons(panel, "< Competitions").size());
    }

    @Test public void staleDetailCannotReappearAfterBackLogoutOrAnotherSelection() throws Exception {
        FakeApi api = new FakeApi();
        api.active = Collections.singletonList(competition("BOTW", "boss_kills", 12));
        CompetitionsPanel panel = panel(api);
        click(panel, "Full leaderboard >");
        click(panel, "< Competitions");
        reply(api, 0, api.active.get(0));
        assertEquals(5, rows(panel).size());
        click(panel, "Full leaderboard >");
        click(panel, "Refresh");
        reply(api, 1, api.active.get(0));
        assertTrue(rows(panel).isEmpty());
        edt(panel::onLoggedOut);
        reply(api, 2, api.active.get(0));
        assertTrue(rows(panel).isEmpty());
        assertTrue(buttons(panel, "< Competitions").isEmpty());
    }

    @Test public void publicRefreshDoesNotReplaceAnOpenWeeklyLeaderboard() throws Exception {
        FakeApi api = new FakeApi();
        api.active = Collections.singletonList(competition("BOTW", "boss_kills", 12));
        CompetitionsPanel panel = panel(api);
        click(panel, "Full leaderboard >");
        reply(api, 0, api.active.get(0));
        api.race = race("event-id", "kc-id");
        edt(panel::refreshRace);
        assertEquals(12, rows(panel).size());
        assertTrue(find(panel, RaceEventCard.class, c -> true).isEmpty());
    }

    @Test public void linkedRaceKeepsPublicRowsAndMemberSideQuests() throws Exception {
        FakeApi api = new FakeApi();
        api.race = race("event-id", "kc-id");
        CompetitionsPanel panel = panel(api);
        click(panel, "Full KC leaderboard >");
        assertEquals(12, rows(panel).size());
        assertEquals(Collections.singletonList("kc-id"), api.detailIds);
        CompetitionsResponse.Competition comp = competition("kc-id", "boss_kills", 12);
        CompetitionsResponse.SideQuest quest = new CompetitionsResponse.SideQuest();
        quest.setName("Bonus task");
        quest.setLeaderboard(Collections.singletonList(comp.getLeaderboard().get(0)));
        comp.setSideQuests(Collections.singletonList(quest));
        reply(api, 0, comp);
        assertEquals(13, rows(panel).size());
        assertFalse(labels(panel, "Side Quests").isEmpty());
    }

    @Test public void bannerArrivalKeepsTheOpenRaceDetail() throws Exception {
        FakeApi api = new FakeApi();
        api.race = race("event-id", "kc-id");
        api.race.setImageUrl("https://example.invalid/banner.png");
        CompetitionsPanel panel = panel(api);
        click(panel, "Full KC leaderboard >");
        assertEquals(1, api.artwork.size());
        edt(() -> api.artwork.get(0).accept(new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB)));
        edt(() -> { });
        // The banner swaps into the open card: no second side-quest fetch, and the
        // pending one still lands in the same detail view.
        assertEquals(Collections.singletonList("kc-id"), api.detailIds);
        CompetitionsResponse.Competition comp = competition("kc-id", "boss_kills", 12);
        CompetitionsResponse.SideQuest quest = new CompetitionsResponse.SideQuest();
        quest.setName("Bonus task");
        quest.setLeaderboard(Collections.singletonList(comp.getLeaderboard().get(0)));
        comp.setSideQuests(Collections.singletonList(quest));
        reply(api, 0, comp);
        assertFalse(labels(panel, "Side Quests").isEmpty());
    }

    @Test public void placementRewardsFollowBackendPrecedence() {
        Map<String, Integer> rewards = new LinkedHashMap<>();
        rewards.put("1", 300);
        rewards.put("2", 0);
        rewards.put("3", -5);
        rewards.put("top25", 25);
        rewards.put("top10", 100);
        assertEquals(300, CompetitionsPanel.placementPoints(1, rewards));
        assertEquals(25, CompetitionsPanel.placementPoints(2, rewards));
        assertEquals(0, CompetitionsPanel.placementPoints(3, rewards));
        assertEquals(25, CompetitionsPanel.placementPoints(4, rewards));
        assertEquals(25, CompetitionsPanel.placementPoints(25, rewards));
        assertEquals(0, CompetitionsPanel.placementPoints(26, rewards));
    }

    @Test public void completedDetailsShowTheirTerminalStatus() throws Exception {
        FakeApi api = new FakeApi();
        CompetitionsResponse.Competition comp = competition("BOTW", "boss_kills", 8);
        api.active = Collections.singletonList(comp);
        CompetitionsPanel panel = panel(api);
        click(panel, "Full leaderboard >");
        comp.setStatus("completed");
        reply(api, 0, comp);
        assertEquals(1, labels(panel, "Completed").size());
    }

    @Test public void missingRaceMemberDetailsCanBeRetriedWithoutLosingPublicStandings() throws Exception {
        FakeApi api = new FakeApi();
        api.race = race("event-id", "kc-id");
        CompetitionsPanel panel = panel(api);
        click(panel, "Full KC leaderboard >");
        reply(api, 0, null);
        assertEquals(12, rows(panel).size());
        click(panel, "Retry side quests");
        assertEquals(Arrays.asList("kc-id", "kc-id"), api.detailIds);
        reply(api, 1, competition("kc-id", "boss_kills", 12));
        assertEquals(12, rows(panel).size());
        assertTrue(buttons(panel, "Retry side quests").isEmpty());
    }

    @Test public void allCompetitionMetricsRemainAvailable() {
        String[] kinds = {"skill_xp", "boss_kills", "npc_kills", "loot_value", "collection_log", "minigame_rounds", "clue_scrolls", "custom"};
        String[] labels = {"XP", "KC", "KC", "GP", "Slots", "Rounds", "Clues", "Value"};
        for (int i = 0; i < kinds.length; i++) assertEquals(labels[i], CompetitionsPanel.metric(kinds[i]));
    }

    @Test public void narrowStandingsKeepPlayerNamesAndBillionScoresVisible() throws Exception {
        edt(() -> {
            List<CompetitionStandings.Entry> entries = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                entries.add(new CompetitionStandings.Entry(i, String.format("LongName%04d", i), 3_000_000_000L, 500));
            }
            CompetitionStandings table = new CompetitionStandings("XP", entries, "Empty", null);
            table.setSize(193, table.getPreferredSize().height);
            layout(table);
            for (JLabel label : find(table, JLabel.class, l -> true)) {
                assertTrue(label.getText(), label.getFontMetrics(label.getFont()).stringWidth(label.getText()) <= label.getWidth());
            }
        });
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
    }

    private static CompetitionsPanel panel(FakeApi api) throws Exception {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(client.getAccountHash()).thenReturn(42L);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Player 1");
        CompetitionsPanel[] panel = new CompetitionsPanel[1];
        edt(() -> {
            panel[0] = new CompetitionsPanel();
            panel[0].init(api, client, null);
            panel[0].refresh();
        });
        return panel[0];
    }

    private static CompetitionsResponse.Competition competition(String id, String type, int count) {
        CompetitionsResponse.Competition comp = new CompetitionsResponse.Competition();
        comp.setId(id);
        comp.setName(id);
        comp.setCompetitionType(type);
        comp.setEndDate("2099-01-01T00:00:00Z");
        comp.setLeaderboard(new ArrayList<>());
        for (int i = 1; i <= count; i++) {
            CompetitionsResponse.LeaderboardEntry entry = new CompetitionsResponse.LeaderboardEntry();
            entry.setRank(i);
            entry.setOsrsNickname("Player " + i);
            entry.setCurrentValue(100L - i);
            comp.getLeaderboard().add(entry);
        }
        return comp;
    }

    private static FeaturedRaceResponse.Race race(String eventId, String kcId) {
        FeaturedRaceResponse.Race race = new FeaturedRaceResponse.Race();
        race.setId(eventId);
        race.setName("Race");
        FeaturedRaceResponse.Kc kc = new FeaturedRaceResponse.Kc();
        kc.setId(kcId);
        kc.setLeaderboard(new ArrayList<>());
        for (int i = 1; i <= 12; i++) {
            FeaturedRaceResponse.Standing entry = new FeaturedRaceResponse.Standing();
            entry.setRank(i);
            entry.setName("Raider " + i);
            kc.getLeaderboard().add(entry);
        }
        race.setKc(kc);
        return race;
    }

    private static List<JPanel> rows(Component root) {
        return find(root, JPanel.class, c -> "competition-standings-row".equals(c.getName()));
    }

    private static List<JLabel> labels(Component root, String text) {
        return find(root, JLabel.class, l -> text.equals(l.getText()));
    }

    private static List<AbstractButton> buttons(Component root, String text) {
        return find(root, AbstractButton.class, b -> text.equals(b.getText()));
    }

    private static <T extends Component> List<T> find(Component root, Class<T> type, Predicate<T> predicate) {
        List<T> result = new ArrayList<>();
        if (!root.isVisible()) return result;
        if (type.isInstance(root) && predicate.test(type.cast(root))) result.add(type.cast(root));
        if (root instanceof Container) for (Component child : ((Container) root).getComponents()) result.addAll(find(child, type, predicate));
        return result;
    }

    private static void click(Component root, String text) throws Exception {
        List<AbstractButton> buttons = buttons(root, text);
        assertEquals(text, 1, buttons.size());
        edt(buttons.get(0)::doClick);
    }

    private static void reply(FakeApi api, int index, CompetitionsResponse.Competition comp) throws Exception {
        CompetitionDetailsResponse response = new CompetitionDetailsResponse();
        response.setData(comp);
        edt(() -> api.pending.get(index).accept(response));
    }

    private static void edt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
        for (int i = 0; i < 4; i++) SwingUtilities.invokeAndWait(() -> {});
    }

    private static class FakeApi extends RevalApiService {
        FeaturedRaceResponse.Race race;
        List<CompetitionsResponse.Competition> active = Collections.emptyList(), scheduled = Collections.emptyList();
        final List<String> detailIds = new ArrayList<>();
        final List<Consumer<CompetitionDetailsResponse>> pending = new ArrayList<>();
        final List<Consumer<BufferedImage>> artwork = new ArrayList<>();

        FakeApi() { super(null, null, null); }

        @Override public void fetchFeaturedRace(Consumer<FeaturedRaceResponse> ok, Consumer<Exception> error) {
            FeaturedRaceResponse response = new FeaturedRaceResponse();
            FeaturedRaceResponse.RaceData data = new FeaturedRaceResponse.RaceData();
            data.setRace(race);
            response.setData(data);
            ok.accept(response);
        }
        @Override public void fetchVotes(Consumer<VotesResponse> ok, Consumer<Exception> error) { ok.accept(new VotesResponse()); }
        @Override public void fetchActiveCompetitions(Consumer<CompetitionsResponse> ok, Consumer<Exception> error) { ok.accept(response(active)); }
        @Override public void fetchScheduledCompetitions(Consumer<CompetitionsResponse> ok, Consumer<Exception> error) { ok.accept(response(scheduled)); }
        @Override public void fetchCompetitionArtwork(String imageUrl, Consumer<BufferedImage> ok, Consumer<Exception> error) {
            artwork.add(ok);
        }
        @Override public void fetchCompetitionDetails(String id, Consumer<CompetitionDetailsResponse> ok, Consumer<Exception> error) {
            detailIds.add(id);
            pending.add(ok);
        }
        private CompetitionsResponse response(List<CompetitionsResponse.Competition> competitions) {
            CompetitionsResponse response = new CompetitionsResponse();
            CompetitionsResponse.CompetitionsData data = new CompetitionsResponse.CompetitionsData();
            data.setCompetitions(competitions);
            response.setData(data);
            return response;
        }
    }
}

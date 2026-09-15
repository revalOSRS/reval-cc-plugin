package com.revalclan.ui;

import com.google.gson.Gson;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.achievements.AchievementsResponse;
import com.revalclan.api.diaries.DiariesResponse;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.api.leaderboard.LeaderboardResponse;
import com.revalclan.api.points.PointsResponse;
import com.revalclan.api.competitions.*;
import com.revalclan.ui.components.LoginPrompt;
import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class RevalPanelLoadingTest {
	private RevalPanel panel;
	private FakeApi api;
	@Before public void setup() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			api = new FakeApi();
			Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
				(proxy, method, args) -> method.getName().equals("getAccountHash") ? 42L : null);
			panel = new RevalPanel();
			panel.init(api, client, null, null, null, null, null);
			assertEquals(0, api.count("ranking"));
		});
	}
	@Test public void loginLoadsOnlySelectedMemberTabsOnEdt() throws Exception {
		panel.onLoggedIn(); flush();
		assertEquals(1, api.count("profile"));
		assertEquals(0, api.count("diary"));
		for (String tab : new String[]{"DIARY", "ACHIEVEMENTS", "EVENTS", "COMPETITIONS"}) {
			SwingUtilities.invokeAndWait(() -> { panel.showTab(tab); panel.showTab(tab); });
		}
		assertEquals(2, api.count("events"));
		for (String name : new String[]{"diary", "achievements", "votes", "active", "scheduled"}) assertEquals(name, 1, api.count(name));
	}
	@Test public void publicPanelsSurviveLoginAndLogout() throws Exception {
		SwingUtilities.invokeAndWait(() -> { panel.showTab("LEADERBOARD"); panel.showTab("RANKING"); });
		panel.onLoggedIn(); panel.onLoggedOut(); flush();
		SwingUtilities.invokeAndWait(() -> { panel.showTab("LEADERBOARD"); panel.showTab("RANKING"); });
		assertEquals(1, api.count("leaderboard")); assertEquals(2, api.count("ranking"));
	}
	@Test public void selectionBeforeValidationAndQuickRelogLoadSelectedMemberTab() throws Exception {
		SwingUtilities.invokeAndWait(() -> panel.showTab("DIARY"));
		assertEquals(0, api.count("diary"));
		panel.onLoggedIn(); flush(); assertEquals(1, api.count("diary"));
		panel.onLoggedOut(); panel.onLoggedIn(); flush();
		assertEquals(2, api.count("diary")); assertEquals(0, api.count("events"));
	}
	@Test public void lateCompetitionResponsesCannotReplaceLogoutPrompt() throws Exception {
		panel.onLoggedIn(); flush();
		SwingUtilities.invokeAndWait(() -> panel.showTab("COMPETITIONS"));
		panel.onLoggedOut(); flush();
		api.votes.accept(new VotesResponse()); api.active.accept(new CompetitionsResponse()); api.scheduled.accept(new CompetitionsResponse());
		flush();
		SwingUtilities.invokeAndWait(() -> assertTrue(containsPrompt(panel.getCompetitionsPanel())));
	}
	@Test public void eventsRequireExplicitActionsAndRelogShowsReadyPrompt() throws Exception {
		SwingUtilities.invokeAndWait(() -> panel.showTab("EVENTS"));
		panel.onLoggedIn(); flush();
		assertEquals(0, api.count("events"));
		SwingUtilities.invokeAndWait(() -> assertFalse(containsPrompt(panel.getEventsPanel())));
		SwingUtilities.invokeAndWait(() -> panel.showTab("EVENTS"));
		api.events.accept(new Gson().fromJson("{\"status\":\"success\",\"data\":{\"events\":[{\"name\":\"Past\",\"status\":\"completed\"}]}}", EventsResponse.class));
		flush();
		SwingUtilities.invokeAndWait(() -> panel.showTab("EVENTS"));
		assertEquals(2, api.count("events"));
		panel.onLoggedOut(); panel.onLoggedIn(); flush();
		assertEquals(2, api.count("events"));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(containsPrompt(panel.getEventsPanel()));
			panel.getEventsPanel().refresh();
		});
		assertEquals(3, api.count("events"));
	}
	private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }
	private static boolean containsPrompt(Component c) {
		if (c instanceof LoginPrompt) return true;
		if (c instanceof Container) for (Component child : ((Container)c).getComponents()) if (containsPrompt(child)) return true;
		return false;
	}
	private static class FakeApi extends RevalApiService {
		final Map<String, Integer> calls = new HashMap<>();
		Consumer<VotesResponse> votes;
		Consumer<EventsResponse> events;
		Consumer<CompetitionsResponse> active, scheduled;
		FakeApi() { super(null, new Gson()); }
		void called(String name) { assertTrue("Load must run on EDT", SwingUtilities.isEventDispatchThread()); calls.merge(name, 1, Integer::sum); }
		int count(String name) { return calls.getOrDefault(name, 0); }
		@Override public void fetchPoints(Consumer<PointsResponse> ok, Consumer<Exception> err) { called("ranking"); }
		@Override public void refreshAccount(long hash, Consumer<AccountResponse> ok, Consumer<Exception> err) { called("profile"); }
		@Override public void fetchAccount(long hash, Consumer<AccountResponse> ok, Consumer<Exception> err) { called("profile"); }
		@Override public void fetchLeaderboard(Consumer<LeaderboardResponse> ok, Consumer<Exception> err) { called("leaderboard"); }
		@Override public void fetchDiaries(Long hash, Consumer<DiariesResponse> ok, Consumer<Exception> err) { called("diary"); }
		@Override public void fetchAchievementDefinitions(Long hash, Consumer<AchievementsResponse> ok, Consumer<Exception> err) { called("achievements"); }
		@Override public void fetchEvents(Consumer<EventsResponse> ok, Consumer<Exception> err) { called("events"); events = ok; }
		@Override public void fetchVotes(Consumer<VotesResponse> ok, Consumer<Exception> err) { called("votes"); votes = ok; }
		@Override public void fetchActiveCompetitions(Consumer<CompetitionsResponse> ok, Consumer<Exception> err) { called("active"); active = ok; }
		@Override public void fetchScheduledCompetitions(Consumer<CompetitionsResponse> ok, Consumer<Exception> err) { called("scheduled"); scheduled = ok; }
	}
}

package com.revalclan.ui;

import com.google.gson.Gson;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.account.AccountResponse;
import com.revalclan.api.points.PointsResponse;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class ProfileBootstrapTest {
	private final AtomicReference<Throwable> uiError = new AtomicReference<>();
	private Thread.UncaughtExceptionHandler previousHandler;
	@Before public void captureUiErrors() {
		previousHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, error) -> uiError.set(error));
	}
	@After public void verifyUiErrors() throws Exception {
		SwingUtilities.invokeAndWait(() -> {});
		Thread.setDefaultUncaughtExceptionHandler(previousHandler);
		if (uiError.get() != null) throw new AssertionError("Profile rendering failed", uiError.get());
	}
	private static final String POINTS = "{\"ranks\":[{\"name\":\"Recruit\",\"displayName\":\"Recruit\",\"pointsRequired\":0}],\"pointSources\":{}}";
	@Test public void bundledDefinitionsAvoidTheSeparatePointsRequest() throws Exception { check(true, false, 0); }
	@Test public void oldBackendFallsBackToPointsRequest() throws Exception { check(false, false, 1); }
	@Test public void otherAccountViewRetainsPointsFallback() throws Exception { check(false, true, 1); }

	private void check(boolean bundled, boolean byId, int expectedPointsRequests) throws Exception {
		FakeApi api = new FakeApi();
		ProfilePanel[] panel = new ProfilePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new ProfilePanel();
			panel[0].init(api, null, null, null, null, null);
			assertEquals(0, api.pointsRequests);
			if (byId) panel[0].loadAccountById(42); else panel[0].loadAccount(42);
		});
		String json = "{\"status\":\"success\",\"data\":{\"combatAchievementPoints\":0,\"collectionLogUniqueObtained\":0,\"questPoints\":0,\"diariesTotalCompleted\":0,\"totalKills\":0,\"milestones\":[],\"osrsAccount\":{\"id\":42,\"osrsNickname\":\"Example\",\"activityPoints\":0,\"maintenancePoints\":0}" + (bundled ? ",\"pointsConfig\":" + POINTS : "") + "}}";
		api.account.accept(new Gson().fromJson(json, AccountResponse.class));
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(expectedPointsRequests, api.pointsRequests);
		SwingUtilities.invokeAndWait(() -> assertTrue(panel[0].isAccountLoaded()));
	}
	private static class FakeApi extends RevalApiService {
		int pointsRequests;
		Consumer<AccountResponse> account;
		FakeApi() { super(null, new Gson()); }
		@Override public void fetchAccount(long hash, Consumer<AccountResponse> ok, Consumer<Exception> err) { account = ok; }
		@Override public void fetchAccountById(int id, Consumer<AccountResponse> ok, Consumer<Exception> err) { account = ok; }
		@Override public void fetchPoints(Consumer<PointsResponse> ok, Consumer<Exception> err) { pointsRequests++; }
	}
}

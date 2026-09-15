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
	@Test public void ownProfileStartsBothRequestsBeforeAccountResponse() throws Exception {
		checkLoad(ProfilePanel::loadAccount);
	}
	@Test public void otherProfileStartsBothRequestsBeforeAccountResponse() throws Exception {
		checkLoad((panel, id) -> panel.loadAccountById((int) id));
	}
	@Test public void loadedConfigurationIsReusedAcrossAccountLoads() throws Exception {
		FakeApi api = new FakeApi();
		ProfilePanel[] panel = new ProfilePanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new ProfilePanel();
			panel[0].init(api, null, null, null, null, null);
			panel[0].loadAccount(42);
			api.points.accept(new Gson().fromJson("{\"status\":\"success\",\"data\":{\"ranks\":[]}}", PointsResponse.class));
			api.account.accept(new AccountResponse());
		});
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> panel[0].loadAccount(43));
		assertEquals(1, api.pointsRequests);
	}
	private void checkLoad(java.util.function.ObjLongConsumer<ProfilePanel> load) throws Exception {
		FakeApi api = new FakeApi();
		SwingUtilities.invokeAndWait(() -> {
			ProfilePanel panel = new ProfilePanel();
			panel.init(api, null, null, null, null, null);
			assertEquals(0, api.pointsRequests);
			assertNull(api.account);
			load.accept(panel, 42);
			assertEquals(1, api.pointsRequests);
			assertNotNull(api.account);
		});
	}
	private static class FakeApi extends RevalApiService {
		int pointsRequests;
		Consumer<AccountResponse> account;
		Consumer<PointsResponse> points;
		FakeApi() { super(null, new Gson()); }
		@Override public void fetchAccount(long hash, Consumer<AccountResponse> ok, Consumer<Exception> err) { account = ok; }
		@Override public void fetchAccountById(int id, Consumer<AccountResponse> ok, Consumer<Exception> err) { account = ok; }
		@Override public void fetchPoints(Consumer<PointsResponse> ok, Consumer<Exception> err) { pointsRequests++; points = ok; }
	}
}

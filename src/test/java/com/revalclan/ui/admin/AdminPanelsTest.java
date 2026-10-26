package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.admin.ActualizeRankChangeResponse;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.api.admin.PendingRankChangesResponse;
import com.revalclan.auth.LinkException;
import net.runelite.api.Client;
import net.runelite.api.Player;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The admin views act for one signed-in session and go quiet the moment it ends. */
public class AdminPanelsTest {
	private static final long A = 1_111_111_111_111L;
	private static final long B = 2_222_222_222_222L;

	private long account;
	private AdminManager manager;
	private FakeApi api;
	private Client client;
	private int signedIn;

	@Before
	public void setUp() {
		account = A;
		manager = new AdminManager(() -> account);
		api = new FakeApi();
		client = mock(Client.class);
		Player player = mock(Player.class);
		when(player.getName()).thenReturn("Zezima");
		when(client.getLocalPlayer()).thenReturn(player);
		when(client.getAccountHash()).thenAnswer(call -> account);
		signedIn = 0;
	}

	private AdminLoginPanel loginPanel() throws Exception {
		AdminLoginPanel[] panel = new AdminLoginPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new AdminLoginPanel(api, client, manager, () -> signedIn++, () -> {}));
		return panel[0];
	}

	private void signIn() {
		assertTrue(manager.complete(manager.begin(), AdminManagerTest.accepted()));
	}

	// ------------------------------------------------------------------ login panel

	@Test
	public void checkAccessAsksTheServerAboutTheLinkedAccountAndSignsIn() throws Exception {
		AdminLoginPanel panel = loginPanel();

		click(panel, "Check access");

		assertEquals(1, api.logins.size());
		assertEquals(Long.valueOf(A), api.loginAccountHash);
		assertEquals("Zezima", api.loginNickname);
		assertEquals(0, signedIn);

		edt(() -> api.loginOk.get(0).accept(response(AdminManagerTest.accepted())));

		assertEquals(1, signedIn);
		assertTrue(manager.isLoggedIn());
	}

	@Test
	public void thereIsNothingToTypeAndNoCodeFieldOnTheLoginPanel() throws Exception {
		AdminLoginPanel panel = loginPanel();
		assertTrue(find(panel, JTextField.class, c -> true).isEmpty());
		assertTrue(find(panel, JPasswordField.class, c -> true).isEmpty());
	}

	@Test
	public void aServerRefusalShowsAMessageAndSignsNobodyIn() throws Exception {
		AdminLoginPanel panel = loginPanel();
		click(panel, "Check access");

		edt(() -> api.loginOk.get(0).accept(response(AdminManagerTest.refused())));

		assertEquals(0, signedIn);
		assertFalse(manager.isLoggedIn());
		assertFalse(labels(panel, "Access denied").isEmpty());
		assertTrue("can try again", buttons(panel, "Check access").get(0).isEnabled());
	}

	@Test
	public void anOrdinaryPairedMemberSeesAccessDeniedNotAnEmptyDashboard() throws Exception {
		AdminLoginPanel panel = loginPanel();
		click(panel, "Check access");

		edt(() -> api.loginOk.get(0).accept(response(AdminManagerTest.memberWithoutPermissions(Collections.emptyList()))));

		assertEquals("the dashboard is never opened", 0, signedIn);
		assertFalse(manager.isLoggedIn());
		assertNull(manager.session());
		assertFalse(labels(panel, "Access denied").isEmpty());
		assertTrue("can try again", buttons(panel, "Check access").get(0).isEnabled());
	}

	@Test
	public void aMemberWhoseAnswerCarriesNoPermissionsFieldAtAllIsDeniedToo() throws Exception {
		AdminLoginPanel panel = loginPanel();
		click(panel, "Check access");

		edt(() -> api.loginOk.get(0).accept(response(AdminManagerTest.memberWithoutPermissions(null))));

		assertEquals(0, signedIn);
		assertFalse(labels(panel, "Access denied").isEmpty());
	}

	@Test
	public void anAdminWithPermissionsStillGetsIn() throws Exception {
		AdminLoginPanel panel = loginPanel();
		click(panel, "Check access");

		edt(() -> api.loginOk.get(0).accept(response(AdminManagerTest.memberWithoutPermissions(Collections.singletonList("admin:full")))));

		assertEquals(1, signedIn);
		assertTrue(manager.isLoggedIn());
		assertTrue(labels(panel, "Access denied").isEmpty());
	}

	@Test
	public void anUnlinkedAccountSeesTheLinkingInstruction() throws Exception {
		AdminLoginPanel panel = loginPanel();
		click(panel, "Check access");

		edt(() -> api.loginErr.get(0).accept(new LinkException(LinkException.Kind.NOT_LINKED)));

		assertEquals(0, signedIn);
		assertFalse(labels(panel, "Link code").isEmpty());
		assertTrue(buttons(panel, "Check access").get(0).isEnabled());
	}

	@Test
	public void theAdminHintSendsAdministratorsToTheirOwnMemberRecordInTheAdminConsole() throws Exception {
		AdminLoginPanel panel = loginPanel();

		assertFalse("where the Link code comes from", labels(panel, "own member record in the Reval admin console").isEmpty());
		assertFalse(labels(panel, "Link my RuneLite plugin").isEmpty());
		assertFalse("and where it goes", labels(panel, "'Link code' setting").isEmpty());
		assertTrue("the public account settings cannot issue an admin credential", labels(panel, "account settings").isEmpty());
	}

	@Test
	public void aReplyThatArrivesAfterLogoutSignsNobodyIn() throws Exception {
		AdminLoginPanel panel = loginPanel();
		click(panel, "Check access");

		edt(manager::clearSession);
		edt(() -> api.loginOk.get(0).accept(response(AdminManagerTest.accepted())));

		assertEquals(0, signedIn);
		assertFalse(manager.isLoggedIn());
	}

	@Test
	public void aReplyThatArrivesAfterAnAccountSwitchSignsNobodyIn() throws Exception {
		AdminLoginPanel panel = loginPanel();
		click(panel, "Check access");

		account = B;
		edt(() -> api.loginOk.get(0).accept(response(AdminManagerTest.accepted())));

		assertEquals(0, signedIn);
		assertFalse(manager.isLoggedIn());
		account = A;
		assertFalse("not even when the first account comes back", manager.isLoggedIn());
	}

	@Test
	public void invalidatingThePanelDropsAReplyStillInFlight() throws Exception {
		AdminLoginPanel panel = loginPanel();
		click(panel, "Check access");

		edt(panel::invalidate);
		edt(() -> api.loginOk.get(0).accept(response(AdminManagerTest.accepted())));

		assertEquals(0, signedIn);
		assertFalse(manager.isLoggedIn());
	}

	@Test
	public void aFailureAfterLogoutIsNotShown() throws Exception {
		AdminLoginPanel panel = loginPanel();
		click(panel, "Check access");

		edt(manager::clearSession);
		edt(() -> api.loginErr.get(0).accept(new Exception("late failure")));

		assertTrue(labels(panel, "late failure").isEmpty());
	}

	// ------------------------------------------------------------------ views

	@Test
	public void theRankupListIgnoresDataThatArrivesAfterLogout() throws Exception {
		signIn();
		PendingRankupsPanel[] panel = new PendingRankupsPanel[1];
		AdminManager.Session session = manager.session();
		edt(() -> panel[0] = new PendingRankupsPanel(api, session, () -> {}, null, null));
		assertEquals(1, api.pending.size());
		assertFalse(labels(panel[0], "Loading").isEmpty());

		edt(manager::clearSession);
		PendingRankChangesResponse response = new PendingRankChangesResponse();
		PendingRankChangesResponse.PendingRankChangesData data = new PendingRankChangesResponse.PendingRankChangesData();
		PendingRankChangesResponse.RankChange change = new PendingRankChangesResponse.RankChange();
		change.setOsrsNickname("Zezima");
		data.setPendingRankChanges(Collections.singletonList(change));
		data.setTotal(1);
		response.setData(data);
		edt(() -> api.pending.get(0).accept(response));

		assertTrue("nothing from the old session was drawn", labels(panel[0], "Zezima").isEmpty());
		assertFalse("still as it was", labels(panel[0], "Loading").isEmpty());
	}

	@Test
	public void theRankupListShowsDataForTheCurrentSession() throws Exception {
		signIn();
		PendingRankupsPanel[] panel = new PendingRankupsPanel[1];
		AdminManager.Session session = manager.session();
		edt(() -> panel[0] = new PendingRankupsPanel(api, session, () -> {}, null, null));

		PendingRankChangesResponse response = new PendingRankChangesResponse();
		PendingRankChangesResponse.PendingRankChangesData data = new PendingRankChangesResponse.PendingRankChangesData();
		data.setPendingRankChanges(new ArrayList<>());
		response.setData(data);
		edt(() -> api.pending.get(0).accept(response));

		assertFalse(labels(panel[0], "No pending rank changes").isEmpty());
	}

	@Test
	public void theDashboardIgnoresStatsThatArriveAfterLogout() throws Exception {
		signIn();
		AdminDashboardPanel[] panel = new AdminDashboardPanel[1];
		AdminManager.Session session = manager.session();
		edt(() -> panel[0] = new AdminDashboardPanel(api, session, manager.getAuthData(), target -> {}));
		assertEquals(1, api.pending.size());

		edt(manager::clearSession);
		PendingRankChangesResponse response = new PendingRankChangesResponse();
		PendingRankChangesResponse.PendingRankChangesData data = new PendingRankChangesResponse.PendingRankChangesData();
		data.setTotal(5);
		response.setData(data);
		edt(() -> api.pending.get(0).accept(response));

		assertTrue(labels(panel[0], "5").isEmpty());
	}

	@Test
	public void aViewForAnEndedSessionDoesNotEvenAskTheServer() throws Exception {
		signIn();
		AdminManager.Session session = manager.session();
		manager.clearSession();

		PendingRankupsPanel[] panel = new PendingRankupsPanel[1];
		edt(() -> panel[0] = new PendingRankupsPanel(api, session, () -> {}, null, null));
		edt(() -> new AdminDashboardPanel(api, session, null, target -> {}));

		assertTrue(api.pending.isEmpty());
		assertTrue(api.actualized.isEmpty());
	}

	// ------------------------------------------------------------------ helpers

	private static AdminAuthResponse response(AdminAuthResponse.AdminAuthData data) {
		AdminAuthResponse response = new AdminAuthResponse();
		response.setStatus("success");
		response.setData(data);
		return response;
	}

	private static void edt(Runnable action) throws Exception {
		SwingUtilities.invokeAndWait(action);
		for (int i = 0; i < 4; i++) SwingUtilities.invokeAndWait(() -> {});
	}

	private static void click(Component root, String text) throws Exception {
		List<AbstractButton> buttons = buttons(root, text);
		assertEquals(text, 1, buttons.size());
		edt(buttons.get(0)::doClick);
	}

	private static List<AbstractButton> buttons(Component root, String text) {
		return find(root, AbstractButton.class, button -> text.equals(button.getText()));
	}

	private static List<JLabel> labels(Component root, String contains) {
		return find(root, JLabel.class, label -> label.getText() != null && label.getText().contains(contains) && label.isVisible());
	}

	private static <T extends Component> List<T> find(Component root, Class<T> type, Predicate<T> filter) {
		List<T> found = new ArrayList<>();
		if (type.isInstance(root) && filter.test(type.cast(root))) found.add(type.cast(root));
		if (root instanceof Container) {
			for (Component child : ((Container) root).getComponents()) found.addAll(find(child, type, filter));
		}
		return found;
	}

	private static class FakeApi extends RevalApiService {
		final List<Object> logins = new ArrayList<>();
		Long loginAccountHash;
		String loginNickname;
		final List<Consumer<AdminAuthResponse>> loginOk = new ArrayList<>();
		final List<Consumer<Exception>> loginErr = new ArrayList<>();
		final List<Consumer<PendingRankChangesResponse>> pending = new ArrayList<>();
		final List<Integer> actualized = new ArrayList<>();

		FakeApi() { super(null, null, null); }

		@Override
		public void adminLogin(Long accountHash, String osrsNickname, Consumer<AdminAuthResponse> ok, Consumer<Exception> error) {
			logins.add(accountHash);
			loginAccountHash = accountHash;
			loginNickname = osrsNickname;
			loginOk.add(ok);
			loginErr.add(error);
		}

		@Override
		public void fetchPendingRankChanges(Consumer<PendingRankChangesResponse> ok, Consumer<Exception> error) {
			pending.add(ok);
		}

		@Override
		public void actualizeRankChange(int rankChangeId, Consumer<ActualizeRankChangeResponse> ok, Consumer<Exception> error) {
			actualized.add(rankChangeId);
		}
	}
}

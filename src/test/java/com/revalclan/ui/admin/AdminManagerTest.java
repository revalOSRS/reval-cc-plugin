package com.revalclan.ui.admin;

import com.revalclan.api.admin.AdminAuthResponse;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;

import static org.junit.Assert.*;

public class AdminManagerTest {
	private static final long A = 1_111_111_111_111L;
	private static final long B = 2_222_222_222_222L;

	private long account;
	private AdminManager manager;

	@Before
	public void setUp() {
		account = A;
		manager = new AdminManager(() -> account);
	}

	static AdminAuthResponse.AdminAuthData accepted() {
		AdminAuthResponse.AdminAuthData data = new AdminAuthResponse.AdminAuthData();
		data.setAuthenticated(true);
		data.setPermissions(Collections.singletonList("rank-changes"));
		return data;
	}

	/** The server answers {@code authenticated: true} for every linked member; only admins hold permissions. */
	static AdminAuthResponse.AdminAuthData memberWithoutPermissions(java.util.List<String> permissions) {
		AdminAuthResponse.AdminAuthData data = new AdminAuthResponse.AdminAuthData();
		data.setAuthenticated(true);
		data.setPermissions(permissions);
		return data;
	}

	static AdminAuthResponse.AdminAuthData refused() {
		AdminAuthResponse.AdminAuthData data = new AdminAuthResponse.AdminAuthData();
		data.setAuthenticated(false);
		return data;
	}

	@Test
	public void nobodyIsSignedInUntilTheServerAcceptsTheAccount() {
		assertFalse(manager.isLoggedIn());
		assertNull(manager.session());

		AdminManager.Session attempt = manager.begin();
		assertFalse("a refusal signs nobody in", manager.complete(attempt, refused()));
		assertFalse("neither does a missing answer", manager.complete(attempt, null));
		assertFalse(manager.isLoggedIn());

		assertTrue(manager.complete(attempt, accepted()));
		assertTrue(manager.isLoggedIn());
		assertTrue(manager.session().isCurrent());
		assertEquals(Collections.singletonList("rank-changes"), manager.getAuthData().getPermissions());
	}

	@Test
	public void anOrdinaryLinkedMemberIsAuthenticatedButNotAnAdmin() {
		java.util.List<java.util.List<String>> noPermissions = java.util.Arrays.asList(
			null, Collections.<String>emptyList(), Collections.singletonList(""), Collections.singletonList("   "),
			java.util.Arrays.asList("", null, " "));
		for (java.util.List<String> permissions : noPermissions) {
			AdminManager.Session attempt = manager.begin();
			assertFalse(String.valueOf(permissions), manager.complete(attempt, memberWithoutPermissions(permissions)));
			assertFalse(manager.isLoggedIn());
			assertNull(manager.session());
			assertNull(manager.getAuthData());
		}
	}

	@Test
	public void anyNonBlankPermissionOpensAdminAccess() {
		for (String permission : new String[] {"admin:full", "rank-changes", "x"}) {
			AdminManager fresh = new AdminManager(() -> account);
			assertTrue(permission, fresh.complete(fresh.begin(), memberWithoutPermissions(Collections.singletonList(permission))));
			assertTrue(fresh.isLoggedIn());
		}
	}

	@Test
	public void permissionsWithoutAuthenticationAreNotEnough() {
		AdminAuthResponse.AdminAuthData data = new AdminAuthResponse.AdminAuthData();
		data.setAuthenticated(false);
		data.setPermissions(Collections.singletonList("admin:full"));
		assertFalse(manager.complete(manager.begin(), data));
		assertFalse(manager.isLoggedIn());
	}

	@Test
	public void aLoginAttemptThatOutlivesALogoutSignsNobodyIn() {
		AdminManager.Session attempt = manager.begin();

		manager.clearSession();

		assertFalse(attempt.isCurrent());
		assertFalse(manager.complete(attempt, accepted()));
		assertFalse(manager.isLoggedIn());
		assertNull(manager.getAuthData());
	}

	@Test
	public void aLoginAttemptThatOutlivesAnAccountSwitchSignsNobodyIn() {
		AdminManager.Session attempt = manager.begin();

		account = B;

		assertFalse(attempt.isCurrent());
		assertFalse(manager.complete(attempt, accepted()));
		assertFalse(manager.isLoggedIn());
	}

	@Test
	public void aLoginAttemptWhileLoggedOutCanNeverSucceed() {
		account = -1;
		AdminManager.Session attempt = manager.begin();
		assertFalse(attempt.isCurrent());
		assertFalse(manager.complete(attempt, accepted()));
	}

	@Test
	public void logoutEndsTheSessionAndEveryViewHoldingIt() {
		manager.complete(manager.begin(), accepted());
		AdminManager.Session view = manager.session();
		assertTrue(view.isCurrent());

		manager.clearSession();

		assertFalse(view.isCurrent());
		assertFalse(manager.isLoggedIn());
		assertNull(manager.session());
		assertNull(manager.getAuthData());

		manager.complete(manager.begin(), accepted());
		assertFalse("signing in again does not revive the old views", view.isCurrent());
		assertTrue(manager.session().isCurrent());
	}

	@Test
	public void aSessionDoesNotCarryOverToAnotherGameAccount() {
		manager.complete(manager.begin(), accepted());
		AdminManager.Session view = manager.session();

		account = B;

		assertFalse(manager.isLoggedIn());
		assertFalse(view.isCurrent());
		assertTrue(manager.isForAnotherAccount());
		assertNull(manager.session());

		account = A;
		assertTrue("back on the account that signed in", manager.isLoggedIn());
		assertFalse(manager.isForAnotherAccount());
	}

	@Test
	public void thereIsNoCodeAnywhereInTheAdminState() throws Exception {
		for (Field field : AdminManager.class.getDeclaredFields()) {
			assertFalse(field.getName(), field.getName().toLowerCase(Locale.ROOT).contains("code"));
		}
		for (Method method : AdminManager.class.getDeclaredMethods()) {
			assertFalse(method.getName(), method.getName().toLowerCase(Locale.ROOT).contains("code"));
			for (Class<?> parameter : method.getParameterTypes()) {
				assertNotEquals("no method takes a credential string", String.class, parameter);
			}
		}
	}
}

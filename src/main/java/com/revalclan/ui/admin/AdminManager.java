package com.revalclan.ui.admin;

import com.revalclan.api.admin.AdminAuthResponse;

import java.util.function.LongSupplier;

/**
 * Admin session state. There is no secret in here: authority comes from the
 * linked account's token (sent by {@code RevalApiService}) and the server's
 * own admin list; this only remembers that the server accepted the logged-in
 * account for the admin views. Being linked is not enough: the server answers
 * {@code authenticated} for every linked member and lists the admin permissions
 * the account actually holds, so access needs at least one of those.
 * <p>
 * Every login attempt and every open admin view works against a {@link Session}.
 * {@link #clearSession()} (logout, shutdown) and an account switch make every
 * existing {@code Session} stale, so a callback that arrives late cannot sign
 * anyone in or touch the UI on behalf of the previous account.
 */
public class AdminManager {
	private static final long NO_ACCOUNT = -1;

	private final LongSupplier currentAccount;
	private AdminAuthResponse.AdminAuthData authData;
	private long accountHash = NO_ACCOUNT;
	private int generation;

	/** @param currentAccount the account hash of the logged-in game account, or -1 */
	public AdminManager(LongSupplier currentAccount) {
		this.currentAccount = currentAccount;
	}

	/** A claim on the admin state as it is right now; stale as soon as the session is cleared or the account changes. */
	public final class Session {
		private final int generation;
		private final long accountHash;

		private Session(int generation, long accountHash) {
			this.generation = generation;
			this.accountHash = accountHash;
		}

		public boolean isCurrent() {
			return AdminManager.this.isCurrent(this);
		}
	}

	/** Starts a login attempt for the logged-in account. */
	public synchronized Session begin() {
		return new Session(generation, currentAccount.getAsLong());
	}

	/**
	 * Records a successful server check. Returns false (and records nothing) when the attempt is
	 * stale, the server did not authenticate the account, or the account holds no admin permission.
	 */
	public synchronized boolean complete(Session session, AdminAuthResponse.AdminAuthData data) {
		if (!isCurrent(session) || !hasAdminAccess(data)) return false;
		this.authData = data;
		this.accountHash = session.accountHash;
		return true;
	}

	/** Authenticated <i>and</i> holding at least one permission (e.g. {@code admin:full}). */
	static boolean hasAdminAccess(AdminAuthResponse.AdminAuthData data) {
		if (data == null || !data.isAuthenticated() || data.getPermissions() == null) return false;
		for (String permission : data.getPermissions()) {
			if (permission != null && !permission.trim().isEmpty()) return true;
		}
		return false;
	}

	/** The signed-in admin session, or null when nobody (or a different account) is signed in. */
	public synchronized Session session() {
		return isLoggedIn() ? new Session(generation, accountHash) : null;
	}

	public synchronized boolean isLoggedIn() {
		return authData != null && accountHash != NO_ACCOUNT && accountHash == currentAccount.getAsLong();
	}

	public synchronized AdminAuthResponse.AdminAuthData getAuthData() {
		return authData;
	}

	/** True when a session exists for an account other than the logged-in one. */
	public synchronized boolean isForAnotherAccount() {
		return authData != null && accountHash != currentAccount.getAsLong();
	}

	public synchronized void clearSession() {
		generation++;
		authData = null;
		accountHash = NO_ACCOUNT;
	}

	private synchronized boolean isCurrent(Session session) {
		long current = currentAccount.getAsLong();
		return session.generation == generation && current != NO_ACCOUNT && session.accountHash == current;
	}
}

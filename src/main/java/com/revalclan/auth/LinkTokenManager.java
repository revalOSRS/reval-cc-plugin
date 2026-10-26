package com.revalclan.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.api.common.ApiEndpoints;
import com.revalclan.util.PluginVersion;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;

/**
 * The one place that knows which credential belongs to which game account.
 * <p>
 * {@code RevalApiService} and {@code WebhookService} both ask it for a
 * {@link Grant} before every request, so the rules live here once:
 * <ul>
 *   <li>A request for "the current account" only ever gets that account's
 *       token, and only when any account hash it carries equals that account.</li>
 *   <li>A buffered event is asked for by the account hash inside the event, so
 *       it keeps its own token even if someone else is logged in by now.</li>
 *   <li>Every logout, account switch and shutdown bumps a generation. A grant
 *       remembers the generation it was issued in, and a pairing remembers the
 *       one it started in, so a late callback can neither act as nor activate
 *       the credential of whoever happens to be logged in afterwards.</li>
 *   <li>Credentials only travel to {@link ApiOrigin}, over a client that never
 *       follows redirects ({@link SecureHttp}).</li>
 *   <li>A credential is removed only when the server says it is invalid
 *       ({@link #rejected}), never because this computer's clock thinks it has
 *       expired: a clock that is far ahead must not be able to erase a valid
 *       90-day token. Such a credential is simply not used, and the player is
 *       told why.</li>
 * </ul>
 * Tokens and Link codes are never logged and never put in messages.
 */
@Singleton
public class LinkTokenManager {
	/** Where a Link code comes from; administrators cannot get one in the public member app. */
	static final String CODE_SOURCE = "in your Reval account settings on revalosrs.ee (administrators: from your own member record in the Reval admin console)";
	public static final String LINK_INSTRUCTION = "Your Reval account is not linked. Create a Link code " + CODE_SOURCE + ", then paste it into the 'Link code' setting of the Reval Clan plugin.";
	static final String LOGIN_FIRST = "Log in to the game account you want to link. Your Link code is used automatically once you are logged in.";
	static final String BAD_FORMAT = "That does not look like a Link code. Copy the full 64-character code from where you created it: your Reval account settings, or for administrators your own member record in the Reval admin console.";
	static final String LINKED = "Your game account is now linked to Reval.";

	private static final long NO_ACCOUNT = -1;
	private static final Pattern LINK_CODE = Pattern.compile("[0-9a-f]{64}");
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final int MAX_BODY_BYTES = 64 * 1024;
	/** Below this the local clock is trusted as is: Date headers only have whole-second precision. */
	private static final long CLOCK_TOLERANCE_MS = 60_000;
	/** Beyond this a difference is not drift but a wrong clock; it is never used to stamp events. */
	static final long MAX_CLOCK_OFFSET_MS = 24L * 60 * 60 * 1000;

	private final CredentialStore store;
	private final Gson gson;
	private final ApiOrigin origin;
	private final String pairUrl;
	private final Clock clock;
	private final OkHttpClient pairClient;

	private final Object lock = new Object();
	private final AtomicLong generation = new AtomicLong();
	private volatile long currentAccountHash = NO_ACCOUNT;
	private final ConcurrentMap<Long, LinkCredential> cache = new ConcurrentHashMap<>();
	private final Set<String> noticed = ConcurrentHashMap.newKeySet();
	private volatile Consumer<String> messages = message -> {};
	private volatile LongConsumer onLinked = hash -> {};
	private volatile long serverClockOffsetMs;
	/** The server's Date was more than {@link #MAX_CLOCK_OFFSET_MS} from this computer's clock the last time it was seen. */
	private volatile boolean clockSuspect;

	// guarded by lock
	private String inFlightCode;
	private String attemptedCode;
	private long attemptedFor = NO_ACCOUNT;

	/**
	 * The login a request was made in. It goes stale when that account logs out, another logs in or the
	 * plugin shuts down, so a late answer can neither show an old account's data to the new one nor act
	 * for it. Independent of whether the request carried a token.
	 */
	public static final class Login {
		private final long generation;

		private Login(long generation) {
			this.generation = generation;
		}
	}

	/** A credential selected for one request, tied to the login that selected it (none for an event: it belongs to its account). */
	public static final class Grant {
		private final LinkCredential credential;
		private final Login login;

		private Grant(LinkCredential credential, Login login) {
			this.credential = credential;
			this.login = login;
		}

		public LinkCredential getCredential() {
			return credential;
		}

		/** The login the request belongs to, or null for an event-scoped grant. */
		public Login login() {
			return login;
		}

		public void applyTo(Request.Builder request) {
			request.header("Authorization", "Bearer " + credential.getToken());
		}
	}

	@Inject
	public LinkTokenManager(CredentialStore store, OkHttpClient httpClient, Gson gson) {
		this(store, httpClient, gson, ApiOrigin.PRODUCTION, ApiEndpoints.url(ApiEndpoints.AUTH_PAIR), Clock.systemUTC(),
			SecureHttp.API_TIMEOUT_MS);
	}

	LinkTokenManager(CredentialStore store, OkHttpClient httpClient, Gson gson, ApiOrigin origin, String pairUrl, Clock clock,
	                 long pairTimeoutMs) {
		this.store = store;
		this.gson = gson;
		this.origin = origin;
		this.pairUrl = pairUrl;
		this.clock = clock;
		this.pairClient = SecureHttp.harden(httpClient, origin, pairTimeoutMs);
	}

	/** The pairing client, for tests that look at how it is configured. */
	OkHttpClient pairClient() {
		return pairClient;
	}

	// ------------------------------------------------------------------ lifecycle

	/**
	 * @param messageSink shows a plain-language line to the player (any thread)
	 * @param onLinked    runs after the logged-in account was linked (any thread)
	 */
	public void start(Consumer<String> messageSink, LongConsumer onLinked) {
		this.messages = messageSink;
		this.onLinked = onLinked;
	}

	public void shutDown() {
		invalidate();
		synchronized (lock) {
			attemptedCode = null;
			attemptedFor = NO_ACCOUNT;
		}
		messages = message -> {};
		onLinked = hash -> {};
	}

	/**
	 * The game reports who is logged in. A new account starts a new session
	 * (generation) and loads only its own stored credential; a configured Link
	 * code is then tried for it. Returns whether the account changed.
	 */
	public boolean onAccount(long accountHash) {
		if (!isValidHash(accountHash)) return false;
		synchronized (lock) {
			if (accountHash == currentAccountHash) return false;
			generation.incrementAndGet();
			cache.clear();
			noticed.clear();
			currentAccountHash = accountHash;
		}
		credentialFor(accountHash);
		applyConfiguredLinkCode();
		return true;
	}

	/** Back at the login screen: nothing may act for the previous account any more. */
	public void onLoggedOut() {
		invalidate();
	}

	private void invalidate() {
		synchronized (lock) {
			generation.incrementAndGet();
			currentAccountHash = NO_ACCOUNT;
			cache.clear();
			noticed.clear();
		}
	}

	public long currentAccountHash() {
		return currentAccountHash;
	}

	// ------------------------------------------------------------------ credential lookup

	/**
	 * The credential for exactly this account that may be used right now, or null when the account is
	 * not linked or the link looks expired. "Looks expired" is judged against the best known time (see
	 * {@link #serverNowMillis}) and never deletes anything: a wrong clock cannot erase a valid token.
	 */
	public LinkCredential credentialFor(long accountHash) {
		LinkCredential stored = storedCredential(accountHash);
		return stored != null && !expired(stored) ? stored : null;
	}

	public boolean isLinked(long accountHash) {
		return credentialFor(accountHash) != null;
	}

	/** The stored credential even if it looks expired, loaded from storage when not in memory. */
	private LinkCredential storedCredential(long accountHash) {
		if (!isValidHash(accountHash)) return null;
		LinkCredential cached = cache.get(accountHash);
		if (cached != null) return cached;
		LinkCredential loaded = store.load(accountHash);
		if (loaded == null) return null;
		LinkCredential raced = cache.putIfAbsent(accountHash, loaded);
		return raced != null ? raced : loaded;
	}

	private boolean expired(LinkCredential credential) {
		return credential.isExpired(Instant.ofEpochMilli(serverNowMillis()));
	}

	/** Why a stored-but-unusable link cannot be used: this computer's clock is off, or it simply ran out. */
	private LinkException.Kind expiredKind() {
		return clockSuspect ? LinkException.Kind.CLOCK_OFF : LinkException.Kind.EXPIRED_LOCALLY;
	}

	/**
	 * Selects the credential for a request made on behalf of the logged-in
	 * account. Any account hash carried by the request (query or body) must be
	 * that account's. Fails before anything is sent when there is no usable link.
	 */
	public Grant grantForCurrentAccount(HttpUrl url, Long bodyAccountHash) throws LinkException {
		long hash;
		long startGeneration;
		synchronized (lock) {
			hash = currentAccountHash;
			startGeneration = generation.get();
		}
		if (!isValidHash(hash)) throw new LinkException(LinkException.Kind.NOT_LOGGED_IN);
		if (carriesOtherAccount(url, hash) || (bodyAccountHash != null && bodyAccountHash != hash)) {
			throw new LinkException(LinkException.Kind.ACCOUNT_MISMATCH);
		}
		if (!origin.allows(url)) throw new LinkException(LinkException.Kind.UNTRUSTED_ORIGIN);
		LinkCredential stored = storedCredential(hash);
		if (stored == null) throw new LinkException(LinkException.Kind.NOT_LINKED);
		if (expired(stored)) throw new LinkException(expiredKind());
		return new Grant(stored, new Login(startGeneration));
	}

	/**
	 * The credential for an event that was recorded for {@code eventAccountHash},
	 * whoever is logged in now. Null when that account has no usable link.
	 */
	public Grant grantForEvent(HttpUrl url, long eventAccountHash) {
		if (!isValidHash(eventAccountHash) || !origin.allows(url)) return null;
		LinkCredential credential = credentialFor(eventAccountHash);
		return credential == null ? null : new Grant(credential, null);
	}

	/** The login a request is being made in, or null when nobody is logged in (nothing to go stale). */
	public Login currentLogin() {
		synchronized (lock) {
			return isValidHash(currentAccountHash) ? new Login(generation.get()) : null;
		}
	}

	/** False once the account that made the request logged out, switched or the plugin shut down. */
	public boolean isCurrent(Login login) {
		return login == null || login.generation == generation.get();
	}

	/** Same, for the login a grant was issued in; an event grant belongs to its account, not to a login. */
	public boolean isCurrent(Grant grant) {
		return grant.login == null || (isCurrent(grant.login) && grant.credential.getAccountHash() == currentAccountHash);
	}

	/**
	 * The server said this very token is invalid (invalid_plugin_credential): forget it (unless a newer one
	 * replaced it meanwhile) and tell the player. The only way a stored credential is ever removed.
	 */
	public void rejected(Grant grant) {
		forget(grant.credential);
		notice(LinkException.Kind.EXPIRED, grant.credential.getAccountHash());
	}

	/** The server answered 401 for some other reason: the link stays, the player hears it may be temporary. */
	public void refused(Grant grant) {
		notice(LinkException.Kind.REFUSED, grant.credential.getAccountHash());
	}

	/** Events keep being refused for their timestamp: if the clock is the reason, say so (once per login). */
	public void envelopeRefused(long accountHash) {
		if (clockSuspect) notice(LinkException.Kind.CLOCK_OFF, accountHash);
	}

	/** Tells the logged-in player, once per login, why this account's events and changes are not going out. Silent for any other account. */
	public void noticeLinkRequired(long accountHash) {
		synchronized (lock) {
			if (inFlightCode != null) return;
		}
		LinkCredential stored = storedCredential(accountHash);
		if (stored == null) {
			notice(LinkException.Kind.NOT_LINKED, accountHash);
		} else if (expired(stored)) {
			notice(expiredKind(), accountHash);
		}
	}

	// ------------------------------------------------------------------ server time

	/**
	 * Events must be stamped within a few minutes of server time, so a PC clock that has
	 * drifted far off would get every event refused. The {@code Date} header of a response that
	 * really came from the server tells how far off the local clock is; small differences are ignored.
	 * <p>
	 * Only a network response from {@link ApiOrigin} counts (a public read that was redirected may have
	 * ended on another host), and only a plausible drift: a difference of more than a day means this
	 * computer's date is simply wrong, which is remembered ({@link #clockSuspect}) but never turned into
	 * event timestamps.
	 */
	public void observeServerTime(Response response) {
		if (response.cacheResponse() != null || !origin.allows(response.request().url())) return;
		Date date = response.headers().getDate("Date");
		if (date == null) return;
		long offset = date.getTime() - clock.millis();
		if (Math.abs(offset) > MAX_CLOCK_OFFSET_MS) {
			clockSuspect = true;
			return;
		}
		clockSuspect = false;
		serverClockOffsetMs = Math.abs(offset) > CLOCK_TOLERANCE_MS ? offset : 0;
	}

	/** Epoch milliseconds as the server's clock reads now, as far as is known and believable. */
	public long serverNowMillis() {
		return clock.millis() + serverClockOffsetMs;
	}

	// ------------------------------------------------------------------ pairing

	/** Uses the Link code setting for the logged-in account unless it is already linked. */
	public void applyConfiguredLinkCode() {
		long hash = currentAccountHash;
		if (!isValidHash(hash) || credentialFor(hash) != null) return;
		submitLinkCode(store.configuredLinkCode(), false);
	}

	/**
	 * Exchanges a Link code for the logged-in account. {@code explicit} is the
	 * player just entering it (they get feedback for every problem); otherwise
	 * it is the saved setting being tried again at login, which stays quiet and
	 * does not repeat a code it already tried for this account.
	 */
	public void submitLinkCode(String raw, boolean explicit) {
		String code = raw == null ? "" : raw.trim();
		if (code.isEmpty()) return;
		long hash;
		long startGeneration;
		String problem = null;
		synchronized (lock) {
			hash = currentAccountHash;
			startGeneration = generation.get();
			if (!isValidHash(hash)) {
				problem = explicit ? LOGIN_FIRST : "";
			} else if (!LINK_CODE.matcher(code).matches()) {
				problem = explicit ? BAD_FORMAT : "";
			} else if (!explicit && code.equals(attemptedCode) && hash == attemptedFor) {
				problem = "";
			} else if (code.equals(inFlightCode)) {
				problem = "";
			} else {
				attemptedCode = code;
				attemptedFor = hash;
				inFlightCode = code;
			}
		}
		if (problem != null) {
			if (!problem.isEmpty()) messages.accept(problem);
			return;
		}
		pair(code, hash, startGeneration);
	}

	private void pair(String code, long accountHash, long startGeneration) {
		JsonObject body = new JsonObject();
		body.addProperty("code", code);
		body.addProperty("accountHash", String.valueOf(accountHash));
		Request request;
		try {
			request = new Request.Builder()
				.url(pairUrl)
				.header("Accept", "application/json")
				.header("User-Agent", PluginVersion.userAgent())
				.post(RequestBody.create(JSON, gson.toJson(body).getBytes(StandardCharsets.UTF_8)))
				.build();
		} catch (RuntimeException e) {
			finishFailure(code, startGeneration, new LinkException(LinkException.Kind.INVALID_RESPONSE).getMessage());
			return;
		}
		pairClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				finishFailure(code, startGeneration, "Could not reach Reval to link your account. Check your connection and try again.");
			}

			@Override
			public void onResponse(Call call, Response response) {
				try (Response closeable = response) {
					observeServerTime(closeable);
					if (!closeable.isSuccessful()) {
						finishFailure(code, startGeneration, failureMessage(closeable.code(), ServerError.read(closeable, gson)));
						return;
					}
					String text = closeable.body() == null ? "" : closeable.peekBody(MAX_BODY_BYTES).string();
					LinkCredential credential = LinkCredential.fromPairResponse(gson.fromJson(text, JsonObject.class), accountHash);
					if (expired(credential)) {
						// The server just handed out a credential that is already over by the best known time:
						// either this computer's date is wrong, or the server's answer is not to be trusted.
						finishFailure(code, startGeneration, clockSuspect
							? LinkException.Kind.CLOCK_OFF.message() : LinkException.Kind.INVALID_RESPONSE.message());
						return;
					}
					finishSuccess(credential, code, startGeneration);
				} catch (LinkException e) {
					finishFailure(code, startGeneration, e.getMessage());
				} catch (Exception e) {
					finishFailure(code, startGeneration, new LinkException(LinkException.Kind.INVALID_RESPONSE).getMessage());
				}
			}
		});
	}

	private void finishSuccess(LinkCredential credential, String code, long startGeneration) {
		long hash = credential.getAccountHash();
		try {
			// Stored under the account the server bound it to, even when the player has
			// moved on: the one-use code is spent, and it will be found at that account's next login.
			store.save(credential);
		} catch (RuntimeException e) {
			finishFailure(code, startGeneration, "Your account was linked but the link could not be saved. Create a new Link code and try again.");
			return;
		}
		boolean active;
		synchronized (lock) {
			active = generation.get() == startGeneration && currentAccountHash == hash;
			if (active) cache.put(hash, credential);
		}
		store.clearLinkCodeIfUnchanged(code);
		release(code);
		if (active) {
			messages.accept(LINKED);
			onLinked.accept(hash);
		}
	}

	private void finishFailure(String code, long startGeneration, String message) {
		release(code);
		if (generation.get() == startGeneration) messages.accept(message);
	}

	private void release(String code) {
		synchronized (lock) {
			if (code.equals(inFlightCode)) inFlightCode = null;
		}
	}

	/** The server's own short explanation when it gave one (the pairing refusals carry a safe one), else a status-based fallback. */
	private String failureMessage(int status, ServerError error) {
		if (error.message() != null) return error.message();
		if (status == 403) {
			return "Reval refused this Link code for this game account. Log in with the game account that was approved in your Reval account settings, or ask clan staff to reset the link.";
		}
		if (status == 429) return "Too many link attempts. Please wait a few minutes and try again.";
		return "Reval could not link this account (HTTP " + status + "). Create a fresh Link code and try again.";
	}

	// ------------------------------------------------------------------ internals

	private void forget(LinkCredential credential) {
		cache.remove(credential.getAccountHash(), credential);
		store.deleteIfToken(credential.getAccountHash(), credential.getToken());
	}

	private void notice(LinkException.Kind kind, long accountHash) {
		if (!isValidHash(accountHash) || accountHash != currentAccountHash) return;
		if (noticed.add(generation.get() + ":" + accountHash + ":" + kind)) {
			messages.accept(kind.message());
		}
	}

	private static boolean carriesOtherAccount(HttpUrl url, long accountHash) {
		List<String> values = url.queryParameterValues("accountHash");
		for (String value : values) {
			if (!String.valueOf(accountHash).equals(value)) return true;
		}
		return false;
	}

	/** -1 means "nobody"; 0 is never a real account either. */
	public static boolean isValidHash(long accountHash) {
		return accountHash != NO_ACCOUNT && accountHash != 0;
	}
}

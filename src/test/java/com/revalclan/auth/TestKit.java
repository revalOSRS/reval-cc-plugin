package com.revalclan.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.api.common.ApiEndpoints;
import net.runelite.client.config.ConfigManager;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixtures: two linked accounts, an in-memory RuneLite config, a clock
 * that can be moved, and an OkHttp client that answers from a stub instead of
 * the network (calls run on the caller's thread so tests are deterministic).
 * Lives in {@code com.revalclan.auth} so tests in other packages can reach the
 * package-private constructors through these factories.
 */
public final class TestKit {
	public static final long ACCOUNT_A = 1_111_111_111_111L;
	public static final long ACCOUNT_B = 2_222_222_222_222L;
	public static final long ACCOUNT_C = 3_333_333_333_333L;
	public static final String TOKEN_A = "rp_" + repeat('a', 64);
	public static final String TOKEN_B = "rp_" + repeat('b', 64);
	public static final String TOKEN_NEW = "rp_" + repeat('c', 64);
	public static final String LINK_CODE = repeat('d', 64);
	public static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
	public static final String UUID_V4 = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
	public static final String PAIR_URL = ApiEndpoints.url(ApiEndpoints.AUTH_PAIR);

	private TestKit() {
	}

	/** An origin for a local test server, which only tests may trust. */
	public static ApiOrigin originFor(String origin) {
		return ApiOrigin.unsafeForTests(origin);
	}

	public static String repeat(char c, int count) {
		char[] chars = new char[count];
		java.util.Arrays.fill(chars, c);
		return new String(chars);
	}

	// ------------------------------------------------------------------ clock

	public static final class MutableClock extends Clock {
		private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

		public void advance(Duration duration) {
			now.updateAndGet(i -> i.plus(duration));
		}

		public void set(Instant instant) {
			now.set(instant);
		}

		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now.get(); }
	}

	// ------------------------------------------------------------------ config

	/**
	 * A RuneLite ConfigManager backed by a map, restricted to the plugin's own group. Every write is
	 * recorded: a credential must never be among them (the token lives in a file, not in the profile).
	 */
	public static final class FakeConfig {
		public final Map<String, String> values = new ConcurrentHashMap<>();
		public final List<String[]> writes = new CopyOnWriteArrayList<>();
		public final ConfigManager manager = mock(ConfigManager.class);

		public FakeConfig() {
			when(manager.getConfiguration(eq("revalclan"), anyString()))
				.thenAnswer(call -> values.get((String) call.getArgument(1)));
			when(manager.getConfigurationKeys(anyString())).thenAnswer(call -> {
				String prefix = call.getArgument(0);
				return values.keySet().stream().map(key -> "revalclan." + key)
					.filter(full -> full.startsWith(prefix)).collect(Collectors.toList());
			});
			doAnswer(call -> {
				writes.add(new String[] {call.getArgument(1), call.getArgument(2)});
				values.put(call.getArgument(1), call.getArgument(2));
				return null;
			}).when(manager).setConfiguration(eq("revalclan"), anyString(), anyString());
			doAnswer(call -> {
				values.remove((String) call.getArgument(1));
				return null;
			}).when(manager).unsetConfiguration(eq("revalclan"), anyString());
		}

		/** The key the 2.24 development builds used for the token in the profile config. */
		public static String legacyKey(long accountHash) {
			return CredentialStore.LEGACY_PREFIX + accountHash;
		}
	}

	public static JsonObject data(long accountHash, String token, Instant expiresAt) {
		JsonObject data = new JsonObject();
		data.addProperty("token", token);
		data.addProperty("credentialId", "3f2b8c1e-5d4a-4e6b-9a7c-1d2e3f4a5b6c");
		data.addProperty("accountId", 42);
		data.addProperty("accountHash", String.valueOf(accountHash));
		data.addProperty("expiresAt", expiresAt.toString());
		return data;
	}

	public static JsonObject pairResponse(long accountHash, String token) {
		JsonObject body = new JsonObject();
		body.addProperty("status", "success");
		body.add("data", data(accountHash, token, NOW.plus(Duration.ofDays(90))));
		return body;
	}

	public static LinkCredential credential(long accountHash, String token) throws LinkException {
		return LinkCredential.fromData(data(accountHash, token, NOW.plus(Duration.ofDays(90))), accountHash);
	}

	// ------------------------------------------------------------------ http

	public interface Responder {
		Response respond(Request request) throws IOException;
	}

	/** Records every request that reaches the "network" and answers from {@link #responder}. */
	public static final class Recorder implements Interceptor {
		public final List<Request> requests = new CopyOnWriteArrayList<>();
		public volatile Responder responder = request -> json(request, 200, "{\"status\":\"success\"}");

		@Override
		public Response intercept(Chain chain) throws IOException {
			Request request = chain.request();
			requests.add(request);
			return responder.respond(request);
		}

		public Request last() {
			return requests.get(requests.size() - 1);
		}
	}

	public static OkHttpClient client(Recorder recorder) {
		return new OkHttpClient.Builder()
			.dispatcher(new Dispatcher(new DirectExecutor()))
			.addInterceptor(recorder)
			.build();
	}

	public static Response json(Request request, int code, String body) {
		return new Response.Builder()
			.request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(code)
			.message("stub")
			.body(ResponseBody.create(MediaType.parse("application/json"), body))
			.build();
	}

	/** The response as a server whose clock reads {@code serverTime} would send it. */
	public static Response withDate(Response response, Instant serverTime) {
		return response.newBuilder()
			.header("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(serverTime.atOffset(ZoneOffset.UTC)))
			.build();
	}

	public static byte[] bytes(RequestBody body) throws IOException {
		Buffer buffer = new Buffer();
		body.writeTo(buffer);
		return buffer.readByteArray();
	}

	public static String bodyText(Request request) throws IOException {
		return request.body() == null ? "" : new String(bytes(request.body()), StandardCharsets.UTF_8);
	}

	public static String gunzip(byte[] bytes) throws IOException {
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
			return new String(readAll(in), StandardCharsets.UTF_8);
		}
	}

	private static byte[] readAll(java.io.InputStream in) throws IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] chunk = new byte[4096];
		for (int read; (read = in.read(chunk)) != -1; ) out.write(chunk, 0, read);
		return out.toByteArray();
	}

	/** Everything that identifies or authenticates the caller, in one string, for leak checks. */
	public static String everything(Request request) throws IOException {
		StringBuilder all = new StringBuilder(request.url().toString()).append('\n');
		for (String name : request.headers().names()) {
			for (String value : request.headers(name)) all.append(name).append(": ").append(value).append('\n');
		}
		if (request.body() != null) {
			if (request.body() instanceof MultipartBody) {
				for (MultipartBody.Part part : ((MultipartBody) request.body()).parts()) {
					all.append(new String(bytes(part.body()), StandardCharsets.ISO_8859_1));
				}
			} else {
				all.append(new String(bytes(request.body()), StandardCharsets.ISO_8859_1));
			}
		}
		return all.toString();
	}

	public static List<String> headerNames(Request request) {
		List<String> names = new ArrayList<>(request.headers().names());
		Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Runs submitted tasks inline, so OkHttp's async calls complete before {@code enqueue} returns. */
	private static final class DirectExecutor extends AbstractExecutorService {
		@Override public void execute(Runnable command) { command.run(); }
		@Override public void shutdown() { }
		@Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
		@Override public boolean isShutdown() { return false; }
		@Override public boolean isTerminated() { return false; }
		@Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
	}

	// ------------------------------------------------------------------ manager

	private static final Pattern TOKEN_SHAPE = Pattern.compile("rp_[0-9a-f]{64}");

	/**
	 * Everything a manager test needs to look at. Credentials live in a throw-away folder under the JVM's temp
	 * directory (never the player's RuneLite profile); call {@link #close()} after the test.
	 */
	public static final class Rig {
		public final Path directory;
		public final FakeConfig config = new FakeConfig();
		public final Recorder http = new Recorder();
		public final MutableClock clock = new MutableClock();
		public final List<String> messages = new CopyOnWriteArrayList<>();
		public final List<Long> linked = new CopyOnWriteArrayList<>();
		public final Gson gson = new Gson();
		public final CredentialStore store;
		public final OkHttpClient client = client(http);
		public final LinkTokenManager tokens;

		public Rig() {
			this(ApiOrigin.PRODUCTION);
		}

		/** The credential rules point at {@code origin}, e.g. a loopback server (see {@link #originFor}). */
		public Rig(ApiOrigin origin) {
			try {
				directory = Files.createTempDirectory("reval-credentials-test");
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			store = new CredentialStore(config.manager, gson, directory.resolve("link"));
			tokens = new LinkTokenManager(store, client, gson, origin, PAIR_URL, clock, SecureHttp.API_TIMEOUT_MS);
			tokens.start(messages::add, linked::add);
		}

		/** Stores a valid 90-day credential for the account, as a previous pairing would have. */
		public void link(long accountHash, String token) {
			try {
				store.save(credential(accountHash, token));
			} catch (LinkException e) {
				throw new IllegalStateException(e);
			}
		}

		public boolean hasCredential(long accountHash) {
			return Files.exists(store.fileFor(accountHash));
		}

		/** Fails if the token ever reached the RuneLite config, then removes the temporary folder. */
		public void close() throws IOException {
			try {
				for (String[] write : config.writes) {
					if (write[0].startsWith(CredentialStore.LEGACY_PREFIX) || TOKEN_SHAPE.matcher(write[1]).find()) {
						throw new AssertionError("a credential was written to the RuneLite config under " + write[0]);
					}
				}
			} finally {
				deleteTree(directory);
			}
		}
	}

	public static void deleteTree(Path root) throws IOException {
		if (root == null || !Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).collect(Collectors.toList())) {
				Files.deleteIfExists(path);
			}
		}
	}
}

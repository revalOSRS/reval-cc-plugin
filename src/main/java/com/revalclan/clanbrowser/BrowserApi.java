package com.revalclan.clanbrowser;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.api.common.ApiEndpoints;
import com.revalclan.auth.ApiOrigin;
import com.revalclan.auth.LinkException;
import com.revalclan.auth.LinkTokenManager;
import com.revalclan.auth.SecureHttp;
import com.revalclan.auth.ServerError;
import com.revalclan.util.PluginVersion;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Cancellable reads. Callbacks run on the EDT; a cached view is applied inline when already on it.
 * <p>
 * A view that names an account (the player's own collection log, an {@code accountHash} query) is private: the
 * backend wants the bearer for it, so it is only requested for the logged-in, linked account, through the
 * credentialed client (same origin only, no redirects, no cache, finite timeout). Everything that reaches the
 * player from such a request, a fresh answer or a cached view alike, is dropped once that login has ended
 * (logout, account switch, shutdown) or the link is gone. Every other read is public and carries no token.
 */
@Singleton
public class BrowserApi {
    private final OkHttpClient http;
    private final Gson gson;
    private final LinkTokenManager tokens;
    private final ApiOrigin origin;
    private final String baseUrl;
    private volatile OkHttpClient credentialedClient;
    private volatile BrowserModels.Catalogue cached;
    private volatile long cachedAt;
    private final Map<String, Saved> views = new LinkedHashMap<>(16, .75f, true);

    /** A cached view; a private one remembers the login it was fetched in and is only good for that login. */
    private static final class Saved {
        final BrowserModels.View view;
        final LinkTokenManager.Login login;
        Saved(BrowserModels.View view, LinkTokenManager.Login login) { this.view = view; this.login = login; }
    }

    @Inject public BrowserApi(OkHttpClient http, Gson gson, LinkTokenManager tokens) {
        this(http, gson, tokens, ApiOrigin.PRODUCTION, System.getProperty("reval.browserApi", ApiEndpoints.BASE_URL));
    }

    BrowserApi(OkHttpClient http, Gson gson, LinkTokenManager tokens, ApiOrigin origin, String baseUrl) {
        this.http = http; this.gson = gson; this.tokens = tokens; this.origin = origin; this.baseUrl = baseUrl;
    }

    /** Client for requests that carry a token: no redirects, no cache, trusted origin only. */
    OkHttpClient credentialedClient() {
        OkHttpClient client = credentialedClient;
        if (client == null) { client = SecureHttp.harden(http, origin, SecureHttp.API_TIMEOUT_MS); credentialedClient = client; }
        return client;
    }

    public Call catalogue(Consumer<BrowserModels.Catalogue> success, Consumer<String> failure) {
        if (cached != null && System.currentTimeMillis() - cachedAt < 3600_000) {
            SwingUtilities.invokeLater(() -> success.accept(cached));
            return null;
        }
        return get("catalogue", null, BrowserModels.Catalogue.class, null, data -> {
            BrowserAliases.apply(data);
            cached = data; cachedAt = System.currentTimeMillis(); success.accept(data);
        }, failure);
    }

    public Call view(BrowserCommand command, long accountHash, boolean refresh, Consumer<BrowserModels.View> success, Consumer<String> failure) {
        command = command.resolve(cached);
        Map<String, String> parameters = command.parameters(accountHash);
        Map<String, String> identity = new LinkedHashMap<>(parameters);
        identity.replaceAll((name, value) -> name.equals("accountHash") ? value :
            name.equals("category") || name.equals("boss") ? BrowserCommand.normalize(value) : value.toLowerCase(Locale.ROOT));
        if ("all".equals(identity.get("tier"))) identity.remove("tier");
        if (java.util.Arrays.asList("all", "overall", "all bosses").contains(identity.get("boss"))) identity.remove("boss");
        String key = url("view", identity).toString();
        if (refresh) parameters.put("refresh", "true");

        // Before anything is read from the cache: a private view needs a usable link for the logged-in account right now.
        LinkTokenManager.Grant grant = null;
        if (parameters.containsKey("accountHash")) {
            try {
                grant = tokens.grantForCurrentAccount(url("view", parameters), null);
            } catch (LinkException error) {
                dropPrivateViews();
                String message = error.getMessage();
                onEdt(() -> failure.accept(message));
                return null;
            }
        }
        final LinkTokenManager.Grant sent = grant;

        BrowserModels.View saved = null;
        synchronized (views) {
            views.values().removeIf(entry -> entry.login != null && !tokens.isCurrent(entry.login));
            if (refresh) views.remove(key);
            Saved entry = views.get(key);
            if (entry != null) saved = entry.view;
        }
        if (saved != null) {
            final BrowserModels.View hit = saved;
            // Re-checked when it is applied: the account may change between this call and the EDT running it.
            onEdt(() -> { if (sent == null || tokens.isCurrent(sent)) success.accept(hit); });
            return null;
        }
        return get("view", parameters, BrowserModels.View.class, sent, data -> {
            synchronized (views) {
                views.put(key, new Saved(data, sent == null ? null : sent.login()));
                if (views.size() > 64) views.remove(views.keySet().iterator().next());
            }
            success.accept(data);
        }, failure);
    }

    /** Nothing private is kept for an account that can no longer be served (logged out, switched, unlinked). */
    private void dropPrivateViews() {
        synchronized (views) { views.values().removeIf(entry -> entry.login != null); }
    }

    private HttpUrl url(String path, Map<String, String> parameters) {
        HttpUrl parsed = HttpUrl.parse(baseUrl.replaceAll("/+$", "") + "/clan-browser/" + path);
        if (parsed == null) throw new IllegalArgumentException("Invalid clan browser API address");
        HttpUrl.Builder builder = parsed.newBuilder();
        if (parameters != null) parameters.forEach(builder::addQueryParameter);
        return builder.build();
    }

    private <T> Call get(String path, Map<String, String> parameters, Class<T> type, LinkTokenManager.Grant grant,
                         Consumer<T> success, Consumer<String> failure) {
        Request.Builder builder = new Request.Builder().url(url(path, parameters))
            .header("Accept", "application/json").header("User-Agent", PluginVersion.userAgent()).get();
        if (grant != null) grant.applyTo(builder);
        Call call = (grant != null ? credentialedClient() : http).newCall(builder.build());
        call.enqueue(new Callback() {
            @Override public void onFailure(Call ignored, IOException error) {
                deliver(call, grant, () -> failure.accept("The clan browser could not connect. Please try again."));
            }
            @Override public void onResponse(Call ignored, Response response) {
                try (Response closed = response) {
                    if (grant != null) {
                        tokens.observeServerTime(closed);
                        if (!tokens.isCurrent(grant)) return;
                        if (closed.code() == 401) {
                            // Only the server saying the token itself is bad removes the link; any other 401 keeps it.
                            boolean revoked = ServerError.read(closed, gson).is(ServerError.INVALID_PLUGIN_CREDENTIAL);
                            if (revoked) { tokens.rejected(grant); dropPrivateViews(); }
                            String message = new LinkException(revoked ? LinkException.Kind.EXPIRED : LinkException.Kind.REFUSED).getMessage();
                            deliver(call, grant, () -> failure.accept(message));
                            return;
                        }
                    }
                    if (closed.body() == null) throw new IOException("Empty response");
                    JsonObject envelope = gson.fromJson(closed.body().string(), JsonObject.class);
                    if (!closed.isSuccessful() || envelope == null || !envelope.has("data") || !envelope.has("status") || !"success".equals(envelope.get("status").getAsString())) {
                        String message = envelope != null && envelope.has("message") ? envelope.get("message").getAsString() : "This view could not be loaded. Please try again.";
                        deliver(call, grant, () -> failure.accept(message));
                        return;
                    }
                    T data = gson.fromJson(envelope.get("data"), type);
                    if (data == null) throw new IOException("Empty view");
                    deliver(call, grant, () -> success.accept(data));
                } catch (Exception error) {
                    deliver(call, grant, () -> failure.accept("This view could not be read. Please refresh and try again."));
                }
            }
        });
        return call;
    }

    /** On the EDT, unless the call was cancelled or (for a credentialed call) its login has ended in the meantime. */
    private void deliver(Call call, LinkTokenManager.Grant grant, Runnable callback) {
        SwingUtilities.invokeLater(() -> {
            if (call.isCanceled() || (grant != null && !tokens.isCurrent(grant))) return;
            callback.run();
        });
    }

    private static void onEdt(Runnable callback) {
        if (SwingUtilities.isEventDispatchThread()) callback.run();
        else SwingUtilities.invokeLater(callback);
    }
}

package com.revalclan.clanbrowser;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.api.common.ApiEndpoints;
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

/** Cancellable reads. Callbacks run on the EDT; a cached view is applied inline when already on it. */
@Singleton
public class BrowserApi {
    private final OkHttpClient http;
    private final Gson gson;
    private volatile BrowserModels.Catalogue cached;
    private volatile long cachedAt;
    private final Map<String, BrowserModels.View> views = new LinkedHashMap<>(16, .75f, true);

    @Inject public BrowserApi(OkHttpClient http, Gson gson) { this.http = http; this.gson = gson; }

    public Call catalogue(Consumer<BrowserModels.Catalogue> success, Consumer<String> failure) {
        if (cached != null && System.currentTimeMillis() - cachedAt < 3600_000) {
            SwingUtilities.invokeLater(() -> success.accept(cached));
            return null;
        }
        return get("catalogue", null, BrowserModels.Catalogue.class, data -> {
            cached = data; cachedAt = System.currentTimeMillis(); success.accept(data);
        }, failure);
    }

    public Call view(BrowserCommand command, long accountHash, Consumer<BrowserModels.View> success, Consumer<String> failure) {
        return view(command, accountHash, false, success, failure);
    }

    public Call view(BrowserCommand command, long accountHash, boolean refresh, Consumer<BrowserModels.View> success, Consumer<String> failure) {
        command = command.resolve(cached);
        Map<String, String> parameters = command.parameters(accountHash);
        Map<String, String> identity = new LinkedHashMap<>(parameters);
        identity.replaceAll((name, value) -> name.equals("accountHash") ? value :
            name.equals("category") || name.equals("boss") ? BrowserCommand.normalize(value) : value.toLowerCase(Locale.ROOT));
        if ("all".equals(identity.get("tier"))) identity.remove("tier");
        if (java.util.Arrays.asList("all", "overall", "all bosses").contains(identity.get("boss"))) identity.remove("boss");
        String key = endpoint("view", identity).toString();
        BrowserModels.View saved;
        synchronized (views) { if (refresh) views.remove(key); saved = views.get(key); }
        if (saved != null) {
            if (SwingUtilities.isEventDispatchThread()) success.accept(saved);
            else SwingUtilities.invokeLater(() -> success.accept(saved));
            return null;
        }
        if (refresh) parameters.put("refresh", "true");
        return get("view", parameters, BrowserModels.View.class, data -> {
            synchronized (views) {
                views.put(key, data);
                if (views.size() > 64) views.remove(views.keySet().iterator().next());
            }
            success.accept(data);
        }, failure);
    }

    public static HttpUrl endpoint(String path, Map<String, String> parameters) {
        String base = System.getProperty("reval.browserApi", ApiEndpoints.BASE_URL);
        HttpUrl parsed = HttpUrl.parse(base.replaceAll("/+$", "") + "/clan-browser/" + path);
        if (parsed == null) throw new IllegalArgumentException("Invalid clan browser API address");
        HttpUrl.Builder builder = parsed.newBuilder();
        if (parameters != null) parameters.forEach(builder::addQueryParameter);
        return builder.build();
    }

    private <T> Call get(String path, Map<String, String> parameters, Class<T> type, Consumer<T> success, Consumer<String> failure) {
        Request request = new Request.Builder().url(endpoint(path, parameters))
            .header("Accept", "application/json").header("User-Agent", PluginVersion.userAgent()).get().build();
        Call call = http.newCall(request);
        call.enqueue(new Callback() {
            @Override public void onFailure(Call ignored, IOException error) {
                deliver(call, () -> failure.accept("The clan browser could not connect. Please try again."));
            }
            @Override public void onResponse(Call ignored, Response response) {
                try (Response closed = response) {
                    if (closed.body() == null) throw new IOException("Empty response");
                    JsonObject envelope = gson.fromJson(closed.body().string(), JsonObject.class);
                    if (!closed.isSuccessful() || envelope == null || !envelope.has("data") || !envelope.has("status") || !"success".equals(envelope.get("status").getAsString())) {
                        String message = envelope != null && envelope.has("message") ? envelope.get("message").getAsString() : "This view could not be loaded. Please try again.";
                        deliver(call, () -> failure.accept(message));
                        return;
                    }
                    T data = gson.fromJson(envelope.get("data"), type);
                    if (data == null) throw new IOException("Empty view");
                    deliver(call, () -> success.accept(data));
                } catch (Exception error) {
                    deliver(call, () -> failure.accept("This view could not be read. Please refresh and try again."));
                }
            }
        });
        return call;
    }

    private static void deliver(Call call, Runnable callback) {
        SwingUtilities.invokeLater(() -> { if (!call.isCanceled()) callback.run(); });
    }
}

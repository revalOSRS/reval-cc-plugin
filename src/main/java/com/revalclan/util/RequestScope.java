package com.revalclan.util;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Work owned by one plugin, account or view lifetime. Cancellation is terminal:
 * every tracked future completes, and its HTTP call is cancelled. UI callbacks
 * queued on another thread use guard() to check the same lifetime when run.
 * No external callback runs under a scope monitor.
 */
public final class RequestScope {
	private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
	private volatile boolean active = true;

	public boolean isActive() { return active; }

	public Runnable guard(Runnable callback) {
		return () -> { if (active) callback.run(); };
	}

	public <T> CompletableFuture<T> track(CompletableFuture<T> future) {
		pending.add(future);
		future.whenComplete((value, error) -> pending.remove(future));
		if (!active) future.cancel(false);
		return future;
	}

	public void cancel() {
		active = false;
		pending.forEach(future -> future.cancel(false));
	}

	@FunctionalInterface
	public interface ResponseReader<T> { T read(Response response) throws Exception; }

	/** Response ownership stays here, including late responses after cancellation. */
	public <T> CompletableFuture<T> request(OkHttpClient http, Request request, ResponseReader<T> reader) {
		CompletableFuture<T> result = track(new CompletableFuture<>());
		if (result.isDone()) return result;
		try {
			Call call = http.newCall(request);
			result.whenComplete((value, error) -> { if (result.isCancelled()) call.cancel(); });
			if (result.isDone()) return result;
			call.enqueue(new Callback() {
				@Override public void onFailure(Call ignored, IOException error) {
					result.completeExceptionally(error);
				}
				@Override public void onResponse(Call ignored, Response response) {
					T value;
					try (Response closed = response) {
						if (result.isDone()) return;
						value = reader.read(closed);
					} catch (Exception error) {
						result.completeExceptionally(error);
						return;
					}
					result.complete(value);
				}
			});
		} catch (RuntimeException error) {
			result.completeExceptionally(error);
		}
		return result;
	}
}

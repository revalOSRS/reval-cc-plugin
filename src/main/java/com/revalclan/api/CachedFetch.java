package com.revalclan.api;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** One keyed response, one shared fetch, and a short failure backoff. Uses monotonic time. */
final class CachedFetch<T> {
	private final long lifetime;
	private final LongSupplier clock;
	private Object key;
	private T value;
	private long fetchedAt, failedAt;
	private CompletableFuture<T> pending, cached, failure;
	private static final long RETRY_NANOS = TimeUnit.MINUTES.toNanos(1);

	CachedFetch(long lifetimeMillis) { this(lifetimeMillis, System::nanoTime); }
	CachedFetch(long lifetimeMillis, LongSupplier clock) {
		this.lifetime = TimeUnit.MILLISECONDS.toNanos(lifetimeMillis);
		this.clock = clock;
	}

	CompletableFuture<T> get(Object requestKey, Supplier<CompletableFuture<T>> loader) {
		CompletableFuture<T> old = null, started = null, answer;
		synchronized (this) {
			if (!Objects.equals(key, requestKey)) { old = reset(); key = requestKey; }
			long now = clock.getAsLong();
			if (cached != null && (lifetime == 0 || now - fetchedAt < lifetime)) answer = cached;
			else if (pending != null) answer = pending;
			else if (failure != null && now - failedAt < RETRY_NANOS) answer = failure;
			else { started = new CompletableFuture<>(); answer = pending = started; }
		}
		if (old != null) old.cancel(false);
		if (started != null) start(started, loader);
		return answer;
	}

	private void start(CompletableFuture<T> result, Supplier<CompletableFuture<T>> loader) {
		CompletableFuture<T> source;
		try { source = loader.get(); }
		catch (RuntimeException error) { source = CompletableFuture.failedFuture(error); }
		CompletableFuture<T> request = source;
		result.whenComplete((data, error) -> { if (result.isCancelled()) request.cancel(false); });
		source.whenComplete((data, error) -> {
			synchronized (this) {
				if (pending == result) {
					pending = null;
					if (error == null) { value = data; cached = result; fetchedAt = clock.getAsLong(); failure = null; }
					else if (!(error instanceof CancellationException)) { failure = result; failedAt = clock.getAsLong(); }
				}
			}
			if (error == null) result.complete(data); else result.completeExceptionally(error);
		});
	}

	synchronized T peek() { return value; }
	void clear() {
		CompletableFuture<T> old;
		synchronized (this) { old = reset(); }
		if (old != null) old.cancel(false);
	}
	private CompletableFuture<T> reset() {
		CompletableFuture<T> old = pending;
		value = null; cached = null; pending = null; failure = null;
		return old;
	}
}

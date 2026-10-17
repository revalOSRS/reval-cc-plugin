package com.revalclan.api;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class CachedFetchTest {
	@Test public void pendingAndFreshResponsesShareOneFetchUntilMonotonicExpiry() {
		AtomicLong clock = new AtomicLong();
		CachedFetch<String> cache = new CachedFetch<>(100, clock::get);
		CompletableFuture<String> source = new CompletableFuture<>();
		CompletableFuture<String> first = cache.get(null, () -> source);
		for (int i = 0; i < 1000; i++) assertSame(first, cache.get(null, () -> { fail("Duplicate fetch"); return null; }));
		source.complete("one");
		assertEquals("one", first.join());
		assertSame(first, cache.get(null, () -> { fail("Still fresh"); return null; }));
		clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));
		assertEquals("two", cache.get(null, () -> CompletableFuture.completedFuture("two")).join());
	}

	@Test public void failuresBackOffAndRetryAfterOneMinute() {
		AtomicLong clock = new AtomicLong();
		AtomicInteger attempts = new AtomicInteger();
		CachedFetch<String> cache = new CachedFetch<>(100, clock::get);
		for (int i = 0; i < 1000; i++) {
			assertTrue(cache.get(null, () -> {
				attempts.incrementAndGet();
				return CompletableFuture.failedFuture(new IOException("offline"));
			}).isCompletedExceptionally());
		}
		assertEquals(1, attempts.get());
		clock.addAndGet(TimeUnit.MINUTES.toNanos(1));
		assertEquals("recovered", cache.get(null, () -> CompletableFuture.completedFuture("recovered")).join());
	}

	@Test public void accountChangesCancelPendingWorkAndIgnoreItsLateResponse() {
		CachedFetch<String> cache = new CachedFetch<>(100);
		CompletableFuture<String> oldSource = new CompletableFuture<>();
		CompletableFuture<String> old = cache.get(1L, () -> oldSource);
		assertEquals("new account", cache.get(2L, () -> CompletableFuture.completedFuture("new account")).join());
		assertTrue(old.isCancelled());
		assertTrue(oldSource.isCancelled());
		oldSource.complete("old account");
		assertEquals("new account", cache.peek());
	}

	@Test public void clearingCancelsAllWaitersAndTheirCallbacksRunOutsideTheCacheMonitor() {
		CachedFetch<String> cache = new CachedFetch<>(100);
		CompletableFuture<String> old = cache.get(null, CompletableFuture::new);
		java.util.concurrent.atomic.AtomicBoolean callbackLock = new java.util.concurrent.atomic.AtomicBoolean(true);
		old.whenComplete((data, error) -> callbackLock.set(Thread.holdsLock(cache)));
		cache.clear();
		assertTrue(old.isCancelled());
		assertFalse(callbackLock.get());
		assertEquals("restart", cache.get(null, () -> CompletableFuture.completedFuture("restart")).join());
	}

	@Test public void successfulCallbacksRunOutsideTheCacheMonitor() {
		CachedFetch<String> cache = new CachedFetch<>(100);
		CompletableFuture<String> source = new CompletableFuture<>();
		CompletableFuture<Boolean> checked = cache.get(null, () -> source).thenApply(data -> Thread.holdsLock(cache));
		source.complete("data");
		assertFalse(checked.join());
	}
}

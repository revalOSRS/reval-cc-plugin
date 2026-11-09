package com.revalclan.util;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * One admission lease covers preparation and any resulting transport. Completing
 * the caller, including cancellation, does not release retained request bodies.
 */
final class WebhookDelivery {
	private final RequestScope owner;
	private final CompletableFuture<JsonObject> result;
	private final Runnable release;
	private final AtomicBoolean preparationFinished = new AtomicBoolean();
	// Attempts are sequential; final preparation completes only after the last queued attempt exits.
	private CompletableFuture<Void> transportFinished = CompletableFuture.completedFuture(null);

	WebhookDelivery(RequestScope owner, Runnable release) {
		this.owner = owner;
		this.result = owner.track(new CompletableFuture<>());
		this.release = release;
	}

	CompletableFuture<JsonObject> result() { return result; }

	CompletableFuture<JsonObject> send(OkHttpClient http, Request request, RequestScope.ResponseReader<JsonObject> reader) {
		RequestScope.Exchange<JsonObject> transport = owner.exchange(http, request, reader);
		transportFinished = CompletableFuture.allOf(transportFinished, transport.finished());
		result.whenComplete((response, error) -> { if (result.isCancelled()) transport.result().cancel(false); });
		return transport.result();
	}

	void finishPreparation() {
		if (preparationFinished.compareAndSet(false, true)) transportFinished.thenRun(release);
	}
}

package com.revalclan.collectionlog;

import net.runelite.api.widgets.Widget;

import javax.inject.Singleton;

/**
 * State for the guided "sync your collection log" flow: armed from the side
 * panel, the {@link SyncGuideOverlay} then highlights the collection log
 * burger menu and the injected "Sync Reval" entry until the sync runs,
 * the guide times out, or it is disarmed.
 */
@Singleton
public class SyncGuide {
	private static final long TIMEOUT_MS = 3 * 60_000;

	private volatile long armedAt = 0;
	private volatile Widget syncButtonWidget;

	public void arm() {
		armedAt = System.currentTimeMillis();
	}

	public void disarm() {
		armedAt = 0;
	}

	public boolean isArmed() {
		return armedAt > 0 && System.currentTimeMillis() - armedAt < TIMEOUT_MS;
	}

	/** How long the guide has been armed, in milliseconds */
	public long armedForMs() {
		return armedAt > 0 ? System.currentTimeMillis() - armedAt : 0;
	}

	public void setSyncButtonWidget(Widget widget) {
		this.syncButtonWidget = widget;
	}

	public Widget getSyncButtonWidget() {
		return syncButtonWidget;
	}
}

/*
 * Portions of this file are derived from or inspired by the RuneProfile plugin
 * Copyright (c) RuneProfile
 * Licensed under the BSD 2-Clause License
 */
package com.revalclan.collectionlog;

import com.revalclan.RevalClanConfig;
import com.revalclan.debug.DebugDataDumper;
import com.revalclan.notifiers.SyncNotifier;
import com.revalclan.util.ClanValidator;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Adds a "Sync Reval" button to the collection log dropdown menu
 * Similar to RuneProfile's implementation, in order to avoid overlapping buttons
 */
@Slf4j
@Singleton
public class CollectionLogSyncButton {
	private static final int DRAW_BURGER_MENU = 7812;
	private static final int FONT_COLOR = 0xFF981F;
	private static final int FONT_COLOR_ACTIVE = 0xFFFFFF;
	private static final String BUTTON_TEXT = "Sync Reval";
	private static final String DEBUG_BUTTON_TEXT = "Reval Debug Dump";
	private static final int SYNC_DELAY_TICKS = 3; // Wait 3 ticks for search to complete

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private CollectionLogManager collectionLogManager;

	@Inject
	private SyncNotifier syncNotifier;

	@Inject
	private RevalClanConfig config;

	@Inject
	private DebugDataDumper debugDumper;

	private int baseMenuHeight = -1;
	private int lastAttemptedSync = -1;
	private int pendingSyncTick = -1;
	private int pendingDebugDumpTick = -1;

	public void startUp() {
		eventBus.register(this);
	}

	public void shutDown() {
		eventBus.unregister(this);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event) {
		if (event.getScriptId() != DRAW_BURGER_MENU) {
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		int menuId = (int) args[3];

		try {
			addButton(menuId, BUTTON_TEXT, "Sync collection log to Reval", this::onButtonClick);
			if (config.debugMode()) {
				addButton(menuId, DEBUG_BUTTON_TEXT, "Dump raw data to local files (debug)", this::onDebugButtonClick);
			}
		} catch (Exception e) {
			log.debug("Failed to add Reval button to menu: {}", e.getMessage());
		}
	}

	private void onButtonClick() {
		// Rate limit: 30 seconds between syncs
		if (lastAttemptedSync != -1 && lastAttemptedSync + 50 > client.getTickCount()) {
			int secondsRemaining = (int) Math.round((lastAttemptedSync + 50 - client.getTickCount()) * 0.6);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
				"Reval: Please wait " + secondsRemaining + " seconds before syncing again.", "");
			return;
		}
		lastAttemptedSync = client.getTickCount();

		// Validate clan membership
		if (!ClanValidator.validateClan(client)) {
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Reval: You must be in the Reval clan to sync.", "");
			return;
		}

		// Clear existing data and trigger collection log scan.
		// Deferred: the click callback runs inside a script, and runScript is
		// not reentrant (asserts in dev clients run with -ea)
		clientThread.invokeLater(() -> {
			collectionLogManager.clearObtainedItems();

			// Trigger the search to scan all items
			client.menuAction(-1, 40697932, MenuAction.CC_OP, 1, -1, "Search", null);
			client.runScript(2240);

			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Reval: Scanning collection log...", "");

			// Schedule the sync after a short delay to allow the search to populate items
			scheduleSync();
		});
	}

	/**
	 * Debug-mode button: runs the same collection log scan as the sync button,
	 * then dumps everything to local files instead of sending to the backend.
	 * No clan validation — this is purely local test tooling.
	 */
	private void onDebugButtonClick() {
		// Deferred for the same script-reentrancy reason as onButtonClick
		clientThread.invokeLater(() -> {
			collectionLogManager.clearObtainedItems();

			client.menuAction(-1, 40697932, MenuAction.CC_OP, 1, -1, "Search", null);
			client.runScript(2240);

			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Reval Debug: Scanning collection log for dump...", "");
			pendingDebugDumpTick = client.getTickCount() + SYNC_DELAY_TICKS;
		});
	}

	private void scheduleSync() {
		pendingSyncTick = client.getTickCount() + SYNC_DELAY_TICKS;
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (pendingSyncTick != -1 && client.getTickCount() >= pendingSyncTick) {
			pendingSyncTick = -1;
			performSync();
		}

		if (pendingDebugDumpTick != -1 && client.getTickCount() >= pendingDebugDumpTick) {
			pendingDebugDumpTick = -1;
			try {
				debugDumper.dumpAll();
				debugDumper.dumpClogRawStructure();
			} catch (Exception e) {
				log.warn("Debug dump failed", e);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Reval Debug: dump failed - " + e.getMessage(), "");
			}
		}
	}

	private void performSync() {
		try {
			syncNotifier.triggerSync();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
				"Reval: Synced account data successfully!", "");
		} catch (Exception e) {
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Reval: Failed to sync. Please try again.", "");
		}
	}

	private void addButton(int menuId, String buttonText, String actionText, Runnable onClick)
			throws NullPointerException, NoSuchElementException {
		// Don't add button if viewing from POH adventure log (viewing someone else's log)
		boolean isOpenedFromAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
		if (isOpenedFromAdventureLog) return;

		Widget menu = Objects.requireNonNull(client.getWidget(menuId));
		Widget[] menuChildren = Objects.requireNonNull(menu.getChildren());

		if (baseMenuHeight == -1) {
			baseMenuHeight = menu.getOriginalHeight();
		}

		// Find the last rectangle and text widgets to use as templates
		// (after a Reval button is added, its own widgets become the templates,
		// so subsequent buttons stack below it)
		List<Widget> reversedMenuChildren = new ArrayList<>(Arrays.asList(menuChildren));
		Collections.reverse(reversedMenuChildren);

		Widget lastRectangle = reversedMenuChildren.stream()
			.filter(w -> w.getType() == WidgetType.RECTANGLE)
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("No RECTANGLE widget found in menu"));

		Widget lastText = reversedMenuChildren.stream()
			.filter(w -> w.getType() == WidgetType.TEXT)
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("No TEXT widget found in menu"));

		final int buttonHeight = lastRectangle.getHeight();
		final int buttonY = lastRectangle.getOriginalY() + buttonHeight;

		// Check if button already exists
		final boolean existingButton = Arrays.stream(menuChildren)
			.anyMatch(w -> w.getText() != null && w.getText().equals(buttonText));

		if (!existingButton) {
			// Create background rectangle
			final Widget background = menu.createChild(WidgetType.RECTANGLE)
				.setOriginalWidth(lastRectangle.getOriginalWidth())
				.setOriginalHeight(lastRectangle.getOriginalHeight())
				.setOriginalX(lastRectangle.getOriginalX())
				.setOriginalY(buttonY)
				.setOpacity(lastRectangle.getOpacity())
				.setFilled(lastRectangle.isFilled());
			background.revalidate();

			// Create text button
			final Widget text = menu.createChild(WidgetType.TEXT)
				.setText(buttonText)
				.setTextColor(FONT_COLOR)
				.setFontId(lastText.getFontId())
				.setTextShadowed(lastText.getTextShadowed())
				.setOriginalWidth(lastText.getOriginalWidth())
				.setOriginalHeight(lastText.getOriginalHeight())
				.setOriginalX(lastText.getOriginalX())
				.setOriginalY(buttonY)
				.setXTextAlignment(lastText.getXTextAlignment())
				.setYTextAlignment(lastText.getYTextAlignment());

			text.setHasListener(true);
			text.setOnMouseOverListener((JavaScriptCallback) ev -> text.setTextColor(FONT_COLOR_ACTIVE));
			text.setOnMouseLeaveListener((JavaScriptCallback) ev -> text.setTextColor(FONT_COLOR));
			text.setAction(0, actionText);
			text.setOnOpListener((JavaScriptCallback) ev -> onClick.run());
			text.revalidate();
		}

		// Extend menu height so every Reval button fits (re-fetch children:
		// createChild above may have appended to the array)
		Widget[] currentChildren = menu.getChildren();
		if (currentChildren != null) {
			long revalButtons = Arrays.stream(currentChildren)
				.filter(w -> w != null && w.getText() != null
					&& (w.getText().equals(BUTTON_TEXT) || w.getText().equals(DEBUG_BUTTON_TEXT)))
				.count();
			int desiredHeight = baseMenuHeight + (int) revalButtons * buttonHeight;
			if (menu.getOriginalHeight() < desiredHeight) {
				menu.setOriginalHeight(desiredHeight);
			}
		}

		menu.revalidate();
		for (Widget child : menuChildren) {
			child.revalidate();
		}
	}
}

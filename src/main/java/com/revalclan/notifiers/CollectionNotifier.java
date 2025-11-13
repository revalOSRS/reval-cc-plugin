/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class CollectionNotifier extends BaseNotifier {
	private static final Pattern COLLECTION_PATTERN = Pattern.compile(
		"New item added to your collection log: (?<item>.+)",
		Pattern.CASE_INSENSITIVE
	);

	@Inject private RevalClanConfig config;

	@Override
	public boolean isEnabled() {
		return config.notifyCollection() && filterManager.getFilters().isCollectionEnabled();
	}

	@Override
	protected String getEventType() {
		return "COLLECTION";
	}

	public void onChatMessage(String message) {
		if (!isEnabled()) return;

		// Strip HTML color tags from the message
		String cleanMessage = message.replaceAll("<col=[0-9a-fA-F]+>", "").replaceAll("</col>", "");

		Matcher matcher = COLLECTION_PATTERN.matcher(cleanMessage);
		if (matcher.find()) {
			String itemName = matcher.group("item");
			handleCollectionItem(itemName);
		}
	}

	private void handleCollectionItem(String itemName) {
		Map<String, Object> collectionData = new HashMap<>();
		collectionData.put("player", getPlayerName());
		collectionData.put("item", itemName);

		sendNotification(collectionData);
	}
}

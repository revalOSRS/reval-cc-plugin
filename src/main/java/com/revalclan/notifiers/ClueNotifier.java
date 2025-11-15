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
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class ClueNotifier extends BaseNotifier {
	private static final Pattern CLUE_PATTERN = Pattern.compile(
		"You have completed (?<count>\\d+) (?<tier>\\w+) Treasure Trails?\\.",
		Pattern.CASE_INSENSITIVE
	);

	@Inject private RevalClanConfig config;

	@Inject private ItemManager itemManager;

	private int clueCount = -1;
	private String clueTier = "";

	@Override
	public boolean isEnabled() {
		return config.notifyClue() && filterManager.getFilters().isClueEnabled();
	}

	@Override
	protected String getEventType() {
		return "CLUE";
	}

	public void onChatMessage(String message) {
		if (!isEnabled()) return;

		Matcher matcher = CLUE_PATTERN.matcher(message);
		if (matcher.find()) {
			clueCount = Integer.parseInt(matcher.group("count"));
			clueTier = matcher.group("tier");
		}
	}

	public void onWidgetLoaded(WidgetLoaded event) {
		if (!isEnabled()) return;

		if (event.getGroupId() == InterfaceID.TRAIL_REWARDSCREEN && !clueTier.isEmpty()) {
			Widget clueWidget = client.getWidget(InterfaceID.TrailRewardscreen.ITEMS);
			if (clueWidget != null) {
				Widget[] children = clueWidget.getChildren();
				if (children == null) return;

				List<Map<String, Object>> items = new ArrayList<>();
				long totalValue = 0;

				for (Widget child : children) {
					if (child == null) continue;

					int quantity = child.getItemQuantity();
					int itemId = child.getItemId();
					if (itemId > -1 && quantity > 0) {
						int price = itemManager.getItemPrice(itemId);
						String name = itemManager.getItemComposition(itemId).getName();

						Map<String, Object> item = new HashMap<>();
						item.put("id", itemId);
						item.put("name", name);
						item.put("quantity", quantity);
						item.put("price", price);
						items.add(item);

						totalValue += (long) price * quantity;
					}
				}

				handleClueCompletion(items, totalValue);
			}
		}
	}

	private void handleClueCompletion(List<Map<String, Object>> items, long totalValue) {
		Map<String, Object> clueData = new HashMap<>();
		clueData.put("tier", clueTier);
		clueData.put("count", clueCount);
		clueData.put("totalValue", totalValue);
		clueData.put("items", items);

		sendNotification(clueData);

		clueCount = -1;
		clueTier = "";
	}

	public void reset() {
		clueCount = -1;
		clueTier = "";
	}
}


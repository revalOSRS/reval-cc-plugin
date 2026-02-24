package com.revalclan.notifiers;

import net.runelite.api.events.MenuOptionClicked;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class EmoteNotifier extends BaseNotifier {
	@Override
	public boolean isEnabled() {
		return config.notifyEmote() && filterManager.getFilters().isEmoteEnabled();
	}

	@Override
	protected String getEventType() {
		return "EMOTE";
	}

	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (!isEnabled()) return;

		String menuOption = event.getMenuOption();
		String menuTarget = event.getMenuTarget();

		if (menuOption != null && menuOption.toLowerCase().contains("perform")) {
			Map<String, Object> emoteData = new HashMap<>();
			emoteData.put("emote", menuTarget);

			sendNotification(emoteData);
		}
	}
}


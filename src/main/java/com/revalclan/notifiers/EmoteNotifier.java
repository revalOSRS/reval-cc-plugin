package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import net.runelite.api.events.MenuOptionClicked;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class EmoteNotifier extends BaseNotifier {
	@Inject
	private RevalClanConfig config;

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


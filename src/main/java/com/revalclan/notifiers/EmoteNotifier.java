package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOptionClicked;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
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
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer == null) return;

			int regionId = localPlayer.getWorldLocation().getRegionID();
			int x = localPlayer.getWorldLocation().getX();
			int y = localPlayer.getWorldLocation().getY();

			Map<String, Object> emoteData = new HashMap<>();
			emoteData.put("player", getPlayerName());
			emoteData.put("emote", menuTarget);
			emoteData.put("regionId", regionId);
			emoteData.put("x", x);
			emoteData.put("y", y);

			sendNotification(emoteData);
		}
	}
}


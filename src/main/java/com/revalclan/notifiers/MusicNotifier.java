package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class MusicNotifier extends BaseNotifier {
	private static final int MUSIC_INTERFACE = InterfaceID.MUSIC;

	@Inject
	private RevalClanConfig config;

	@Override
	public boolean isEnabled() {
		return config.notifyMusic() && filterManager.getFilters().isMusicEnabled();
	}

	@Override
	protected String getEventType() {
		return "MUSIC_PLAYED";
	}

	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (!isEnabled()) return;
		
		String menuOption = event.getMenuOption();
		String menuTarget = event.getMenuTarget();
		int widgetId = event.getParam1();
		int widgetGroup = widgetId >> 16;
		
		if ("Play".equals(menuOption) && widgetGroup == MUSIC_INTERFACE) {
			String trackName = menuTarget.replaceAll("<[^>]+>", "").trim();
			
			if (!trackName.isEmpty()) {
				Map<String, Object> musicData = new HashMap<>();
				musicData.put("trackName", trackName);
				
				sendNotification(musicData);
			}
		}
	}
}


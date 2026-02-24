package com.revalclan.notifiers;

import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Singleton
public class MusicNotifier extends BaseNotifier {
	private static final int MUSIC_INTERFACE = InterfaceID.MUSIC;
	private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");

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
			String trackName = HTML_TAGS.matcher(menuTarget).replaceAll("").trim();
			
			if (!trackName.isEmpty()) {
				Map<String, Object> musicData = new HashMap<>();
				musicData.put("trackName", trackName);
				
				sendNotification(musicData);
			}
		}
	}
}


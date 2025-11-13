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
public class PetNotifier extends BaseNotifier {
	private static final Pattern PET_PATTERN = Pattern.compile(
		"You (?:have a funny feeling like you're being followed|feel something weird sneaking into your backpack|have a funny feeling like you would have been followed)\\.?",
		Pattern.CASE_INSENSITIVE
	);

	@Inject private RevalClanConfig config;

	@Override
	public boolean isEnabled() {
		return config.notifyPet() && filterManager.getFilters().isPetEnabled();
	}

	@Override
	protected String getEventType() {
		return "PET";
	}

	public void onChatMessage(String message) {
		if (!isEnabled()) return;

		// Strip HTML color tags from the message
		String cleanMessage = message.replaceAll("<col=[0-9a-fA-F]+>", "").replaceAll("</col>", "");

		Matcher matcher = PET_PATTERN.matcher(cleanMessage);
		if (matcher.find()) {
			handlePetDrop(cleanMessage);
		}
	}

	private void handlePetDrop(String originalMessage) {
		Map<String, Object> petData = new HashMap<>();
		petData.put("player", getPlayerName());
		petData.put("message", originalMessage);
		petData.put("obtained", !originalMessage.contains("would have been"));

		sendNotification(petData);
	}
}


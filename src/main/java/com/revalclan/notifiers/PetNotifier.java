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

		Matcher matcher = PET_PATTERN.matcher(message);
		if (matcher.find()) {
			handlePetDrop(message);
		}
	}

	private void handlePetDrop(String originalMessage) {
		Map<String, Object> petData = new HashMap<>();
		petData.put("message", originalMessage);
		petData.put("obtained", !originalMessage.contains("would have been"));

		sendNotification(petData);
	}
}


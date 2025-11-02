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
public class CollectionNotifier extends BaseNotifier
{
	private static final Pattern COLLECTION_PATTERN = Pattern.compile(
		"New item added to your collection log: (?<item>.+)",
		Pattern.CASE_INSENSITIVE
	);

	@Inject
	private RevalClanConfig config;

	@Override
	public boolean isEnabled()
	{
		return config.enableWebhook() && config.notifyCollection();
	}

	@Override
	protected String getEventType()
	{
		return "COLLECTION";
	}

	public void onChatMessage(String message)
	{
		if (!isEnabled()) return;

		Matcher matcher = COLLECTION_PATTERN.matcher(message);
		if (matcher.find())
		{
			String itemName = matcher.group("item");
			handleCollectionItem(itemName);
		}
	}

	private void handleCollectionItem(String itemName)
	{
		Map<String, Object> collectionData = new HashMap<>();
		collectionData.put("player", getPlayerName());
		collectionData.put("item", itemName);

		sendNotification(config.webhookUrl(), collectionData);
	}
}


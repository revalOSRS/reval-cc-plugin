package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notifies on boss kill counts
 */
@Slf4j
@Singleton
public class KillCountNotifier extends BaseNotifier
{
	private static final Pattern KC_PATTERN = Pattern.compile(
		"Your (?<boss>.+?)\\s+(?<type>kill|chest|completion)\\s+count is:?\\s+(?<count>[\\d,]+)",
		Pattern.CASE_INSENSITIVE
	);

	@Inject
	private RevalClanConfig config;

	@Override
	public boolean isEnabled()
	{
		return config.enableWebhook() && config.notifyKillCount();
	}

	@Override
	protected String getEventType()
	{
		return "KILL_COUNT";
	}

	public void onChatMessage(String message)
	{
		if (!isEnabled()) return;

		Matcher matcher = KC_PATTERN.matcher(message);
		if (matcher.find())
		{
			String boss = matcher.group("boss");
			String countStr = matcher.group("count").replace(",", "");
			int count = Integer.parseInt(countStr);

			handleKillCount(boss, count);
		}
	}

	private void handleKillCount(String boss, int count)
	{
		Map<String, Object> kcData = new HashMap<>();
		kcData.put("player", getPlayerName());
		kcData.put("boss", boss);
		kcData.put("killCount", count);

		sendNotification(config.webhookUrl(), kcData);
	}
}


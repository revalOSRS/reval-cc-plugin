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
public class SlayerNotifier extends BaseNotifier
{
	private static final Pattern TASK_PATTERN = Pattern.compile(
		"You have completed your task! You killed (?<count>[\\d,]+) (?<monster>.+)\\.",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern POINTS_PATTERN = Pattern.compile(
		"You've completed (?:at least )?(?<taskCount>[\\d,]+) (?:Wilderness )?tasks?(?: and received (?<points>[\\d,]+) points)?",
		Pattern.CASE_INSENSITIVE
	);

	@Inject
	private RevalClanConfig config;

	private String taskMonster = "";
	private String taskCount = "";

	@Override
	public boolean isEnabled()
	{
		return config.enableWebhook() && config.notifySlayer();
	}

	@Override
	protected String getEventType()
	{
		return "SLAYER";
	}

	public void onChatMessage(String message)
	{
		if (!isEnabled()) return;

		Matcher taskMatcher = TASK_PATTERN.matcher(message);
		if (taskMatcher.find())
		{
			taskCount = taskMatcher.group("count");
			taskMonster = taskMatcher.group("monster");
			return;
		}

		Matcher pointsMatcher = POINTS_PATTERN.matcher(message);
		if (pointsMatcher.find() && !taskMonster.isEmpty())
		{
			String points = pointsMatcher.group("points");
			String taskCountTotal = pointsMatcher.group("taskCount");

			handleSlayerCompletion(points != null ? points : "0", taskCountTotal);
		}
	}

	private void handleSlayerCompletion(String points, String totalTasks)
	{
		Map<String, Object> slayerData = new HashMap<>();
		slayerData.put("player", getPlayerName());
		slayerData.put("monster", taskMonster);
		slayerData.put("killCount", taskCount.replace(",", ""));
		slayerData.put("points", points.replace(",", ""));
		slayerData.put("totalTasks", totalTasks.replace(",", ""));

		sendNotification(config.webhookUrl(), slayerData);

		taskMonster = "";
		taskCount = "";
	}

	public void reset()
	{
		taskMonster = "";
		taskCount = "";
	}
}


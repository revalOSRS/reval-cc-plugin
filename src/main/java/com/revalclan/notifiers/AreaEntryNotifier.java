package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class AreaEntryNotifier extends BaseNotifier
{
	@Inject
	private RevalClanConfig config;

	private int lastRegionId = -1;

	@Override
	public boolean isEnabled()
	{
		return config.enableWebhook() && config.notifyAreaEntry();
	}

	@Override
	protected String getEventType()
	{
		return "AREA_ENTRY";
	}

	public void onGameTick(GameTick event)
	{
		if (!isEnabled()) return;
		if (client.getLocalPlayer() == null) return;

		WorldPoint location = client.getLocalPlayer().getWorldLocation();
		int regionId = location.getRegionID();

		if (regionId != lastRegionId && lastRegionId != -1)
		{
			handleAreaEntry(regionId, location);
		}

		lastRegionId = regionId;
	}

	private void handleAreaEntry(int regionId, WorldPoint location)
	{
		Map<String, Object> areaData = new HashMap<>();
		areaData.put("player", getPlayerName());
		areaData.put("regionId", regionId);
		areaData.put("x", location.getX());
		areaData.put("y", location.getY());
		areaData.put("plane", location.getPlane());

		sendNotification(config.webhookUrl(), areaData);
	}

	public void reset()
	{
		lastRegionId = -1;
	}
}


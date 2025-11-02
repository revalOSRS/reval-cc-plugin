package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import com.revalclan.util.ClanValidator;
import com.revalclan.util.WebhookService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import java.util.Map;

/**
 * Base class for all notification types (loot, death, pets, etc.)
 */
@Slf4j
public abstract class BaseNotifier
{
	@Inject
	protected Client client;

	@Inject
	protected WebhookService webhookService;

	@Inject
	protected RevalClanConfig config;

	/**
	 * Check if this notifier should be active
	 * @return true if the notifier is enabled and conditions are met
	 */
	public abstract boolean isEnabled();

	/**
	 * Get the event type identifier for this notifier
	 * @return Event type string (e.g., "LOOT", "DEATH", "PET")
	 */
	protected abstract String getEventType();

	/**
	 * Send a notification with the given data
	 * 
	 * @param webhookUrl The webhook URL to send to
	 * @param data The notification data
	 */
	protected void sendNotification(String webhookUrl, Map<String, Object> data)
	{
		if (webhookUrl == null || webhookUrl.trim().isEmpty())
		{
			log.debug("No webhook URL configured for {}", getEventType());
			return;
		}

		// Validate clan membership before sending
		if (!ClanValidator.validateClan(client))
		{
			log.debug("Clan validation failed for {} - notification blocked", getEventType());
			return;
		}

		// Add event metadata
		data.put("eventType", getEventType());
		data.put("eventTimestamp", System.currentTimeMillis());

		// Send async
		webhookService.sendDataAsync(webhookUrl, data);
	}

	/**
	 * Get the player's name
	 */
	protected String getPlayerName()
	{
		if (client.getLocalPlayer() != null)
		{
			return client.getLocalPlayer().getName();
		}
		return "Unknown";
	}
}


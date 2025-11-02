package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.InteractingChanged;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class DeathNotifier extends BaseNotifier
{
	@Inject
	private RevalClanConfig config;

	private Actor lastAttacker = null;
	private long lastAttackTime = 0;
	private static final long ATTACK_TIMEOUT_MS = 10000;

	@Override
	public boolean isEnabled()
	{
		return config.enableWebhook() && config.notifyDeath();
	}

	@Override
	protected String getEventType()
	{
		return "DEATH";
	}

	/**
	 * Track who is attacking the player
	 */
	public void onInteractingChanged(InteractingChanged event)
	{
		if (!isEnabled()) return;

		Actor source = event.getSource();
		Actor target = event.getTarget();

		if (target != null && target == client.getLocalPlayer())
		{
			lastAttacker = source;
			lastAttackTime = System.currentTimeMillis();
		}
	}

	public void onActorDeath(ActorDeath event)
	{
		if (!isEnabled()) return;

		Actor actor = event.getActor();
		if (actor == client.getLocalPlayer())
		{
			handleDeath();
		}
	}

	private void handleDeath()
	{
		Map<String, Object> deathData = new HashMap<>();
		deathData.put("player", getPlayerName());
		
		if (lastAttacker != null && (System.currentTimeMillis() - lastAttackTime) < ATTACK_TIMEOUT_MS)
		{
			if (lastAttacker instanceof NPC)
			{
				NPC npc = (NPC) lastAttacker;
				deathData.put("killedBy", npc.getName());
				deathData.put("killerType", "NPC");
				deathData.put("killerId", npc.getId());
			}
			else if (lastAttacker instanceof Player)
			{
				Player player = (Player) lastAttacker;
				deathData.put("killedBy", player.getName());
				deathData.put("killerType", "PLAYER");
				deathData.put("killerCombatLevel", player.getCombatLevel());
			}
		}
		else
		{
			deathData.put("killedBy", "Unknown");
			deathData.put("killerType", "UNKNOWN");
		}
		
		sendNotification(config.webhookUrl(), deathData);
		
		lastAttacker = null;
	}

	public void reset()
	{
		lastAttacker = null;
		lastAttackTime = 0;
	}
}


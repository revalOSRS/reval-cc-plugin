/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.InteractingChanged;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class DeathNotifier extends BaseNotifier {
	private static final String ATTACK_OPTION = "Attack";
	
	@Inject private RevalClanConfig config;

	private Actor lastAttacker = null;
	private long lastAttackTime = 0;
	private static final long ATTACK_TIMEOUT_MS = 10000;
	
	/**
	 * Tracks the last Actor our local player interacted with (attacked)
	 * Wrapped in WeakReference to allow garbage collection if actor despawns
	 */
	private WeakReference<Actor> lastTarget = new WeakReference<>(null);

	@Override
	public boolean isEnabled() {
		return config.notifyDeath() && filterManager.getFilters().isDeathEnabled();
	}

	@Override
	protected String getEventType() {
		return "DEATH";
	}

	/**
	 * Track who is attacking the player (inbound interaction)
	 */
	public void onInteractingChanged(InteractingChanged event) {
		if (!isEnabled()) return;

		Actor source = event.getSource();
		Actor target = event.getTarget();

		// Track who is attacking us
		if (target != null && target == client.getLocalPlayer()) {
			lastAttacker = source;
			lastAttackTime = System.currentTimeMillis();
		}
		
		// Track who we are attacking (outbound interaction)
		if (source == client.getLocalPlayer() && target != null && target.getCombatLevel() > 0) {
			lastTarget = new WeakReference<>(target);
		}
	}

	public void onActorDeath(ActorDeath event) {
		if (!isEnabled()) return;

		Actor actor = event.getActor();
		if (actor == client.getLocalPlayer()) {
			handleDeath();
		}
		
		// Clear target reference if our target or we died
		if (actor == client.getLocalPlayer() || actor == lastTarget.get()) {
			lastTarget = new WeakReference<>(null);
		}
	}

	private void handleDeath() {
		Map<String, Object> deathData = new HashMap<>();
		
		// Identify killer using sophisticated algorithm
		Actor killer = identifyKiller();
		
		if (killer instanceof NPC) {
			NPC npc = (NPC) killer;
			deathData.put("killedBy", npc.getName());
			deathData.put("killerType", "NPC");
			deathData.put("killerId", npc.getId());
			deathData.put("killerCombatLevel", npc.getCombatLevel());
		} else if (killer instanceof Player) {
			Player player = (Player) killer;
			deathData.put("killedBy", player.getName());
			deathData.put("killerType", "PLAYER");
			deathData.put("killerCombatLevel", player.getCombatLevel());
		} else {
			deathData.put("killedBy", "Unknown");
			deathData.put("killerType", "UNKNOWN");
		}
		
		EnumSet<WorldType> worldTypes = client.getWorldType();
		deathData.put("isPvpWorld", worldTypes.contains(WorldType.PVP));
		deathData.put("isHighRiskWorld", worldTypes.contains(WorldType.HIGH_RISK));
		
		sendNotificationWithScreenshot(deathData);
		
		reset();
	}
	
	/**
	 * Identify who killed the player using multiple strategies:
	 * 1. Check last target (who we attacked)
	 * 2. Check who was attacking us (lastAttacker)
	 * 3. Search through all NPCs interacting with us
	 */
	private Actor identifyKiller() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return null;
		
		// Strategy 1: Check our last target (who we attacked)
		Actor lastTarget = this.lastTarget.get();
		if (isValidKiller(lastTarget, localPlayer)) {
			return lastTarget;
		}
		
		// Strategy 2: Check last attacker (simple fallback)
		if (lastAttacker != null && (System.currentTimeMillis() - lastAttackTime) < ATTACK_TIMEOUT_MS) {
			if (isValidKiller(lastAttacker, localPlayer)) {
				return lastAttacker;
			}
		}
		
		// Strategy 3: Search through all NPCs currently interacting with us
		return client.getTopLevelWorldView().npcs().stream()
			.filter(npc -> npc != null && !npc.isDead())
			.filter(npc -> npc.getInteracting() == localPlayer)
			.filter(npc -> {
				NPCComposition comp = npc.getTransformedComposition();
				return comp != null && comp.isInteractible() && !comp.isFollower() && comp.getCombatLevel() > 0;
			})
			.findFirst()
			.orElse(null);
	}
	
	/**
	 * Check if an actor is a valid killer
	 */
	private boolean isValidKiller(Actor actor, Player localPlayer) {
		if (actor == null || actor.isDead()) return false;
		if (actor.getInteracting() != localPlayer) return false;
		
		if (actor instanceof NPC) {
			NPC npc = (NPC) actor;
			NPCComposition comp = npc.getTransformedComposition();
			if (comp == null || !comp.isInteractible() || comp.isFollower()) return false;
			if (comp.getCombatLevel() <= 0) return false;
			
			// Check if NPC has attack option (is attackable)
			String[] actions = comp.getActions();
			if (actions != null) {
				for (String action : actions) {
					if (ATTACK_OPTION.equals(action)) {
						return true;
					}
				}
			}
			return false;
		}
		
		return actor instanceof Player;
	}

	public void reset() {
		lastAttacker = null;
		lastAttackTime = 0;
		lastTarget = new WeakReference<>(null);
	}
}


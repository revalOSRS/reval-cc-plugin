package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Hitsplat;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
public class DetailedKillNotifier extends BaseNotifier
{
	@Inject
	private RevalClanConfig config;

	@Inject
	private ItemManager itemManager;

	private final Map<NPC, KillData> activeKills = new ConcurrentHashMap<>();
	
	private int previousSpecEnergy = 100;
	private int specTicksRemaining = 0;
	private String specWeaponName = null;

	@Override
	public boolean isEnabled()
	{
		return config.enableWebhook() && config.notifyDetailedKill();
	}

	@Override
	protected String getEventType()
	{
		return "DETAILED_KILL";
	}

	public void onGameTick(net.runelite.api.events.GameTick event)
	{
		if (!isEnabled()) return;

		int currentSpecEnergy = client.getVarpValue(300);
		
		if (currentSpecEnergy < previousSpecEnergy)
		{
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null)
			{
				int weaponId = localPlayer.getPlayerComposition().getEquipmentId(net.runelite.api.kit.KitType.WEAPON);
				specWeaponName = weaponId > 0 ? itemManager.getItemComposition(weaponId).getName() : "Unarmed";
				specTicksRemaining = 3;
			}
		}
		else if (specTicksRemaining > 0)
		{
			specTicksRemaining--;
			if (specTicksRemaining == 0)
			{
				specWeaponName = null;
			}
		}
		
		previousSpecEnergy = currentSpecEnergy;
	}

	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!isEnabled()) return;

		Actor actor = event.getActor();
		if (!(actor instanceof NPC)) return;

		Hitsplat hitsplat = event.getHitsplat();
		if (!hitsplat.isMine()) return;

		NPC npc = (NPC) actor;
		int damage = hitsplat.getAmount();

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return;

		int weaponId = localPlayer.getPlayerComposition().getEquipmentId(net.runelite.api.kit.KitType.WEAPON);
		String weaponName = weaponId > 0 ? itemManager.getItemComposition(weaponId).getName() : "Unarmed";

		boolean isSpec = specTicksRemaining > 0 && weaponName.equals(specWeaponName);
		KillData data = activeKills.computeIfAbsent(npc, k -> new KillData(npc.getName(), npc.getId()));
		data.addHit(damage, weaponName, isSpec);
	}

	public void onActorDeath(ActorDeath event)
	{
		if (!isEnabled()) return;

		Actor actor = event.getActor();
		if (!(actor instanceof NPC)) return;

		NPC npc = (NPC) actor;
		KillData data = activeKills.remove(npc);

		if (data != null && data.totalDamage > 0)
		{
			handleDetailedKill(data);
		}
	}

	private void handleDetailedKill(KillData data)
	{
		Map<String, Object> killData = new HashMap<>();
		killData.put("player", getPlayerName());
		killData.put("npcName", data.npcName);
		killData.put("npcId", data.npcId);
		killData.put("totalDamage", data.totalDamage);
		killData.put("hitCount", data.hitCount);
		killData.put("specialAttacks", data.specialAttackCount);
		killData.put("lastHitWeapon", data.lastHitWeapon);
		killData.put("lastHitDamage", data.lastHitDamage);
		killData.put("lastHitWasSpec", data.lastHitWasSpec);
		killData.put("weaponsUsed", new ArrayList<>(data.weaponsUsed.keySet()));
		killData.put("damageByWeapon", data.weaponsUsed);

		sendNotification(config.webhookUrl(), killData);
	}

	public void reset()
	{
		activeKills.clear();
		previousSpecEnergy = 100;
		specTicksRemaining = 0;
		specWeaponName = null;
	}

	private static class KillData
	{
		final String npcName;
		final int npcId;
		int totalDamage = 0;
		int hitCount = 0;
		int specialAttackCount = 0;
		String lastHitWeapon = "Unknown";
		int lastHitDamage = 0;
		boolean lastHitWasSpec = false;
		final Map<String, Integer> weaponsUsed = new HashMap<>();

		KillData(String npcName, int npcId)
		{
			this.npcName = npcName;
			this.npcId = npcId;
		}

		void addHit(int damage, String weapon, boolean isSpec)
		{
			totalDamage += damage;
			hitCount++;
			lastHitWeapon = weapon;
			lastHitDamage = damage;
			lastHitWasSpec = isSpec;

			if (isSpec)
			{
				specialAttackCount++;
			}

			weaponsUsed.merge(weapon, damage, Integer::sum);
		}
	}
}


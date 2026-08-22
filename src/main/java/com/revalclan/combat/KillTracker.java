package com.revalclan.combat;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accumulates per-kill combat details (damage, weapons, specs) from hitsplats.
 * Pure accumulator — consumers decide what a completed kill is used for
 * (session counts, DETAILED_KILL webhook), see RevalClanPlugin.onActorDeath.
 */
@Singleton
public class KillTracker {
	@Inject private Client client;
	@Inject private ItemManager itemManager;

	private final Map<NPC, KillData> activeKills = new ConcurrentHashMap<>();

	private int previousSpecEnergy = 100;
	private int specTicksRemaining = 0;
	private String specWeaponName = null;

	public void onGameTick(GameTick event) {
		int currentSpecEnergy = client.getVarpValue(300);

		if (currentSpecEnergy < previousSpecEnergy) {
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null) {
				specWeaponName = equippedWeaponName(localPlayer);
				specTicksRemaining = 3;
			}
		} else if (specTicksRemaining > 0) {
			specTicksRemaining--;
			if (specTicksRemaining == 0) {
				specWeaponName = null;
			}
		}

		previousSpecEnergy = currentSpecEnergy;
	}

	public void onHitsplatApplied(HitsplatApplied event) {
		Actor actor = event.getActor();
		if (!(actor instanceof NPC)) return;

		Hitsplat hitsplat = event.getHitsplat();
		if (!hitsplat.isMine()) return;

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return;

		NPC npc = (NPC) actor;
		String weaponName = equippedWeaponName(localPlayer);
		boolean isSpec = specTicksRemaining > 0 && weaponName.equals(specWeaponName);

		KillData data = activeKills.computeIfAbsent(npc, k -> new KillData(npc.getName(), npc.getId()));
		data.addHit(hitsplat.getAmount(), weaponName, isSpec);
	}

	/** Single cleanup point — death fires a despawn too. */
	public void onNpcDespawned(NpcDespawned event) {
		activeKills.remove(event.getNpc());
	}

	/** The completed kill (with damage dealt), or null when this death isn't ours. */
	public KillData onActorDeath(ActorDeath event) {
		Actor actor = event.getActor();
		if (!(actor instanceof NPC)) return null;

		KillData data = activeKills.remove((NPC) actor);
		return data != null && data.totalDamage > 0 ? data : null;
	}

	public void reset() {
		activeKills.clear();
		previousSpecEnergy = 100;
		specTicksRemaining = 0;
		specWeaponName = null;
	}

	private String equippedWeaponName(Player localPlayer) {
		int weaponId = localPlayer.getPlayerComposition().getEquipmentId(net.runelite.api.kit.KitType.WEAPON);
		return weaponId > 0 ? itemManager.getItemComposition(weaponId).getName() : "Unarmed";
	}

	public static class KillData {
		public final String npcName;
		public final int npcId;
		public int totalDamage = 0;
		public int hitCount = 0;
		public int specialAttackCount = 0;
		public String lastHitWeapon = "Unknown";
		public int lastHitDamage = 0;
		public boolean lastHitWasSpec = false;
		public final Map<String, Integer> weaponsUsed = new HashMap<>();

		KillData(String npcName, int npcId) {
			this.npcName = npcName;
			this.npcId = npcId;
		}

		void addHit(int damage, String weapon, boolean isSpec) {
			totalDamage += damage;
			hitCount++;
			lastHitWeapon = weapon;
			lastHitDamage = damage;
			lastHitWasSpec = isSpec;

			if (isSpec) {
				specialAttackCount++;
			}

			weaponsUsed.merge(weapon, damage, Integer::sum);
		}
	}
}

package com.revalclan.notifiers;

import com.revalclan.session.SessionTracker;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Hitsplat;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-kill combat details (damage, weapons, specs).
 *
 * Kill ACCUMULATION always runs (cheap, in-memory) — it feeds the local
 * SessionTracker with complete kill counts regardless of server filters.
 * Only the DETAILED_KILL webhook send is gated by the server-driven filters:
 * the enabled toggle plus the NPC id/name whitelists derived from active
 * requirements, so the backend only receives kills it can actually use.
 */
@Singleton
public class DetailedKillNotifier extends BaseNotifier {
	@Inject
	private SessionTracker sessionTracker;

	private final Map<NPC, KillData> activeKills = new ConcurrentHashMap<>();

	private int previousSpecEnergy = 100;
	private int specTicksRemaining = 0;
	private String specWeaponName = null;

	@Override
	public boolean isEnabled() {
		return config.notifyDetailedKill() && filterManager.getFilters().isDetailedKillEnabled();
	}

	@Override
	protected String getEventType() {
		return "DETAILED_KILL";
	}

	public void onGameTick(GameTick event) {
		int currentSpecEnergy = client.getVarpValue(300);

		if (currentSpecEnergy < previousSpecEnergy) {
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null) {
				int weaponId = localPlayer.getPlayerComposition().getEquipmentId(net.runelite.api.kit.KitType.WEAPON);
				specWeaponName = weaponId > 0 ? itemManager.getItemComposition(weaponId).getName() : "Unarmed";
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

	public void onActorDeath(ActorDeath event) {
		Actor actor = event.getActor();
		if (!(actor instanceof NPC)) return;

		NPC npc = (NPC) actor;
		KillData data = activeKills.remove(npc);

		if (data != null && data.totalDamage > 0) {
			// Session kill counts are complete and local — independent of server filters
			sessionTracker.addKill(data.npcName);

			if (isEnabled()) {
				handleDetailedKill(data);
			}
		}
	}

	private void handleDetailedKill(KillData data) {
		// Apply filters
		if (!shouldNotifyKill(data.npcId, data.npcName)) {
			return;
		}

		Map<String, Object> killData = new HashMap<>();
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

		sendNotification(killData);
	}

	/**
	 * Check if we should notify for this NPC based on filters.
	 * Filter priority:
	 * 1. If NPC is in the id blacklist -> DENY
	 * 2. If both whitelists are empty -> ALLOW (server controls volume via the enabled toggle)
	 * 3. If NPC id is in the id whitelist -> ALLOW
	 * 4. If NPC name matches the name whitelist (containment, case-insensitive,
	 *    mirroring the backend requirement matcher) -> ALLOW
	 * 5. Otherwise -> DENY
	 */
	private boolean shouldNotifyKill(int npcId, String npcName) {
		var filters = filterManager.getFilters();

		if (filters.getDetailedKillNpcIdBlacklist().contains(npcId)) {
			return false;
		}

		Set<Integer> idWhitelist = filters.getDetailedKillNpcIdWhitelist();
		Set<String> nameWhitelist = filters.getDetailedKillNpcNameWhitelist();

		if (idWhitelist.isEmpty() && nameWhitelist.isEmpty()) {
			return true;
		}

		if (idWhitelist.contains(npcId)) {
			return true;
		}

		if (npcName != null && !nameWhitelist.isEmpty()) {
			String lowerName = npcName.toLowerCase();
			for (String target : nameWhitelist) {
				if (lowerName.contains(target) || target.contains(lowerName)) {
					return true;
				}
			}
		}

		return false;
	}

	public void reset() {
		activeKills.clear();
		previousSpecEnergy = 100;
		specTicksRemaining = 0;
		specWeaponName = null;
	}

	private static class KillData {
		final String npcName;
		final int npcId;
		int totalDamage = 0;
		int hitCount = 0;
		int specialAttackCount = 0;
		String lastHitWeapon = "Unknown";
		int lastHitDamage = 0;
		boolean lastHitWasSpec = false;
		final Map<String, Integer> weaponsUsed = new HashMap<>();

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

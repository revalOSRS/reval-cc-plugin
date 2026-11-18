package com.revalclan.notifiers;

import com.revalclan.combat.KillTracker.KillData;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Sends DETAILED_KILL webhooks for kills completed by KillTracker,
 * gated by the server-driven filters.
 */
@Singleton
public class DetailedKillNotifier extends BaseNotifier {
	@Override
	public boolean isEnabled() {
		return filterManager.getFilters().isDetailedKillEnabled();
	}

	@Override
	protected String getEventType() {
		return "DETAILED_KILL";
	}

	public void onKill(KillData data) {
		if (!isEnabled() || !shouldNotifyKill(data.npcId, data.npcName)) {
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
	 * 4. If NPC name matches the name whitelist (containment, case-insensitive) -> ALLOW
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
				// Forward containment only — reverse would let "Rat" match a "brine rat" entry
				if (lowerName.contains(target)) {
					return true;
				}
			}
		}

		return false;
	}
}

package com.revalclan.notifiers;

import com.revalclan.util.RaidDeathTracker;
import com.revalclan.util.RaidPartyTracker;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Player;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports the raid uniques the game announces for other party members, so the
 * backend can show megarares of players who do not run the plugin. Our own
 * uniques arrive as LOOT and are skipped here. Paused with the loot toggle.
 */
@Singleton
public class RaidPartyDropNotifier extends BaseNotifier {
	/** ToB, ToA and the Archive: "esseo jr found something special: Obligator (uncharged)". */
	private static final Pattern FOUND_SPECIAL = Pattern.compile("^([\\w -]{1,12}) found something special: (.+)$");
	/** CoX prints "Special loot:", then one "Name - Item" line per unique. */
	private static final String COX_SPECIAL_LOOT = "Special loot:";
	private static final Pattern COX_UNIQUE = Pattern.compile("^([\\w -]{1,12}) - (.+)$");
	private static final String[] FOUND_SPECIAL_RAIDS = {
		RaidPartyTracker.THEATRE_OF_BLOOD, RaidPartyTracker.TOMBS_OF_AMASCUT, RaidPartyTracker.FRACTURED_ARCHIVE};

	@Inject private RaidPartyTracker raidPartyTracker;
	@Inject private RaidDeathTracker deathTracker;

	private int coxLootTick = -1;

	@Override
	public boolean isEnabled() {
		return filterManager.getFilters().isLootEnabled();
	}

	@Override
	protected String getEventType() {
		return "RAID_PARTY_DROP";
	}

	/** @param message tag-free, with non-breaking spaces already replaced */
	public void onChatMessage(ChatMessageType type, String message) {
		if (type != ChatMessageType.FRIENDSCHATNOTIFICATION && type != ChatMessageType.GAMEMESSAGE) return;
		if (message.startsWith(COX_SPECIAL_LOOT)) {
			coxLootTick = client.getTickCount();
			return;
		}

		Matcher found = FOUND_SPECIAL.matcher(message);
		if (found.matches()) {
			report(found.group(1), found.group(2), currentRaid());
			return;
		}

		int age = client.getTickCount() - coxLootTick;
		Matcher cox = COX_UNIQUE.matcher(message);
		if (coxLootTick >= 0 && age >= 0 && age <= 1 && cox.matches()) {
			report(cox.group(1), cox.group(2), RaidPartyTracker.CHAMBERS_OF_XERIC);
		}
	}

	private String currentRaid() {
		for (String raid : FOUND_SPECIAL_RAIDS) {
			if (deathTracker.isInside(raid)) return raid;
		}
		return null;
	}

	private void report(String recipient, String itemName, String raid) {
		if (!isEnabled()) return;
		Player local = client.getLocalPlayer();
		if (local == null || "you".equalsIgnoreCase(recipient)
			|| RaidPartyTracker.nameKey(recipient).equals(RaidPartyTracker.nameKey(local.getName()))) return;

		Map<String, Object> data = new HashMap<>();
		data.put("recipient", recipient.trim());
		data.put("itemName", itemName.trim());
		if (raid != null) {
			data.put("raid", raid);
			RaidPartyTracker.Party party = raidPartyTracker.partyFor(raid);
			if (party != null) party.addTo(data);
		}
		sendCompactNotification(getEventType(), data);
	}
}

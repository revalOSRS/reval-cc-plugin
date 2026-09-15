package com.revalclan.util;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemStack;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps a ToB or ToA reward from being reported again when its chest is reopened.
 *
 * RuneLite's loot tracker reports a raid chest each time it opens after the player
 * has moved between an instance and the normal world (runelite/runelite#18427). A
 * reward opened inside the raid and claimed later from the chest outside it (opposite
 * the Ver Sinhaza bank, or the rewards niche in the ToA lobby), or reopened after a
 * trip through a house, is reported again with whatever is left in it. Until the raid
 * is completed again the chest can only lose items, so a chest holding nothing beyond
 * the reward already reported is that same reward.
 *
 * The reported reward is kept in the RS profile, so a reward left in the chest across
 * a logout or a client restart still counts once. Call on the client thread.
 */
@Singleton
public class RaidRewardLedger {
	private static final String CONFIG_GROUP = "revalclan";
	private static final String CONFIG_KEY_PREFIX = "raidReward_";

	/** "Your completed Tombs of Amascut: Expert Mode count is: 12." — any mode of either raid. */
	private static final Pattern COMPLETION = Pattern.compile(
		"Your completed (Theatre of Blood|Tombs of Amascut)(?::[^:]*)? count is");

	@Inject private ConfigManager configManager;

	/** A completed raid leaves a new reward in its chest: forget the one reported before. */
	public void onGameMessage(String message) {
		String raid = completedRaid(message);
		if (raid != null) {
			configManager.unsetRSProfileConfiguration(CONFIG_GROUP, key(raid));
		}
	}

	/**
	 * Record a loot report. False only for a ToB or ToA chest holding nothing beyond the
	 * reward reported since the raid was last completed; loot from anything else is always
	 * new and is not recorded.
	 */
	public boolean record(String source, Collection<ItemStack> items) {
		String raid = guardedRaid(source);
		if (raid == null) return true;

		Map<Integer, Integer> chest = totals(items);
		if (within(chest, decode(configManager.getRSProfileConfiguration(CONFIG_GROUP, key(raid))))) return false;

		configManager.setRSProfileConfiguration(CONFIG_GROUP, key(raid), encode(chest));
		return true;
	}

	/** The raid a loot source is the reward chest of, when that chest can be reopened outside the raid. */
	static String guardedRaid(String source) {
		String raid = RaidPartyTracker.raidOf(source);
		// Chambers of Xeric has no reward chest outside the raid
		return RaidPartyTracker.CHAMBERS_OF_XERIC.equals(raid) ? null : raid;
	}

	/** The raid a "Your completed ... count is" message completes, or null. */
	static String completedRaid(String message) {
		if (message == null) return null;
		Matcher matcher = COMPLETION.matcher(message);
		return matcher.find() ? matcher.group(1) : null;
	}

	/** Item id → quantity, merging split stacks and skipping empty slots. */
	static Map<Integer, Integer> totals(Collection<ItemStack> items) {
		Map<Integer, Integer> totals = new TreeMap<>();
		for (ItemStack item : items) {
			if (item.getId() > 0 && item.getQuantity() > 0) {
				totals.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return totals;
	}

	/** Whether the reported reward covers every item in the chest, in quantity. */
	static boolean within(Map<Integer, Integer> chest, Map<Integer, Integer> reported) {
		for (Map.Entry<Integer, Integer> item : chest.entrySet()) {
			if (item.getValue() > reported.getOrDefault(item.getKey(), 0)) return false;
		}
		return true;
	}

	/** "id:quantity,id:quantity" in id order. */
	static String encode(Map<Integer, Integer> totals) {
		StringJoiner joined = new StringJoiner(",");
		for (Map.Entry<Integer, Integer> item : new TreeMap<>(totals).entrySet()) {
			joined.add(item.getKey() + ":" + item.getValue());
		}
		return joined.toString();
	}

	/** The totals an encoded reward holds; empty when there is none or it cannot be read. */
	static Map<Integer, Integer> decode(String encoded) {
		Map<Integer, Integer> totals = new TreeMap<>();
		if (encoded == null || encoded.isEmpty()) return totals;
		try {
			for (String entry : encoded.split(",")) {
				int colon = entry.indexOf(':');
				totals.merge(Integer.parseInt(entry.substring(0, colon)), Integer.parseInt(entry.substring(colon + 1)), Integer::sum);
			}
		} catch (RuntimeException e) {
			// Unreadable: report the next chest rather than risk swallowing a new reward
			totals.clear();
		}
		return totals;
	}

	private static String key(String raid) {
		return CONFIG_KEY_PREFIX + raid.toLowerCase(Locale.ROOT).replace(' ', '_');
	}
}

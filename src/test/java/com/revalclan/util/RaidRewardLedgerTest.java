package com.revalclan.util;

import net.runelite.client.game.ItemStack;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static com.revalclan.util.RaidPartyTracker.THEATRE_OF_BLOOD;
import static com.revalclan.util.RaidPartyTracker.TOMBS_OF_AMASCUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RaidRewardLedgerTest {
	private static final int FANG = 26219;
	private static final int LIGHTBEARER = 25975;
	private static final int DEATH_RUNE = 560;

	private static Map<Integer, Integer> chest(ItemStack... items) {
		return RaidRewardLedger.totals(Arrays.asList(items));
	}

	@Test
	public void aReopenedChestHoldsNothingBeyondTheReportedReward() {
		Map<Integer, Integer> reported = chest(new ItemStack(FANG, 1), new ItemStack(DEATH_RUNE, 300));

		assertTrue(RaidRewardLedger.within(chest(new ItemStack(DEATH_RUNE, 300), new ItemStack(FANG, 1)), reported));
		// Partly claimed before the chest was reopened
		assertTrue(RaidRewardLedger.within(chest(new ItemStack(DEATH_RUNE, 120)), reported));
		assertTrue(RaidRewardLedger.within(chest(), reported));
	}

	@Test
	public void aChestHoldingAnythingMoreIsANewReward() {
		Map<Integer, Integer> reported = chest(new ItemStack(FANG, 1), new ItemStack(DEATH_RUNE, 300));

		assertFalse(RaidRewardLedger.within(chest(new ItemStack(LIGHTBEARER, 1)), reported));
		assertFalse(RaidRewardLedger.within(chest(new ItemStack(DEATH_RUNE, 301)), reported));
		assertFalse(RaidRewardLedger.within(chest(new ItemStack(FANG, 1)), chest()));
	}

	@Test
	public void totalsMergeSplitStacksAndSkipEmptySlots() {
		Map<Integer, Integer> totals = chest(new ItemStack(DEATH_RUNE, 100), new ItemStack(-1, 0), new ItemStack(DEATH_RUNE, 50));

		assertEquals(1, totals.size());
		assertEquals(Integer.valueOf(150), totals.get(DEATH_RUNE));
	}

	@Test
	public void theRecordRoundTripsAndAnUnreadableOneIsEmpty() {
		Map<Integer, Integer> reward = chest(new ItemStack(FANG, 1), new ItemStack(DEATH_RUNE, 300));

		assertEquals("560:300,26219:1", RaidRewardLedger.encode(reward));
		assertEquals(reward, RaidRewardLedger.decode(RaidRewardLedger.encode(reward)));
		assertTrue(RaidRewardLedger.decode(null).isEmpty());
		assertTrue(RaidRewardLedger.decode("").isEmpty());
		assertTrue(RaidRewardLedger.decode("560:300,oops").isEmpty());
	}

	@Test
	public void everyModeOfBothRaidsCompletesItsRaid() {
		assertEquals(THEATRE_OF_BLOOD, RaidRewardLedger.completedRaid("Your completed Theatre of Blood count is: 12."));
		assertEquals(THEATRE_OF_BLOOD, RaidRewardLedger.completedRaid("Your completed Theatre of Blood: Hard Mode count is: 3."));
		assertEquals(TOMBS_OF_AMASCUT, RaidRewardLedger.completedRaid("Your completed Tombs of Amascut: Entry Mode count is: 7."));
		assertEquals(TOMBS_OF_AMASCUT, RaidRewardLedger.completedRaid("Your completed Tombs of Amascut: Expert Mode count is: 1,204."));
	}

	@Test
	public void otherCountsCompleteNothing() {
		assertNull(RaidRewardLedger.completedRaid("Your completed Chambers of Xeric count is: 40."));
		assertNull(RaidRewardLedger.completedRaid("Your Vorkath kill count is: 5."));
		assertNull(RaidRewardLedger.completedRaid(null));
	}

	@Test
	public void onlyTobAndToaChestsAreGuarded() {
		assertEquals(THEATRE_OF_BLOOD, RaidRewardLedger.guardedRaid("Theatre of Blood"));
		assertEquals(TOMBS_OF_AMASCUT, RaidRewardLedger.guardedRaid("Tombs of Amascut"));
		assertNull(RaidRewardLedger.guardedRaid("Chambers of Xeric"));
		assertNull(RaidRewardLedger.guardedRaid("Barrows"));
		assertNull(RaidRewardLedger.guardedRaid(null));
	}
}

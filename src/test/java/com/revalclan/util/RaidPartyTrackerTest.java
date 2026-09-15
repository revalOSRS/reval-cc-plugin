package com.revalclan.util;

import com.revalclan.util.RaidPartyTracker.Origin;
import com.revalclan.util.RaidPartyTracker.Party;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.revalclan.util.RaidPartyTracker.CHAMBERS_OF_XERIC;
import static com.revalclan.util.RaidPartyTracker.SAVED_PARTY_TTL_MS;
import static com.revalclan.util.RaidPartyTracker.THEATRE_OF_BLOOD;
import static com.revalclan.util.RaidPartyTracker.TOMBS_OF_AMASCUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class RaidPartyTrackerTest {
	private static Party party(String raid, Origin origin, String... names) {
		return Party.of(raid, origin, Arrays.asList(names), null);
	}

	@Test
	public void raidSourcesIgnoreTheModeSuffix() {
		assertEquals(THEATRE_OF_BLOOD, RaidPartyTracker.raidOf("Theatre of Blood: Hard Mode"));
		assertEquals(CHAMBERS_OF_XERIC, RaidPartyTracker.raidOf("Chambers of Xeric Challenge Mode"));
		assertEquals(TOMBS_OF_AMASCUT, RaidPartyTracker.raidOf("Tombs of Amascut: Expert Mode"));
		assertNull(RaidPartyTracker.raidOf("Zulrah"));
		assertNull(RaidPartyTracker.raidOf(null));
	}

	@Test
	public void onlyTheLastBossOfEachRaidSavesTheParty() {
		assertEquals(CHAMBERS_OF_XERIC, RaidPartyTracker.raidForFinalBoss("Great Olm"));
		assertEquals(THEATRE_OF_BLOOD, RaidPartyTracker.raidForFinalBoss("Verzik Vitur"));
		assertEquals(TOMBS_OF_AMASCUT, RaidPartyTracker.raidForFinalBoss("Tumeken's Warden"));
		assertEquals(TOMBS_OF_AMASCUT, RaidPartyTracker.raidForFinalBoss("Elidinis’ Warden"));
		assertNull(RaidPartyTracker.raidForFinalBoss("Great Olm Left Claw"));
		assertNull(RaidPartyTracker.raidForFinalBoss("The Maiden of Sugadinti"));
		assertNull(RaidPartyTracker.raidForFinalBoss(null));
	}

	@Test
	public void insideTheRaidTheLivePartyWins() {
		Party live = party(THEATRE_OF_BLOOD, Origin.LIVE, "A", "B");
		Party saved = party(THEATRE_OF_BLOOD, Origin.FINAL_BOSS, "A", "B", "C");
		assertSame(live, RaidPartyTracker.choose(THEATRE_OF_BLOOD, live, true, saved, 1_000));
	}

	@Test
	public void outsideTheRaidTheSavedPartyWins() {
		Party live = party(TOMBS_OF_AMASCUT, Origin.LIVE, "A");
		Party saved = party(TOMBS_OF_AMASCUT, Origin.FINAL_BOSS, "A", "B");
		assertSame(saved, RaidPartyTracker.choose(TOMBS_OF_AMASCUT, live, false, saved, 60_000));
		assertSame(saved, RaidPartyTracker.choose(TOMBS_OF_AMASCUT, null, false, saved, 60_000));
	}

	@Test
	public void aSavedPartyFromAnotherRaidOrPastItsTtlIsIgnored() {
		Party live = party(TOMBS_OF_AMASCUT, Origin.LIVE, "A");
		Party otherRaid = party(THEATRE_OF_BLOOD, Origin.FINAL_BOSS, "B");
		Party stale = party(TOMBS_OF_AMASCUT, Origin.FINAL_BOSS, "C");
		assertSame(live, RaidPartyTracker.choose(TOMBS_OF_AMASCUT, live, false, otherRaid, 1_000));
		assertSame(live, RaidPartyTracker.choose(TOMBS_OF_AMASCUT, live, false, stale, SAVED_PARTY_TTL_MS + 1));
		assertNull(RaidPartyTracker.choose(TOMBS_OF_AMASCUT, null, false, stale, SAVED_PARTY_TTL_MS + 1));
	}

	@Test
	public void membersAreFlaggedAgainstTheRevalRoster() {
		Set<String> roster = new HashSet<>(Arrays.asList(
			RaidPartyTracker.nameKey("Iron Man"), RaidPartyTracker.nameKey("Zezima")));
		Party party = Party.of(CHAMBERS_OF_XERIC, Origin.LIVE, Arrays.asList("iron_man", "Zezima", "Guest"), roster);

		Map<String, Object> data = new HashMap<>();
		party.addTo(data);

		assertEquals(Arrays.asList("iron_man", "Zezima", "Guest"), data.get("partyMembers"));
		Map<?, ?> payload = (Map<?, ?>) data.get("party");
		assertEquals("live", payload.get("origin"));
		List<?> members = (List<?>) payload.get("members");
		assertEquals(true, ((Map<?, ?>) members.get(0)).get("clanMember"));
		assertEquals(true, ((Map<?, ?>) members.get(1)).get("clanMember"));
		assertEquals(false, ((Map<?, ?>) members.get(2)).get("clanMember"));
	}

	@Test
	public void withoutARosterTheFlagIsLeftOut() {
		Map<String, Object> data = new HashMap<>();
		party(THEATRE_OF_BLOOD, Origin.FINAL_BOSS, "A").addTo(data);

		Map<?, ?> payload = (Map<?, ?>) data.get("party");
		assertEquals("final_boss", payload.get("origin"));
		Map<?, ?> member = (Map<?, ?>) ((List<?>) payload.get("members")).get(0);
		assertEquals("A", member.get("name"));
		assertFalse(member.containsKey("clanMember"));
	}
}

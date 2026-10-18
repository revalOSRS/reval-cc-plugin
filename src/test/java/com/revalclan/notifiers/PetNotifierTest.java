package com.revalclan.notifiers;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PetNotifierTest {
	private static final String PERSONAL_MESSAGE = "You have a funny feeling like you're being followed.";
	private static final String CLAN_MESSAGE = "KuldneVihm feels like he's acquired something special: Abyssal orphan";

	@Test
	public void resolvesAbyssalOrphanFromUnpunctuatedClanBroadcast() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onChatMessage(PERSONAL_MESSAGE);
		notifier.onClanNotification(CLAN_MESSAGE);
		for (int tick = 0; tick < 11; tick++) notifier.onGameTick();

		assertEquals(1, notifier.sent.size());
		assertEquals("Abyssal orphan", notifier.sent.get(0).get("petName"));
		assertEquals(true, notifier.sent.get(0).get("obtained"));
	}

	@Test
	public void resolvesTheSameBroadcastWithAFinalPeriod() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onChatMessage(PERSONAL_MESSAGE);
		notifier.onClanNotification(CLAN_MESSAGE + ".");
		notifier.onGameTick();
		assertEquals("Abyssal orphan", notifier.sent.get(0).get("petName"));
	}

	@Test
	public void emitsOneNamedEventForTheWholeScreenshotSequence() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onChatMessage(PERSONAL_MESSAGE);
		notifier.onClanNotification(CLAN_MESSAGE);
		notifier.onClanNotification("KuldneVihm received a new collection log item: Abyssal orphan (404/1717)");
		assertEquals("Abyssal orphan", finish(notifier).get("petName"));
	}

	@Test
	public void acceptsNewerPetsAlreadyKnownToTheBackend() {
		for (String pet : new String[] {"Beef", "Maggot marquess"}) {
			CapturingNotifier notifier = new CapturingNotifier();
			notifier.onChatMessage(PERSONAL_MESSAGE);
			notifier.onClanNotification("KuldneVihm has a funny feeling like he's being followed: " + pet);
			assertEquals(pet, finish(notifier).get("petName"));
		}
	}

	@Test
	public void recoversPetNameFromTheClanCollectionLogBroadcast() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onChatMessage(PERSONAL_MESSAGE);
		notifier.onClanNotification("KuldneVihm received a new collection log item: Abyssal orphan (404/1717)");
		for (int tick = 0; tick < 11; tick++) notifier.onGameTick();
		assertEquals("Abyssal orphan", notifier.sent.get(0).get("petName"));
	}

	@Test
	public void acceptsClanBroadcastBeforeThePersonalMessage() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onClanNotification(CLAN_MESSAGE);
		notifier.onGameTick();
		notifier.onChatMessage(PERSONAL_MESSAGE);
		assertEquals("Abyssal orphan", finish(notifier).get("petName"));
	}

	@Test
	public void removesRankIconsAndNonbreakingSpacesBeforeMatchingThePlayer() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onChatMessage(PERSONAL_MESSAGE);
		notifier.onClanNotification("<img=2>" + CLAN_MESSAGE.replace(' ', '\u00A0'));
		assertEquals("Abyssal orphan", finish(notifier).get("petName"));
	}

	@Test
	public void preservesMilestonesAndDuplicateStatus() {
		for (String punctuation : new String[] {"", "."}) {
			CapturingNotifier notifier = new CapturingNotifier();
			notifier.onChatMessage("You have a funny feeling like you would have been followed.");
			notifier.onClanNotification("KuldneVihm has a funny feeling like he would have been followed: Vorki at 1,234 kills" + punctuation);
			Map<String, Object> event = finish(notifier);
			assertEquals("Vorki", event.get("petName"));
			assertEquals("1,234 kills", event.get("killCount"));
			assertEquals(false, event.get("obtained"));
		}
	}

	@Test
	public void preservesPeriodsThatBelongToThePetName() {
		for (String punctuation : new String[] {"", "."}) {
			CapturingNotifier notifier = new CapturingNotifier();
			notifier.onChatMessage(PERSONAL_MESSAGE);
			notifier.onClanNotification("KuldneVihm has a funny feeling like he's being followed: Vet'ion jr." + punctuation);
			assertEquals("Vet'ion jr.", finish(notifier).get("petName"));
		}
	}

	@Test
	public void stripsTheSourceFromSharedLootPetNames() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onChatMessage(PERSONAL_MESSAGE);
		notifier.onClanNotification("KuldneVihm feels something weird sneaking into his backpack: Nexling from Nex at 100 kills.");
		Map<String, Object> event = finish(notifier);
		assertEquals("Nexling", event.get("petName"));
		assertEquals("100 kills", event.get("killCount"));
	}

	@Test
	public void ignoresOtherPlayersEvenWhenTheirNameEndsWithTheLocalName() {
		for (String message : new String[] {
			CLAN_MESSAGE.replace("KuldneVihm", "Other KuldneVihm"),
			"Other KuldneVihm received a new collection log item: Abyssal orphan (404/1717)"
		}) {
			CapturingNotifier notifier = new CapturingNotifier();
			notifier.onChatMessage(PERSONAL_MESSAGE);
			notifier.onClanNotification(message);
			assertFalse(finish(notifier).containsKey("petName"));
		}
	}

	@Test
	public void clanMessagesAloneDoNotConfirmAPetDrop() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onClanNotification(CLAN_MESSAGE);
		notifier.onClanNotification("KuldneVihm received a new collection log item: Abyssal orphan (404/1717)");
		for (int tick = 0; tick < 12; tick++) notifier.onGameTick();
		assertTrue(notifier.sent.isEmpty());
	}

	@Test
	public void rejectsUnknownNamesAndNonPetCollectionItems() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onChatMessage(PERSONAL_MESSAGE);
		notifier.onClanNotification("KuldneVihm feels like he's acquired something special: Not a pet.");
		notifier.onClanNotification("KuldneVihm received a new collection log item: Abyssal dagger (404/1717)");
		assertFalse(finish(notifier).containsKey("petName"));
	}

	@Test
	public void continuesToReadPersonalItemMessages() {
		for (String prefix : new String[] {"Untradeable drop: ", "New item added to your collection log: "}) {
			CapturingNotifier notifier = new CapturingNotifier();
			notifier.onChatMessage(PERSONAL_MESSAGE);
			notifier.onChatMessage(prefix + "Abyssal orphan");
			assertEquals("Abyssal orphan", finish(notifier).get("petName"));
		}
	}

	@Test
	public void expiresUnconfirmedNamesAndResetsAfterSending() {
		CapturingNotifier notifier = new CapturingNotifier();
		notifier.onClanNotification(CLAN_MESSAGE);
		for (int tick = 0; tick < 12; tick++) notifier.onGameTick();
		notifier.onChatMessage(PERSONAL_MESSAGE);
		assertFalse(finish(notifier).containsKey("petName"));
		notifier.onChatMessage(PERSONAL_MESSAGE);
		notifier.onClanNotification(CLAN_MESSAGE);
		for (int tick = 0; tick < 12; tick++) notifier.onGameTick();
		assertEquals(2, notifier.sent.size());
		assertEquals("Abyssal orphan", notifier.sent.get(1).get("petName"));
	}

	private Map<String, Object> finish(CapturingNotifier notifier) {
		for (int tick = 0; tick < 12; tick++) notifier.onGameTick();
		assertEquals(1, notifier.sent.size());
		return notifier.sent.get(0);
	}

	private static class CapturingNotifier extends PetNotifier {
		private final List<Map<String, Object>> sent = new ArrayList<>();

		@Override
		public boolean isEnabled() { return true; }

		@Override
		protected String getPlayerName() { return "KuldneVihm"; }

		@Override
		protected void sendNotification(Map<String, Object> data) {
			sent.add(new HashMap<>(data));
		}
	}
}

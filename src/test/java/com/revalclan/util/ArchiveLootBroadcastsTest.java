package com.revalclan.util;

import net.runelite.api.ChatMessageType;
import org.junit.Test;

import static org.junit.Assert.*;

public class ArchiveLootBroadcastsTest {
    private static final String LOOT = "PhosaniSpoon received special loot from a raid: ";

    @Test public void hidesRegularArchiveLoot() {
        assertTrue(hidden(LOOT + "50 x Onyx bolt tips (very valuable)."));
        assertTrue(hidden(LOOT + "68 x Earth orb (very valuable)."));
        assertTrue(hidden(LOOT + "3,009 x Earth rune (very valuable)."));
        assertTrue(hidden(LOOT + "Clue scroll (elite) (very valuable)."));
        assertTrue(hidden(LOOT + "75 x Ascension shards (very valuable)."));
    }

    @Test public void keepsRareRewardsAndCabbage() {
        for (String item : new String[] {"Rondache", "Zorya's tome (uncharged)", "Horn of Bran", "Ascension crossbows",
            "Obligator (uncharged)", "Orion", "Fragment of burning fire", "Archivist's folio (inert)", "Cabbage"}) {
            assertFalse(item, hidden(LOOT + item + " (very valuable)."));
            assertFalse(item, hidden(LOOT + "1 x " + item + " (very valuable)."));
        }
    }

    @Test public void readsTheBroadcastAsChatShowsIt() {
        assertTrue(hidden("<col=ef1020>Phosani Spoon received special loot from a raid: 305 x Nature rune (very valuable).</col>"));
    }

    @Test public void leavesOtherBroadcastsAlone() {
        assertFalse(hidden(LOOT + "Twisted bow."));
        assertFalse(hidden("PhosaniSpoon received a drop: Onyx bolt tips (very valuable)."));
        assertFalse(ArchiveLootBroadcasts.hidden(ChatMessageType.CLAN_CHAT, LOOT + "50 x Onyx bolt tips (very valuable)."));
        assertFalse(ArchiveLootBroadcasts.hidden(ChatMessageType.GAMEMESSAGE, LOOT + "50 x Onyx bolt tips (very valuable)."));
    }

    @Test public void coversFriendsAndGuestChatBroadcasts() {
        assertTrue(ArchiveLootBroadcasts.hidden(ChatMessageType.FRIENDSCHATNOTIFICATION, LOOT + "50 x Onyx bolt tips (very valuable)."));
        assertTrue(ArchiveLootBroadcasts.hidden(ChatMessageType.CLAN_GUEST_MESSAGE, LOOT + "50 x Onyx bolt tips (very valuable)."));
    }

    private static boolean hidden(String message) {
        return ArchiveLootBroadcasts.hidden(ChatMessageType.CLAN_MESSAGE, message);
    }
}

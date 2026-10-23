package com.revalclan.util;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.util.Text;

/**
 * The Fractured Archive broadcasts every regular reward to friends and clan chats
 * as "(very valuable)" (a known Jagex bug). Those lines are dropped before chat
 * renders them, the same way RuneLite's Chat Filter blocks a message; the raid's
 * rare rewards and Cabbage stay visible. Other raids' broadcasts lack the suffix.
 */
public final class ArchiveLootBroadcasts {
	private static final Set<ChatMessageType> BROADCASTS = EnumSet.of(ChatMessageType.CLAN_MESSAGE,
		ChatMessageType.CLAN_GUEST_MESSAGE, ChatMessageType.CLAN_GIM_MESSAGE, ChatMessageType.FRIENDSCHATNOTIFICATION);

	private static final Pattern VERY_VALUABLE_RAID_LOOT = Pattern.compile(
		"received special loot from a raid: (?:[\\d,]+ x )?(.+) \\(very valuable\\)\\.?$", Pattern.CASE_INSENSITIVE);

	/** Name prefixes of the uniques, pet, collection log tertiaries and the poor-run Cabbage. */
	private static final List<String> KEPT = List.of("rondache", "zorya's tome", "horn of bran",
		"ascension crossbows", "obligator", "orion", "fragment of ", "archivist's folio", "cabbage");

	private ArchiveLootBroadcasts() {}

	/** Blocks the message being checked; the stack layout matches RuneLite's ChatFilterPlugin. */
	public static void filter(Client client, ScriptCallbackEvent event) {
		if (!"chatFilterCheck".equals(event.getEventName())) return;
		int[] ints = client.getIntStack();
		int size = client.getIntStackSize();
		Object message = client.getObjectStack()[client.getObjectStackSize() - 1];
		if (message instanceof String && hidden(ChatMessageType.of(ints[size - 2]), (String) message)) {
			ints[size - 3] = 0;
		}
	}

	static boolean hidden(ChatMessageType type, String message) {
		if (!BROADCASTS.contains(type)) return false;
		Matcher loot = VERY_VALUABLE_RAID_LOOT.matcher(Text.removeTags(message).replace(' ', ' ').trim());
		if (!loot.find()) return false;
		String item = loot.group(1).toLowerCase(Locale.ROOT);
		return KEPT.stream().noneMatch(item::startsWith);
	}
}

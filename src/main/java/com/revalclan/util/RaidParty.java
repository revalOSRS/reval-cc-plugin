package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the current raid party member names (including the local player).
 * CoX uses the raid sidepanel member-list widget,
 * ToB and ToA expose party names through consecutive VarClientStr slots.
 * Must be called on the client thread.
 */
public final class RaidParty {
	/** First VarClientStr holding a ToB party member name (5 consecutive slots). */
	private static final int TOB_MEMBER_NAME_VARC = 330;
	private static final int TOB_PARTY_MAX_SIZE = 5;

	/** First VarClientStr holding a ToA party member name (8 consecutive slots). */
	private static final int TOA_MEMBER_NAME_VARC = 1099;
	private static final int TOA_PARTY_MAX_SIZE = 8;

	private RaidParty() {}

	/**
	 * @param source boss or loot source name, possibly with a mode suffix
	 *               (e.g. "Theatre of Blood: Hard Mode")
	 * @return party member names, or null if the source is not a raid or no party was found
	 */
	public static List<String> getMembers(Client client, String source) {
		if (source == null) return null;

		List<String> members;
		if (source.startsWith("Chambers of Xeric")) {
			members = getXericParty(client);
		} else if (source.startsWith("Tombs of Amascut")) {
			members = getVarcStrings(client, TOA_MEMBER_NAME_VARC, TOA_PARTY_MAX_SIZE);
		} else if (source.startsWith("Theatre of Blood")) {
			members = getVarcStrings(client, TOB_MEMBER_NAME_VARC, TOB_PARTY_MAX_SIZE);
		} else {
			return null;
		}
		return members.isEmpty() ? null : members;
	}

	private static List<String> getXericParty(Client client) {
		Widget widget = client.getWidget(InterfaceID.RaidsSidepanel.LIST);
		if (widget == null) return List.of();

		Widget[] children = widget.getChildren();
		if (children == null) return List.of();

		List<String> names = new ArrayList<>();
		for (Widget child : children) {
			String name = sanitize(child.getName());
			if (!name.isEmpty()) {
				names.add(name);
			}
		}
		return names;
	}

	private static List<String> getVarcStrings(Client client, int initialVarcId, int maxSize) {
		List<String> names = new ArrayList<>(maxSize);
		for (int i = 0; i < maxSize; i++) {
			String name = client.getVarcStrValue(initialVarcId + i);
			if (name == null || name.isEmpty()) continue;
			names.add(name.replace('\u00A0', ' '));
		}
		return names;
	}

	private static String sanitize(String str) {
		if (str == null || str.isEmpty()) return "";
		return Text.removeTags(str).replace('\u00A0', ' ').trim();
	}
}

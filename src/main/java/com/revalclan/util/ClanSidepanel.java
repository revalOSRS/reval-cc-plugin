package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Iteration over the clan sidepanel member list's name widgets. */
public final class ClanSidepanel {
	/** Rows hold a name widget and a world widget ("W123"); worlds are skipped. */
	private static final Pattern WORLD_TEXT = Pattern.compile("W\\d+");

	private ClanSidepanel() {
	}

	/** Calls {@code consumer} with each member-name text widget in the list. */
	public static void eachNameRow(Client client, Consumer<Widget> consumer) {
		Widget list = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (list == null) {
			return;
		}
		Widget[] children = list.getDynamicChildren();
		if (children == null) {
			return;
		}
		for (Widget child : children) {
			String text = child.getText();
			if (text == null || text.isEmpty() || WORLD_TEXT.matcher(text).matches()) {
				continue;
			}
			consumer.accept(child);
		}
	}
}

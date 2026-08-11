package com.revalclan.util;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.client.callback.ClientThread;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Resolves clan rank titles (e.g. "Red Topaz") to their in-game clan icon
 * sprite ids using the game's own enums (CLAN_RANK_NAME → CLAN_RANK_GRAPHIC),
 * so icons stay correct for every rank without hardcoding sprite ids. Falls
 * back to a small verified static map when the cache lookup is unavailable.
 */
public class ClanRankIconResolver {
	// Verified against the game cache enums CLAN_RANK_NAME (3797) → CLAN_RANK_GRAPHIC
	// (3798): every rank title's icon sprite id, dumped 2026-08-11. Mentor→Monarch
	// (3137–3144) and Marshal (3111) also match what the plugin shipped before.
	private static final Map<String, Integer> FALLBACK_SPRITES = Map.ofEntries(
		Map.entry("mentor", 3137),
		Map.entry("prefect", 3138),
		Map.entry("leader", 3139),
		Map.entry("supervisor", 3140),
		Map.entry("superior", 3141),
		Map.entry("executive", 3142),
		Map.entry("senator", 3143),
		Map.entry("monarch", 3144),
		Map.entry("red_topaz", 3129),
		Map.entry("sapphire", 3130),
		Map.entry("emerald", 3131),
		Map.entry("ruby", 3132),
		Map.entry("diamond", 3133),
		Map.entry("dragonstone", 3134),
		Map.entry("onyx", 3135),
		Map.entry("zenyte", 3136),
		Map.entry("marshal", 3111)
	);

	private final Client client;
	private final ClientThread clientThread;
	private volatile Map<String, Integer> titleToSprite;

	public ClanRankIconResolver(Client client, ClientThread clientThread) {
		this.client = client;
		this.clientThread = clientThread;
	}

	/**
	 * Resolve a rank slug or title to a sprite id. The callback fires at most
	 * once, possibly on the client thread; it is skipped when no icon is known.
	 */
	public void resolve(String rankName, IntConsumer callback) {
		if (rankName == null || client == null || clientThread == null) return;
		String title = RankNames.display(rankName).toLowerCase(Locale.ROOT);

		Map<String, Integer> cached = titleToSprite;
		if (cached != null) {
			deliver(cached, title, callback);
			return;
		}

		clientThread.invoke(() -> {
			Map<String, Integer> map = titleToSprite;
			if (map == null) {
				map = buildMap();
				if (!map.isEmpty()) {
					titleToSprite = map;
				}
			}
			deliver(map, title, callback);
		});
	}

	private void deliver(Map<String, Integer> map, String title, IntConsumer callback) {
		Integer spriteId = map.get(title);
		if (spriteId == null) {
			spriteId = FALLBACK_SPRITES.get(title.replace(' ', '_'));
		}
		if (spriteId != null) {
			callback.accept(spriteId);
		}
	}

	private Map<String, Integer> buildMap() {
		Map<String, Integer> map = new HashMap<>();
		try {
			EnumComposition names = client.getEnum(EnumID.CLAN_RANK_NAME);
			EnumComposition graphics = client.getEnum(EnumID.CLAN_RANK_GRAPHIC);
			for (int key : names.getKeys()) {
				String title = names.getStringValue(key);
				int sprite = graphics.getIntValue(key);
				if (title != null && !title.isEmpty() && sprite > 0) {
					map.put(title.toLowerCase(Locale.ROOT), sprite);
				}
			}
		} catch (Exception ignored) {
			// Cache not ready — callers get the static fallback instead
		}
		return map;
	}
}

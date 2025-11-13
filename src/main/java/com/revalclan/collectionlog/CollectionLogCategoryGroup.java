package com.revalclan.collectionlog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * All available category groups (i.e. tabs) mapped to their in-game struct IDs
 */
@RequiredArgsConstructor
public enum CollectionLogCategoryGroup {
	BOSSES(471),
	RAIDS(472),
	CLUES(473),
	MINIGAMES(474),
	OTHER(475);

	@Getter private final int structId;
	
	/**
	 * Get the category name (capitalized) from struct ID
	 * @param structId The struct ID to lookup
	 * @return The category name with first letter capitalized, or "Unknown" if not found
	 */
	public static String getNameFromStructId(int structId) {
		for (CollectionLogCategoryGroup group : values()) {
			if (group.structId == structId) {
				String name = group.name().toLowerCase();
				return name.substring(0, 1).toUpperCase() + name.substring(1);
			}
		}

		return "Unknown";
	}
}

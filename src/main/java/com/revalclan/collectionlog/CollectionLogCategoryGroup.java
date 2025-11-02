package com.revalclan.collectionlog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * All available category groups (i.e. tabs) mapped to their in-game struct IDs
 */
@RequiredArgsConstructor
public enum CollectionLogCategoryGroup
{
	BOSSES(471),
	RAIDS(472),
	CLUES(473),
	MINIGAMES(474),
	OTHER(475);

	@Getter
	private final int structId;
}


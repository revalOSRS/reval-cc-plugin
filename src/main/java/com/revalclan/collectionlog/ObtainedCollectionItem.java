package com.revalclan.collectionlog;

import lombok.Value;

/**
 * Represents a collection log item that the player has obtained
 */
@Value
public class ObtainedCollectionItem {
	int id;
	String name;
	int count;
}


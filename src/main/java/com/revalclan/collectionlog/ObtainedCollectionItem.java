package com.revalclan.collectionlog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a collection log item that the player has obtained
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObtainedCollectionItem {
	private int id;
	private String name;
	private int count;

	public ObtainedCollectionItem(int id, int count) {
		this.id = id;
		this.count = count;
	}
}


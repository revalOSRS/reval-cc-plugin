package com.revalclan.ui.leaguesbingo;

import com.revalclan.api.leaguesbingo.LeaguesBingoResponse.Tile;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

import javax.swing.SwingUtilities;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tile icons from the game's item cache, never the wiki. The backend sends
 * the item id it resolved for the tile's icon; the one fallback is an exact
 * name match against the price list, for tiles it could not resolve.
 */
public class TileIcons {
	private final ItemManager itemManager;
	/** Icon name -> item id from the fallback search; null marks a miss. */
	private final Map<String, Integer> searched = new HashMap<>();

	public TileIcons(ItemManager itemManager) {
		this.itemManager = itemManager;
	}

	public void load(Tile tile, TileCell cell) {
		if (itemManager == null) return;
		Integer itemId = tile.getIconItemId() != null ? tile.getIconItemId() : searchByName(tile.getIcon());
		if (itemId == null) return;
		try {
			AsyncBufferedImage image = itemManager.getImage(itemId);
			cell.setIcon(image);
			image.onLoaded(() -> SwingUtilities.invokeLater(cell::repaint));
		} catch (RuntimeException ignored) {
			// A bad id must never break the grid.
		}
	}

	private Integer searchByName(String iconName) {
		if (iconName == null || iconName.trim().isEmpty()) return null;
		String name = iconName.trim().replace('_', ' ');
		if (searched.containsKey(name)) return searched.get(name);
		Integer found = null;
		try {
			List<ItemPrice> results = itemManager.search(name);
			for (ItemPrice p : results) {
				if (p.getName() != null && p.getName().equalsIgnoreCase(name)) {
					found = p.getId();
					break;
				}
			}
		} catch (RuntimeException ignored) {
			// Price list unavailable: leave the tile without an icon.
		}
		searched.put(name, found);
		return found;
	}
}

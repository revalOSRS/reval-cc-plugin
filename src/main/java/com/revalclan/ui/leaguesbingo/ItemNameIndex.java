package com.revalclan.ui.leaguesbingo;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Item name -> item id lookup over the game cache, so tile icons can be
 * drawn for untradeables (capes, pets, vouchers) that the price list does
 * not know. Built once per session on the client thread, on first use.
 */
@Slf4j
public class ItemNameIndex {
	private final Client client;
	private final ClientThread clientThread;

	private final Map<String, Integer> index = new HashMap<>();
	private boolean built;
	private boolean building;
	private final List<Runnable> waiters = new ArrayList<>();

	public ItemNameIndex(Client client, ClientThread clientThread) {
		this.client = client;
		this.clientThread = clientThread;
	}

	/** Calls back on the Swing thread with the id, or null when no item has that name. */
	public void resolve(String name, Consumer<Integer> callback) {
		if (name == null || client == null || clientThread == null) {
			callback.accept(null);
			return;
		}
		String key = name.trim().toLowerCase();
		synchronized (this) {
			if (built) {
				Integer id = index.get(key);
				callback.accept(id);
				return;
			}
			waiters.add(() -> callback.accept(index.get(key)));
			if (building) return;
			building = true;
		}
		clientThread.invoke(this::build);
	}

	/** Runs on the client thread; returns false to be retried until the cache is available. */
	private boolean build() {
		if (client.getGameState() != GameState.LOGGED_IN) return false;
		Map<String, Integer> names = new HashMap<>();
		int count = client.getItemCount();
		for (int id = 0; id < count; id++) {
			try {
				ItemComposition c = client.getItemDefinition(id);
				if (c == null || c.getNote() != -1 || c.getPlaceholderTemplateId() != -1) continue;
				String n = c.getName();
				if (n == null || n.isEmpty() || "null".equals(n)) continue;
				names.putIfAbsent(n.toLowerCase(), id);
			} catch (Exception e) {
				// Skip anything the cache refuses to load.
			}
		}
		log.debug("Item name index built: {} names", names.size());

		List<Runnable> toRun;
		synchronized (this) {
			index.putAll(names);
			built = true;
			building = false;
			toRun = new ArrayList<>(waiters);
			waiters.clear();
		}
		SwingUtilities.invokeLater(() -> toRun.forEach(Runnable::run));
		return true;
	}
}

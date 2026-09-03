package com.revalclan.notifiers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reports the value of varbits the backend asked to watch (varbits.watch on
 * the event-filters payload).
 *
 * Some content keeps its score in a varbit and nowhere else: Volcanic Mine
 * points accrue in one during a game, with no chat line and no item. Rises are
 * coalesced and flushed every {@link #FLUSH_INTERVAL_TICKS} so a game does not
 * produce an event per swing; a fall flushes at once, and the peak is sent
 * before the new value so the backend never misses what was banked before a
 * reset.
 */
@Slf4j
@Singleton
public class VarbitNotifier extends BaseNotifier {
	/** About 30 seconds. */
	private static final int FLUSH_INTERVAL_TICKS = 50;

	/** Latest value observed per watched varbit. */
	private final Map<Integer, Integer> latest = new HashMap<>();
	/** Last value the backend was told per watched varbit. */
	private final Map<Integer, Integer> sent = new HashMap<>();
	private int ticksSinceFlush = 0;

	@Inject private ClientThread clientThread;

	/**
	 * Send the current value of every watched varbit. A varbit only raises an
	 * event when it changes, so without this the backend's first sighting would
	 * be the value AFTER a player's first deposit, and that deposit would be
	 * lost to the baseline. Called after every filter fetch (login and every
	 * ~10 minutes), unconditionally: the fetch that turns an event live is what
	 * has to establish the pre-event balance.
	 */
	public void syncBaselines() {
		clientThread.invokeLater(() -> {
			if (client.getGameState() != GameState.LOGGED_IN || !isEnabled()) return;
			for (int id : filterManager.getFilters().getVarbitWatch()) {
				int value = client.getVarbitValue(id);
				latest.put(id, value);
				sent.put(id, value);
				log.info("[Reval] varbit baseline {} = {}", id, value);
				Map<String, Object> data = new HashMap<>();
				data.put("varbitId", id);
				data.put("value", value);
				sendNotification("VARBIT_CHANGED", data, null);
			}
		});
	}

	@Override
	public boolean isEnabled() {
		return !filterManager.getFilters().getVarbitWatch().isEmpty();
	}

	@Override
	protected String getEventType() {
		return "VARBIT_CHANGED";
	}

	public void onVarbitChanged(VarbitChanged event) {
		int id = event.getVarbitId();
		if (id < 0) return;
		Set<Integer> watched = filterManager.getFilters().getVarbitWatch();
		if (!watched.contains(id)) return;

		int value = event.getValue();
		Integer previous = latest.put(id, value);
		// Testing aid: every change of a watched varbit, whether or not it is sent.
		log.info("[Reval] watched varbit {} -> {} (was {})", id, value, previous);

		if (previous != null && value < previous) {
			flush(id, previous);
			flush(id, value);
		}
	}

	public void onGameTick() {
		if (latest.isEmpty() || !isEnabled()) return;
		if (++ticksSinceFlush < FLUSH_INTERVAL_TICKS) return;
		ticksSinceFlush = 0;
		for (Map.Entry<Integer, Integer> entry : latest.entrySet()) {
			flush(entry.getKey(), entry.getValue());
		}
	}

	private void flush(int id, int value) {
		Integer last = sent.get(id);
		if (last != null && last == value) return;
		sent.put(id, value);

		Map<String, Object> data = new HashMap<>();
		data.put("varbitId", id);
		data.put("value", value);
		if (last != null) data.put("previous", last);
		log.info("[Reval] VARBIT_CHANGED {} = {} (previous {})", id, value, last);
		sendNotification("VARBIT_CHANGED", data, null);
	}

	/** Logged out or hopped: whatever we knew belongs to a session that is over. */
	public void reset() {
		latest.clear();
		sent.clear();
		ticksSinceFlush = 0;
	}
}

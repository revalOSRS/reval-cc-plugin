package com.revalclan.notifiers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.WorldType;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leagues panel sync notifier.
 * Sends LEAGUES_SYNC on login and when the Relics modal is opened.
 * Payload: leagueType, totalPoints, tasksCompleted, areaSelections (id + name),
 * and selectedRelics (when available from Relics modal).
 */
@Slf4j
@Singleton
public class LeaguesSyncNotifier extends BaseNotifier {

	private static final int LEAGUES_RELICS_GROUP = 655;
	private static final int RELIC_READ_DELAY_TICKS = 2;
	private static final int RELIC_SELECTED_COLOR = 0xF47113;
	private static final int RELIC_UNSELECTED_COLOR = 0xAAAAAA;

	private static final Map<Integer, String> AREA_NAMES = new HashMap<>();
	static {
		AREA_NAMES.put(2, "Karamja");
		AREA_NAMES.put(7, "Tirannwn");
		AREA_NAMES.put(21, "Varlamore");
	}

	private List<String> cachedSelectedRelics = null;
	private int pendingRelicRead = -1;
	private int pendingRelicTicks = 0;

	@Override
	public boolean isEnabled() {
		return client.getWorldType().contains(WorldType.SEASONAL);
	}

	@Override
	protected String getEventType() {
		return "LEAGUES_SYNC";
	}

	/**
	 * Called after login on a seasonal world. Sends initial sync.
	 */
	public void onLogin() {
		if (!isEnabled()) return;
		sendSync();
	}

	public void onWidgetLoaded(WidgetLoaded event) {
		if (!isEnabled() || event.getGroupId() != LEAGUES_RELICS_GROUP) return;
		pendingRelicRead = LEAGUES_RELICS_GROUP;
		pendingRelicTicks = RELIC_READ_DELAY_TICKS;
	}

	public void onGameTick() {
		if (pendingRelicRead == -1) return;
		if (--pendingRelicTicks > 0) return;

		int groupId = pendingRelicRead;
		pendingRelicRead = -1;

		List<String> relics = extractRelics(groupId);
		if (!relics.isEmpty()) {
			cachedSelectedRelics = relics;
			sendSync();
		}
	}

	public void reset() {
		pendingRelicRead = -1;
		pendingRelicTicks = 0;
		cachedSelectedRelics = null;
	}

	// ========== Payload ==========

	private void sendSync() {
		Map<String, Object> data = new HashMap<>();
		data.put("seasonalWorld", true);
		data.put("leagueType", client.getVarbitValue(VarbitID.LEAGUE_TYPE));
		data.put("totalPoints", client.getVarpValue(VarPlayerID.LEAGUE_POINTS_COMPLETED));
		data.put("tasksCompleted", client.getVarbitValue(VarbitID.LEAGUE_TOTAL_TASKS_COMPLETED));

		List<Map<String, Object>> areas = new ArrayList<>();
		for (int areaId : new int[]{
			client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_0),
			client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_1),
			client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_2),
			client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_3),
			client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_4),
			client.getVarbitValue(VarbitID.LEAGUE_AREA_SELECTION_5)
		}) {
			if (areaId != 0) {
				Map<String, Object> area = new HashMap<>();
				area.put("id", areaId);
				String name = AREA_NAMES.get(areaId);
				area.put("name", name != null ? name : "Unknown");
				areas.add(area);
			}
		}
		data.put("areaSelections", areas);

		if (cachedSelectedRelics != null && !cachedSelectedRelics.isEmpty()) {
			data.put("selectedRelics", cachedSelectedRelics);
		}

		sendNotification(data);
	}

	// ========== Relics ==========

	private List<String> extractRelics(int groupId) {
		List<String> selected = new ArrayList<>();
		for (int childId = 0; childId < 20; childId++) {
			Widget child = client.getWidget(groupId, childId);
			if (child == null) continue;
			if (collectSelectedRelics(child, selected, 0)) break;
		}
		return selected;
	}

	private boolean collectSelectedRelics(Widget widget, List<String> selected, int depth) {
		if (widget == null || depth > 8) return false;

		Widget[] dc = widget.getDynamicChildren();
		if (dc != null && dc.length == 23 && dc[0] != null && dc[0].getType() == 4) {
			boolean hasOrange = false, hasGrey = false;
			for (Widget w : dc) {
				if (w == null) continue;
				if (w.getTextColor() == RELIC_SELECTED_COLOR) hasOrange = true;
				if (w.getTextColor() == RELIC_UNSELECTED_COLOR) hasGrey = true;
			}

			if (hasOrange && hasGrey) {
				int[][] tierGroups = {
					{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {9, 10, 11},
					{12, 13, 14}, {15, 16, 17}, {18, 19, 20}, {21, 22}
				};
				for (int[] group : tierGroups) {
					String orangeRelic = null;
					boolean hasGreyInTier = false;
					for (int idx : group) {
						if (idx >= dc.length || dc[idx] == null) continue;
						if (dc[idx].getTextColor() == RELIC_SELECTED_COLOR) {
							orangeRelic = dc[idx].getText();
						} else if (dc[idx].getTextColor() == RELIC_UNSELECTED_COLOR) {
							hasGreyInTier = true;
						}
					}
					if (orangeRelic != null && hasGreyInTier) {
						selected.add(orangeRelic);
					}
				}
				return true;
			}
		}

		Widget[] sc = widget.getStaticChildren();
		if (sc != null) {
			for (Widget child : sc) {
				if (collectSelectedRelics(child, selected, depth + 1)) return true;
			}
		}
		if (dc != null) {
			for (Widget child : dc) {
				if (collectSelectedRelics(child, selected, depth + 1)) return true;
			}
		}
		return false;
	}
}

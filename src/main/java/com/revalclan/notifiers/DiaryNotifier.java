/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.revalclan.notifiers;

import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class DiaryNotifier extends BaseNotifier {
	/**
	 * CS2 Script IDs for diary completion tracking
	 * @see <a href="https://github.com/Joshua-F/cs2-scripts">CS2 Reference</a>
	 */
	private static final int COMPLETED_TASKS_SCRIPT_ID = 3971;
	private static final int TOTAL_TASKS_SCRIPT_ID = 3980;

	/**
	 * Map of diary varbit IDs to their completion status
	 * Key: varbit ID, Value: completion value (0 = not started, 1 = completed, 2 = completed for Karamja special case)
	 */
	private static final Map<Integer, String> DIARY_VARBITS = createDiaryMap();

	@Inject private ClientThread clientThread;

	private final Map<Integer, Integer> diaryCompletionById = new ConcurrentHashMap<>();
	private int initDelayTicks = 0;

	@Override
	public boolean isEnabled() {
		return filterManager.getFilters().isDiaryEnabled();
	}

	@Override
	protected String getEventType() {
		return "DIARY";
	}

	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() != GameState.LOGGED_IN) {
			reset();
		}
	}

	public void onGameTick() {
		if (client.getGameState() != GameState.LOGGED_IN) return;

		if (initDelayTicks > 0) {
			initDelayTicks--;
			if (initDelayTicks == 0)
			{
				initializeDiaries();
			}
		} else if (diaryCompletionById.isEmpty() && isEnabled()) {
			initDelayTicks = 4;
		}
	}

	public void onVarbitChanged(VarbitChanged event) {
		int id = event.getVarbitId();
		if (id < 0) return;
		
		String diaryInfo = DIARY_VARBITS.get(id);
		if (diaryInfo == null) return;
		if (!isEnabled()) return;

		if (diaryCompletionById.isEmpty()) {
			if (client.getGameState() == GameState.LOGGED_IN && isComplete(id, event.getValue())) return;
			else return;
		}

		int value = event.getValue();
		Integer previous = diaryCompletionById.get(id);

		if (previous == null) {
			reset();
			return;
		}

		if (value < previous) {
			reset();
			return;
		}

		if (value > previous) {
			diaryCompletionById.put(id, value);

			if (isComplete(id, value)) {
				clientThread.invokeLater(() -> {
					handleDiaryCompletion(diaryInfo, id);
					return true;
				});
			}
		}
	}

	private void handleDiaryCompletion(String diaryInfo, int varbitId) {
		client.runScript(COMPLETED_TASKS_SCRIPT_ID);
		int completedTasks = client.getIntStack()[0];

		client.runScript(TOTAL_TASKS_SCRIPT_ID);
		int totalTasks = client.getIntStack()[0];

		int totalDiariesCompleted = getTotalCompleted();

		String[] parts = diaryInfo.split("_");
		String area = parts.length > 0 ? parts[0] : "Unknown";
		String difficulty = parts.length > 1 ? parts[1] : "Unknown";

		Map<String, Object> diaryData = new HashMap<>();
		diaryData.put("area", area);
		diaryData.put("difficulty", difficulty);
		diaryData.put("varbitId", varbitId);
		diaryData.put("completedTasks", completedTasks);
		diaryData.put("totalTasks", totalTasks);
		diaryData.put("totalDiariesCompleted", totalDiariesCompleted);

		sendNotification(diaryData);
	}

	private void initializeDiaries() {
		if (!isEnabled()) return;

		diaryCompletionById.clear();

		for (Integer varbitId : DIARY_VARBITS.keySet()) {
			int value = client.getVarbitValue(varbitId);
			if (value >= 0) {
				diaryCompletionById.put(varbitId, value);
			}
		}
	}

	private int getTotalCompleted() {
		int count = 0;
		for (Map.Entry<Integer, Integer> entry : diaryCompletionById.entrySet()) {
			if (isComplete(entry.getKey(), entry.getValue())) count++;
		}
		return count;
	}

	private static boolean isComplete(int varbitId, int value) {
		if (varbitId == 3578 || varbitId == 3599 || varbitId == 3611) {
			return value > 1;
		}
		return value > 0;
	}

	public void reset() {
		diaryCompletionById.clear();
		initDelayTicks = 0;
	}

	private static Map<Integer, String> createDiaryMap() {
		Map<Integer, String> map = new HashMap<>();
		addDiary(map, "Ardougne", 3577, 3598, 3608, 3630);
		addDiary(map, "Desert", 3579, 3597, 3610, 3628);
		addDiary(map, "Falador", 3580, 3596, 3612, 3632);
		addDiary(map, "Fremennik", 3582, 3594, 3615, 3636);
		addDiary(map, "Kandarin", 3583, 3593, 3617, 3638);
		addDiary(map, "Karamja", 3578, 3599, 3611, 3631);
		addDiary(map, "Lumbridge", 3581, 3595, 3614, 3635);
		addDiary(map, "Morytania", 3584, 3592, 3618, 3639);
		addDiary(map, "Varrock", 3576, 3601, 3606, 3627);
		addDiary(map, "Western", 3585, 3591, 3620, 3641);
		addDiary(map, "Wilderness", 3586, 3600, 3621, 3642);
		addDiary(map, "Kourend", 7925, 7926, 7927, 7928);
		return map;
	}

	/** Maps one area's diary varbits, listed Easy to Elite, to "Area_Tier" names. */
	private static void addDiary(Map<Integer, String> map, String area, int... varbits) {
		String[] tiers = {"Easy", "Medium", "Hard", "Elite"};
		for (int i = 0; i < varbits.length; i++) {
			map.put(varbits[i], area + "_" + tiers[i]);
		}
	}
}

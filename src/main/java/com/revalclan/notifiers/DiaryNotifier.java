/*
 * Portions of this file are derived from or inspired by the Dink plugin
 * Copyright (c) 2022, Jake Barter
 * Copyright (c) 2022, pajlads
 * Licensed under the BSD 2-Clause License
 * See LICENSES/dink-LICENSE.txt for full license text
 */
package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
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

	@Inject private RevalClanConfig config;

	@Inject private ClientThread clientThread;

	private final Map<Integer, Integer> diaryCompletionById = new ConcurrentHashMap<>();
	private int initDelayTicks = 0;

	@Override
	public boolean isEnabled() {
		return config.notifyDiary() && filterManager.getFilters().isDiaryEnabled();
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
		
		map.put(3577, "Ardougne_Easy");
		map.put(3598, "Ardougne_Medium");
		map.put(3608, "Ardougne_Hard");
		map.put(3630, "Ardougne_Elite");
		
		map.put(3579, "Desert_Easy");
		map.put(3597, "Desert_Medium");
		map.put(3610, "Desert_Hard");
		map.put(3628, "Desert_Elite");
		
		map.put(3580, "Falador_Easy");
		map.put(3596, "Falador_Medium");
		map.put(3612, "Falador_Hard");
		map.put(3632, "Falador_Elite");
		
		map.put(3582, "Fremennik_Easy");
		map.put(3594, "Fremennik_Medium");
		map.put(3615, "Fremennik_Hard");
		map.put(3636, "Fremennik_Elite");
		
		map.put(3583, "Kandarin_Easy");
		map.put(3593, "Kandarin_Medium");
		map.put(3617, "Kandarin_Hard");
		map.put(3638, "Kandarin_Elite");
		
		map.put(3578, "Karamja_Easy");
		map.put(3599, "Karamja_Medium");
		map.put(3611, "Karamja_Hard");
		map.put(3631, "Karamja_Elite");
		
		map.put(3581, "Lumbridge_Easy");
		map.put(3595, "Lumbridge_Medium");
		map.put(3614, "Lumbridge_Hard");
		map.put(3635, "Lumbridge_Elite");
		
		map.put(3584, "Morytania_Easy");
		map.put(3592, "Morytania_Medium");
		map.put(3618, "Morytania_Hard");
		map.put(3639, "Morytania_Elite");
		
		map.put(3576, "Varrock_Easy");
		map.put(3601, "Varrock_Medium");
		map.put(3606, "Varrock_Hard");
		map.put(3627, "Varrock_Elite");
		
		map.put(3585, "Western_Easy");
		map.put(3591, "Western_Medium");
		map.put(3620, "Western_Hard");
		map.put(3641, "Western_Elite");
		
		map.put(3586, "Wilderness_Easy");
		map.put(3600, "Wilderness_Medium");
		map.put(3621, "Wilderness_Hard");
		map.put(3642, "Wilderness_Elite");
		
		map.put(7925, "Kourend_Easy");
		map.put(7926, "Kourend_Medium");
		map.put(7927, "Kourend_Hard");
		map.put(7928, "Kourend_Elite");
		
		return map;
	}
}

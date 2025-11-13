package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class QuestNotifier extends BaseNotifier {
	@Inject private RevalClanConfig config;

	@Override
	public boolean isEnabled() {
		return config.notifyQuest() && filterManager.getFilters().isQuestEnabled();
	}

	@Override
	protected String getEventType() {
		return "QUEST";
	}

	public void onWidgetLoaded(WidgetLoaded event) {
		if (!isEnabled()) return;

		if (event.getGroupId() == InterfaceID.QUESTSCROLL) {
			Widget questTitle = client.getWidget(InterfaceID.Questscroll.QUEST_TITLE);
			if (questTitle != null) {
				String questText = questTitle.getText();
				handleQuestCompletion(questText);
			}
		}
	}

	private void handleQuestCompletion(String questText) {
		// Parse quest name from the widget text
		String questName = parseQuestName(questText);
		if (questName == null) return;

		int questPoints = client.getVarpValue(VarPlayerID.QP);
		int completedQuests = client.getVarbitValue(VarbitID.QUESTS_COMPLETED_COUNT);
		int totalQuests = client.getVarbitValue(VarbitID.QUESTS_TOTAL_COUNT);

		Map<String, Object> questData = new HashMap<>();
		questData.put("player", getPlayerName());
		questData.put("questName", questName);
		questData.put("questPoints", questPoints);
		questData.put("completedQuests", completedQuests);
		questData.put("totalQuests", totalQuests);

		sendNotification(questData);
	}

	private String parseQuestName(String questText) {
		if (questText == null) return null;

		String[] lines = questText.split("\n");
		if (lines.length >= 3) {
			String name = lines[2].replace("!", "").trim();
			return name.isEmpty() ? null : name;
		}
		return null;
	}
}


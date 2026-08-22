package com.revalclan.combatachievements;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A Combat Achievement task — only the fields the sync payload emits.
 */
@Data
@NoArgsConstructor
public class CombatAchievementTask {
	private String name;
	private String tier;
	private String type;
	private boolean completed;
}


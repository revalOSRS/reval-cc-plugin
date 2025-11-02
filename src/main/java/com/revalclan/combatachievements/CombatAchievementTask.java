package com.revalclan.combatachievements;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Combat Achievement task with all its details
 */
@Data
@NoArgsConstructor
public class CombatAchievementTask
{
	private int id;
	private String name;
	private String description;
	private String tier;
	private String type;
	private String boss;
	private int points;
	private boolean completed;
}


package com.revalclan.ui.constants;

import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;

/** Seasonal side-panel appearance, chosen from the date when the panel is built. */
public enum Season {
	NONE(null, "Reval Clan"),
	HALLOWEEN("HALLOWEEN AT REVAL", "Reval Clan - Halloween"),
	CHRISTMAS("CHRISTMAS AT REVAL", "Reval Clan - Christmas");

	/** Line under the panel title; null without a season. */
	public final String banner;
	public final String tooltip;

	Season(String banner, String tooltip) {
		this.banner = banner;
		this.tooltip = tooltip;
	}

	/** Halloween from 1 August until 1 November, Christmas from 1 December until 1 January. */
	public static Season on(LocalDate date) {
		Month month = date.getMonth();
		if (month == Month.DECEMBER) return CHRISTMAS;
		return month.compareTo(Month.AUGUST) >= 0 && month.compareTo(Month.OCTOBER) <= 0 ? HALLOWEEN : NONE;
	}

	/** Today's season. A dev client can preview one with -Dreval.season=christmas (or halloween, none). */
	public static Season current() {
		String preview = System.getProperty("reval.season");
		for (Season season : values()) {
			if (season.name().equals(preview == null ? null : preview.trim().toUpperCase(Locale.ROOT))) return season;
		}
		return on(LocalDate.now());
	}
}

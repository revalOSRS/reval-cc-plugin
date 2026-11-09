package com.revalclan.ui.constants;

import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;

/** Seasonal side-panel appearance, chosen from the date when the panel is built. */
public enum Season {
	NONE(null, "Reval Clan"),
	CHRISTMAS("CHRISTMAS AT REVAL", "Reval Clan - Christmas");

	/** Line under the panel title; null without a season. */
	public final String banner;
	public final String tooltip;

	Season(String banner, String tooltip) {
		this.banner = banner;
		this.tooltip = tooltip;
	}

	/** Christmas from 7 November until 1 January. */
	public static Season on(LocalDate date) {
		return date.getMonth() == Month.DECEMBER
			|| (date.getMonth() == Month.NOVEMBER && date.getDayOfMonth() >= 7) ? CHRISTMAS : NONE;
	}

	/** Today's season. A dev client can preview one with -Dreval.season=christmas (or none). */
	public static Season current() {
		String preview = System.getProperty("reval.season");
		for (Season season : values()) {
			if (season.name().equals(preview == null ? null : preview.trim().toUpperCase(Locale.ROOT))) return season;
		}
		return on(LocalDate.now());
	}
}

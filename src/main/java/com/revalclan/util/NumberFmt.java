package com.revalclan.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formats integers with space grouping ("12 345") independent of the default locale. */
public final class NumberFmt {
	private NumberFmt() {
	}

	/** "1.5M", "12K", "900" — for GP amounts where the exact figure does not matter. */
	public static String compact(long value) {
		if (value >= 1_000_000_000L) return trimZero(value / 1_000_000_000d) + "B";
		if (value >= 1_000_000L) return trimZero(value / 1_000_000d) + "M";
		if (value >= 1_000L) return trimZero(value / 1_000d) + "K";
		return String.valueOf(value);
	}

	private static String trimZero(double d) {
		String s = String.format(Locale.ROOT, "%.1f", d);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	public static String group(long value) {
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
		symbols.setGroupingSeparator(' ');
		return new DecimalFormat("#,##0", symbols).format(value);
	}
}

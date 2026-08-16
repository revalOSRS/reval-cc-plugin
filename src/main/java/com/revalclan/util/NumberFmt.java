package com.revalclan.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formats integers with space grouping ("12 345") independent of the default locale. */
public final class NumberFmt {
	private NumberFmt() {
	}

	public static String group(long value) {
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
		symbols.setGroupingSeparator(' ');
		return new DecimalFormat("#,##0", symbols).format(value);
	}
}

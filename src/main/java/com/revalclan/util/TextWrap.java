package com.revalclan.util;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/** Greedy word wrap measured with the real font, for plain (non-HTML) labels. */
public final class TextWrap {
	private TextWrap() {
	}

	/** Lines of at most {@code width} pixels; a single overlong word stays on its own line. */
	public static List<String> wrap(String text, FontMetrics fm, int width) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.trim().isEmpty()) return lines;
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (fm.stringWidth(candidate) <= width || line.length() == 0) {
				line.setLength(0);
				line.append(candidate);
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (line.length() > 0) lines.add(line.toString());
		return lines;
	}
}

package com.revalclan.ui.leaguesbingo;

import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.ui.FontManager;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;

/** Small Swing builders shared by the Leagues Bingo views. */
final class Labels {
	private Labels() {
	}

	static JLabel label(String text, Font font, Color color) {
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	static JLabel small(String text, Color color) {
		return label(text, FontManager.getRunescapeSmallFont(), color);
	}

	static JLabel bold(String text, Color color) {
		return label(text, FontManager.getRunescapeBoldFont(), color);
	}

	static JLabel sectionTitle(String text) {
		return bold(text, UIConstants.TEXT_PRIMARY);
	}

	/** HTML-wrapped label; the text is escaped. */
	static JLabel wrapped(String text, Font font, Color color, int width) {
		return wrappedHtml(escapeHtml(text), font, color, width);
	}

	/** HTML-wrapped label from trusted markup (spans for colour). */
	static JLabel wrappedHtml(String html, Font font, Color color, int width) {
		JLabel l = new JLabel("<html><div style='width:" + width + "px'>" + html + "</div></html>");
		l.setFont(font);
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	static String escapeHtml(String text) {
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Cuts text to fit maxWidth pixels in the given font, adding an ellipsis. */
	static String ellipsize(String text, Font font, int maxWidth) {
		if (text == null) return "";
		FontMetrics fm = new JLabel().getFontMetrics(font);
		if (fm.stringWidth(text) <= maxWidth) return text;
		int end = text.length();
		while (end > 1 && fm.stringWidth(text.substring(0, end).trim() + "...") > maxWidth) end--;
		return text.substring(0, end).trim() + "...";
	}

	/** Left/right pair on one line. */
	static JPanel row() {
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	/** Left-aligned flow of small pieces (name, badges). */
	static JPanel flow() {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setOpaque(false);
		return p;
	}

	static JComponent centered(JComponent inner) {
		JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 12));
		wrap.setOpaque(false);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(inner);
		return wrap;
	}
}

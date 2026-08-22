package com.revalclan.ui.components;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Click wiring for custom clickable panels and labels. Actions fire on
 * mousePressed, never mouseClicked: a trackpad micro-drag between press
 * and release makes Swing drop mouseClicked entirely.
 */
public final class Clickable {
	private Clickable() {
	}

	/** Left-click action; also sets the hand cursor. */
	public static void onPress(JComponent component, Runnable action) {
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					action.run();
				}
			}
		});
	}

	/** Left-click action with a background swap on hover. */
	public static void onPress(JComponent component, Runnable action, Color hoverBg, Color restingBg) {
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				component.setBackground(hoverBg);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				component.setBackground(restingBg);
			}
		});
		onPress(component, action);
	}

	/** Left-click action with a hover flag for custom-painted components; repaints on change. */
	public static void onPress(JComponent component, Runnable action, Consumer<Boolean> hovered) {
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hovered.accept(true);
				component.repaint();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hovered.accept(false);
				component.repaint();
			}
		});
		onPress(component, action);
	}
}

package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Vertical scroll pane for panel content: the content sits at the top and
 * always takes the viewport's width, so wrapped labels re-flow instead of
 * forcing a horizontal scrollbar.
 */
public final class ScrollWrap {
	private ScrollWrap() {
	}

	public static JScrollPane of(JComponent content) {
		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = super.getPreferredSize();
				if (getParent() != null) size.width = getParent().getWidth();
				return size;
			}
		};
		wrapper.setBackground(UIConstants.BACKGROUND);
		wrapper.add(content, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(wrapper);
		scroll.setBackground(UIConstants.BACKGROUND);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(UIConstants.BACKGROUND);
		return scroll;
	}
}

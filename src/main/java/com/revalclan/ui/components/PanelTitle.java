package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import java.awt.*;

public class PanelTitle extends JLabel {
	public PanelTitle(String text) {
		this(text, UIConstants.ACCENT_GOLD);
	}

	public PanelTitle(String text, Color color) {
		super(text);
		setFont(new Font("Segoe UI", Font.BOLD, 14));
		setForeground(color);
	}

	public PanelTitle(String text, int horizontalAlignment) {
    this(text, UIConstants.ACCENT_GOLD);
		setHorizontalAlignment(horizontalAlignment);
	}
}

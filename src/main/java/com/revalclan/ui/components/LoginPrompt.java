package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginPrompt extends JPanel {
	public LoginPrompt(String featureName) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(30, 20, 20, 20));

		JLabel title = new JLabel(featureName.toUpperCase());
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(UIConstants.ACCENT_GOLD);
		title.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel hint = new JLabel("Log in to view " + featureName.toLowerCase());
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(UIConstants.TEXT_SECONDARY);
		hint.setAlignmentX(Component.CENTER_ALIGNMENT);

		add(title);
		add(Box.createRigidArea(new Dimension(0, 6)));
		add(hint);
	}
}

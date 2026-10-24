package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginPrompt extends JPanel {
	public LoginPrompt(String featureName) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(30, 20, 20, 20));

		JLabel title = Labels.bold(featureName.toUpperCase(), UIConstants.ACCENT_GOLD);
		title.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel hint = Labels.small("Log in to view " + featureName.toLowerCase(), UIConstants.TEXT_SECONDARY);
		hint.setAlignmentX(Component.CENTER_ALIGNMENT);

		add(title);
		add(Box.createRigidArea(new Dimension(0, 6)));
		add(hint);
	}
}

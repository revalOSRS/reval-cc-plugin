package com.revalclan.ui.components;

public class BackButton extends FlatButton {
	public BackButton(String text, Runnable onBack) {
		super(text);
		addActionListener(e -> onBack.run());
	}
}

package com.revalclan.ui.constants;

/** Player preference; Seasonal follows the holiday calendar. */
public enum PanelTheme {
	SEASONAL("Seasonal"),
	REGULAR("Regular");

	private final String label;

	PanelTheme(String label) {
		this.label = label;
	}

	@Override
	public String toString() {
		return label;
	}
}

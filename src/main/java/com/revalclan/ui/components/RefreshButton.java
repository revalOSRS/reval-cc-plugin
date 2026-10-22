package com.revalclan.ui.components;

public class RefreshButton extends FlatButton {
	private boolean loading = false;

	public RefreshButton(Runnable onRefresh) {
		super("Refresh");
		addActionListener(e -> {
			if (!loading) onRefresh.run();
		});
	}

	public void setLoading(boolean loading) {
		this.loading = loading;
		setText(loading ? "Loading..." : "Refresh");
		setEnabled(!loading);
	}

	/** No hover fill while loading. */
	@Override
	boolean showsHover() {
		return super.showsHover() && !loading;
	}
}

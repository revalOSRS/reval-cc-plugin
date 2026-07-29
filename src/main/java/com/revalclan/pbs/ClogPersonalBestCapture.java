package com.revalclan.pbs;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import java.util.Map;

/**
 * Captures the server-side "Personal best" lines from collection log page
 * headers as the player views pages. Accumulated per account, shipped with
 * every sync.
 */
@Singleton
public class ClogPersonalBestCapture {
	private static final String CONFIG_GROUP = "revalclanclogpb";
	private static final int CLOG_GROUP = 621;
	private static final int HEADER_CHILD = 20;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ConfigManager configManager;

	public Map<String, Object> sync() {
		return PbStore.read(configManager, CONFIG_GROUP);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST) {
			clientThread.invokeLater(this::captureOpenPage);
		}
	}

	private void captureOpenPage() {
		Widget header = client.getWidget(CLOG_GROUP, HEADER_CHILD);
		if (header == null || configManager.getRSProfileKey() == null) return;

		Widget[] headerLines = header.getDynamicChildren();
		if (headerLines == null || headerLines.length < 2) return;

		String pageName = clean(headerLines[0]);
		if (pageName.isEmpty()) return;

		for (Widget lineWidget : headerLines) {
			String line = clean(lineWidget);
			int pbLabelAt = line.toLowerCase(Locale.ROOT).indexOf("personal best");
			if (pbLabelAt < 0) continue;

			int colonAt = line.indexOf(':', pbLabelAt);
			if (colonAt < 0) continue;

			String pbLabel = line.substring(0, colonAt);
			double pbSeconds = parseTime(line.substring(colonAt + 1).trim());
			if (pbSeconds > 0) {
				configManager.setRSProfileConfiguration(CONFIG_GROUP, key(pageName, pbLabel), String.valueOf(pbSeconds));
			}
		}
	}

	/** Page name + any label qualifier beyond "Personal best" (raid modes / team sizes). */
	private static String key(String pageName, String pbLabel) {
		String qualifier = pbLabel.toLowerCase(Locale.ROOT).replace("personal best", " ").replaceAll("[()]", " ");
		return (pageName + " " + qualifier).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	/** "h:mm:ss.ss" / "m:ss.ss" / "ss.ss" -> seconds; -1 when unparseable. */
	private static double parseTime(String time) {
		try {
			double seconds = 0;
			for (String part : time.split(":")) seconds = seconds * 60 + Double.parseDouble(part.trim());
			return seconds;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** Widget text without color tags and non-breaking spaces. */
	private static String clean(Widget widget) {
		String text = widget == null ? null : widget.getText();
		return text == null ? "" : Text.removeTags(text).replace('\u00A0', ' ').trim();
	}
}

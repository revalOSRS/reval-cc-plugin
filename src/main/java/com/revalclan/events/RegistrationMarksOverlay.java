package com.revalclan.events;

import com.revalclan.util.PlayerNames;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;

/**
 * Hover tooltip for {@link RegistrationMarks}: shows which upcoming event(s)
 * a checkmarked clan member registered for.
 */
public class RegistrationMarksOverlay extends Overlay {
	private static final Color GOLD = new Color(255, 200, 60);

	private final Client client;
	private final RegistrationMarks marks;
	private final TooltipManager tooltipManager;

	@Inject
	public RegistrationMarksOverlay(Client client, RegistrationMarks marks, TooltipManager tooltipManager) {
		this.client = client;
		this.marks = marks;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g) {
		Map<String, String> registrations = marks.getRegistrations();
		if (registrations.isEmpty() || !marks.isAdminViewer()) {
			return null;
		}
		Widget list = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (list == null || list.isHidden()) {
			return null;
		}
		Widget[] children = list.getDynamicChildren();
		if (children == null) {
			return null;
		}

		Point mouse = client.getMouseCanvasPosition();
		for (Widget child : children) {
			String text = child.getText();
			if (text == null || text.isEmpty() || text.matches("W\\d+")) {
				continue;
			}
			String events = registrations.get(PlayerNames.normalize(Text.removeTags(text)));
			if (events == null) {
				continue;
			}
			Rectangle bounds = child.getBounds();
			if (bounds != null && bounds.contains(mouse.getX(), mouse.getY())) {
				tooltipManager.add(new Tooltip("Registered: " + ColorUtil.wrapWithColorTag(events, GOLD)));
				break;
			}
		}
		return null;
	}
}

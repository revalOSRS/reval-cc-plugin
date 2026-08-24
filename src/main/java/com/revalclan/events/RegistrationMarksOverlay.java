package com.revalclan.events;

import com.revalclan.util.ClanSidepanel;
import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Map;

/**
 * Draws {@link RegistrationMarks}' checkmark after marked names in the clan
 * sidepanel and shows which upcoming event(s) they registered for on hover.
 */
public class RegistrationMarksOverlay extends Overlay {
	private static final Color GOLD = new Color(255, 200, 60);
	private static final Color CHECK_GREEN = new Color(0x3FBF4A);

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
		if (registrations.isEmpty() || !marks.isActive()) {
			return null;
		}
		Widget list = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (list == null || list.isHidden()) {
			return null;
		}
		Rectangle viewport = list.getBounds();
		if (viewport == null) {
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// Clip to the list like the game does, so half-scrolled rows don't
		// leak checkmarks outside the box
		java.awt.Shape prevClip = g.getClip();
		g.clip(viewport);
		Point mouse = client.getMouseCanvasPosition();

		ClanSidepanel.eachNameRow(client, child -> {
			String events = registrations.get(Text.standardize(child.getText()));
			if (events == null) {
				return;
			}
			Rectangle bounds = child.getBounds();
			if (bounds == null || !viewport.intersects(bounds)) {
				return;
			}

			FontTypeFace font = child.getFont();
			String plain = Text.removeTags(child.getText());
			int textEnd = bounds.x + (font != null ? font.getTextWidth(plain) : bounds.width);
			drawCheck(g, textEnd + 4, bounds.y + bounds.height / 2);

			if (bounds.contains(mouse.getX(), mouse.getY()) && viewport.contains(mouse.getX(), mouse.getY())) {
				tooltipManager.add(new Tooltip("Registered: " + ColorUtil.wrapWithColorTag(events, GOLD)));
			}
		});
		g.setClip(prevClip);
		return null;
	}

	private void drawCheck(Graphics2D g, int x, int centerY) {
		int[] xs = {x, x + 3, x + 8};
		int[] ys = {centerY, centerY + 3, centerY - 4};
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 0, 0, 160));
		g.translate(1, 1);
		g.drawPolyline(xs, ys, 3);
		g.translate(-1, -1);
		g.setColor(CHECK_GREEN);
		g.drawPolyline(xs, ys, 3);
	}
}

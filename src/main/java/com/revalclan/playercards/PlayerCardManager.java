package com.revalclan.playercards;

import com.revalclan.teams.MockTeamColorProvider;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanRankIconResolver;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Adds a "View Reval Profile" right-click option on clan members and shows
 * their {@link PlayerCardOverlay} in-game. While the card is open, mouse
 * input is consumed so the game doesn't react; any click closes it. Card
 * data is mocked until the profile endpoint exists.
 */
@Singleton
public class PlayerCardManager {
	private static final Color DEFAULT_ACCENT = UIConstants.ACCENT_GOLD;

	private final Client client;
	private final MockTeamColorProvider teamColors;
	private final ClanRankIconResolver rankIconResolver;
	private final SpriteManager spriteManager;
	private final MouseManager mouseManager;
	private final PlayerCardOverlay overlay;

	/** Ignore the tail of the menu click that opened the card. */
	private static final long OPEN_GRACE_MS = 250;

	private volatile long closedAt;

	private final MouseAdapter clickCloser = new MouseAdapter() {
		@Override
		public MouseEvent mousePressed(MouseEvent e) {
			if (overlay.openForMs() > OPEN_GRACE_MS) {
				e.consume();
			}
			return e;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e) {
			if (overlay.openForMs() > OPEN_GRACE_MS) {
				e.consume();
				close();
			}
			return e;
		}

		@Override
		public MouseEvent mouseClicked(MouseEvent e) {
			// The click event trails the closing release; swallow it too
			if (overlay.openForMs() > OPEN_GRACE_MS || System.currentTimeMillis() - closedAt < 250) {
				e.consume();
			}
			return e;
		}
	};

	@Inject
	public PlayerCardManager(Client client, MockTeamColorProvider teamColors, ClanRankIconResolver rankIconResolver,
							 SpriteManager spriteManager, MouseManager mouseManager, PlayerCardOverlay overlay) {
		this.client = client;
		this.teamColors = teamColors;
		this.rankIconResolver = rankIconResolver;
		this.spriteManager = spriteManager;
		this.mouseManager = mouseManager;
		this.overlay = overlay;
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		Set<String> seen = new HashSet<>();
		for (MenuEntry entry : event.getMenuEntries()) {
			Player player = entry.getPlayer();
			if (player == null || !player.isClanMember() || player.getName() == null) {
				continue;
			}
			String name = Text.removeTags(player.getName());
			if (!seen.add(name)) {
				continue;
			}
			client.getMenu().createMenuEntry(-1)
				.setOption("View Reval Profile")
				.setTarget(entry.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> open(name));
		}
	}

	private void open(String playerName) {
		Color teamColor = teamColors.teamColorFor(playerName);
		PlayerCardData data = PlayerCardData.mock(playerName);
		if (!overlay.isOpen()) {
			mouseManager.registerMouseListener(clickCloser);
		}
		overlay.show(data, teamColor != null ? teamColor : DEFAULT_ACCENT);
		rankIconResolver.resolve(data.getRankName(), spriteId ->
			spriteManager.getSpriteAsync(spriteId, 0, overlay::setRankSprite));
	}

	private void close() {
		overlay.close();
		closedAt = System.currentTimeMillis();
		mouseManager.unregisterMouseListener(clickCloser);
	}

	/** Closes an open card; called when the plugin shuts down. */
	public void shutDown() {
		if (overlay.isOpen()) {
			close();
		}
	}
}

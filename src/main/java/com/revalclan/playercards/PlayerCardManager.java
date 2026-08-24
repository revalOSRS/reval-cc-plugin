package com.revalclan.playercards;

import com.revalclan.RevalClanConfig;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.PlayerNames;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Adds a "View Reval Profile" right-click option on clan members and shows
 * their {@link PlayerCardOverlay} in-game. While the card is open, mouse
 * input is consumed so the game doesn't react; a click or Escape closes it.
 * Card data is mocked until the profile endpoint exists.
 */
@Singleton
public class PlayerCardManager {
	/** Card accent per rank, loosely matching each rank icon's color. */
	private static final Map<String, Color> RANK_COLORS = Map.ofEntries(
		Map.entry("mentor", new Color(0xB08D57)),
		Map.entry("prefect", new Color(0xC0C0C8)),
		Map.entry("leader", new Color(0xE0B84F)),
		Map.entry("supervisor", new Color(0x7A9CC6)),
		Map.entry("superior", new Color(0x4FA98F)),
		Map.entry("executive", new Color(0xD98E3C)),
		Map.entry("senator", new Color(0x6FA8DC)),
		Map.entry("monarch", new Color(0x9B59B6)),
		Map.entry("red topaz", new Color(0xD9663C)),
		Map.entry("sapphire", new Color(0x3B6FD9)),
		Map.entry("emerald", new Color(0x2FBF71)),
		Map.entry("ruby", new Color(0xD93B5A)),
		Map.entry("diamond", new Color(0xD8D8E8)),
		Map.entry("dragonstone", new Color(0xB05CD9)),
		Map.entry("onyx", new Color(0x8E6FC0)),
		Map.entry("zenyte", new Color(0xE08A3C)),
		Map.entry("marshal", new Color(0xFFC83C))
	);

	private final Client client;
	private final RevalClanConfig config;
	private final ClanRankIconResolver rankIconResolver;
	private final SpriteManager spriteManager;
	private final MouseManager mouseManager;
	private final KeyManager keyManager;
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

	private final KeyListener escCloser = new KeyListener() {
		@Override
		public void keyTyped(KeyEvent e) {
		}

		@Override
		public void keyPressed(KeyEvent e) {
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE && overlay.isOpen()) {
				e.consume();
				close();
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
		}
	};

	@Inject
	public PlayerCardManager(Client client, RevalClanConfig config, ClanRankIconResolver rankIconResolver,
							 SpriteManager spriteManager, MouseManager mouseManager, KeyManager keyManager,
							 PlayerCardOverlay overlay) {
		this.client = client;
		this.config = config;
		this.rankIconResolver = rankIconResolver;
		this.spriteManager = spriteManager;
		this.mouseManager = mouseManager;
		this.keyManager = keyManager;
		this.overlay = overlay;
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		if (!config.playerProfileCards()) {
			return;
		}
		Set<String> seen = new HashSet<>();
		for (MenuEntry entry : event.getMenuEntries()) {
			// Players in the world
			Player player = entry.getPlayer();
			if (player != null && player.isClanMember() && player.getName() != null) {
				addProfileEntry(seen, Text.removeTags(player.getName()), entry.getTarget());
				continue;
			}
			// Rows in the clan sidepanel member list (the "Hop-to" menu)
			if (entry.getParam1() == InterfaceID.ClansSidepanel.PLAYERLIST && entry.getTarget() != null) {
				String name = Text.removeTags(entry.getTarget());
				if (!name.isEmpty()) {
					addProfileEntry(seen, name, entry.getTarget());
				}
			}
		}
	}

	private void addProfileEntry(Set<String> seen, String name, String target) {
		if (!seen.add(name)) {
			return;
		}
		// Entries render bottom-up: Cancel sits at index 0, so index 1 shows
		// the option at the bottom of the list, right above Cancel
		client.getMenu().createMenuEntry(1)
			.setOption("View Reval Profile")
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> open(name));
	}

	private void open(String playerName) {
		PlayerCardData data = PlayerCardData.mock(playerName);
		if (!overlay.isOpen()) {
			mouseManager.registerMouseListener(clickCloser);
			keyManager.registerKeyListener(escCloser);
		}
		overlay.show(data, rankColor(data.getRankName()));
		rankIconResolver.resolve(data.getRankName(), spriteId ->
			spriteManager.getSpriteAsync(spriteId, 0, overlay::setRankSprite));
		if (data.getNextRankName() != null) {
			rankIconResolver.resolve(data.getNextRankName(), spriteId ->
				spriteManager.getSpriteAsync(spriteId, 0, overlay::setNextRankSprite));
		}
	}

	private static Color rankColor(String rankName) {
		Color color = RANK_COLORS.get(PlayerNames.normalize(rankName));
		return color != null ? color : UIConstants.ACCENT_GOLD;
	}

	private void close() {
		overlay.close();
		closedAt = System.currentTimeMillis();
		mouseManager.unregisterMouseListener(clickCloser);
		keyManager.unregisterKeyListener(escCloser);
	}

	/** Closes an open card; called when the plugin shuts down. */
	public void shutDown() {
		if (overlay.isOpen()) {
			close();
		}
	}
}

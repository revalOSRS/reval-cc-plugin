package com.revalclan.playercards;

import com.revalclan.RevalClanConfig;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.playercards.ProfileCardResponse;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.RankColors;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Adds a "View Reval Profile" right-click option on clan members and shows
 * their {@link PlayerCardOverlay} in-game, fed by the profile-card endpoint.
 * While the card is open, mouse input is consumed so the game doesn't react;
 * a click or Escape closes it.
 */
@Singleton
public class PlayerCardManager {
	/**
	 * Input phases while the card is up. The menu click that opens the card
	 * delivers its release (and trailing click) to our listener, so events
	 * pass through until that first release; the closing click's trailing
	 * click event must still be consumed after the card closes.
	 */
	private enum ClickPhase { OPENING, OPEN, CLOSING }

	private final Client client;
	private final RevalClanConfig config;
	private final RevalApiService apiService;
	private final ClanRankIconResolver rankIconResolver;
	private final SpriteManager spriteManager;
	private final MouseManager mouseManager;
	private final KeyManager keyManager;
	private final PlayerCardOverlay overlay;

	// Written on the client thread (open) and the AWT input thread (listeners)
	private volatile ClickPhase clickPhase;
	private volatile boolean listenersRegistered;

	private final MouseAdapter clickCloser = new MouseAdapter() {
		@Override
		public MouseEvent mousePressed(MouseEvent e) {
			if (clickPhase == ClickPhase.OPEN) {
				e.consume();
			} else if (clickPhase == ClickPhase.CLOSING) {
				// The trailing click never came (e.g. drag); stop listening
				unregisterListeners();
			}
			return e;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e) {
			if (clickPhase == ClickPhase.OPENING) {
				// The release of the click that opened the card
				clickPhase = ClickPhase.OPEN;
			} else if (clickPhase == ClickPhase.OPEN) {
				e.consume();
				overlay.close();
				clickPhase = ClickPhase.CLOSING;
			}
			return e;
		}

		@Override
		public MouseEvent mouseClicked(MouseEvent e) {
			if (clickPhase == ClickPhase.OPEN) {
				e.consume();
			} else if (clickPhase == ClickPhase.CLOSING) {
				// Swallow the click that trails the closing release
				e.consume();
				unregisterListeners();
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
				overlay.close();
				unregisterListeners();
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
		}
	};

	@Inject
	public PlayerCardManager(Client client, RevalClanConfig config, RevalApiService apiService,
							 ClanRankIconResolver rankIconResolver,
							 SpriteManager spriteManager, MouseManager mouseManager, KeyManager keyManager,
							 PlayerCardOverlay overlay) {
		this.client = client;
		this.config = config;
		this.apiService = apiService;
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
		if (!listenersRegistered) {
			mouseManager.registerMouseListener(clickCloser);
			keyManager.registerKeyListener(escCloser);
			listenersRegistered = true;
		}
		clickPhase = ClickPhase.OPENING;
		overlay.showLoading(playerName);
		apiService.fetchProfileCard(playerName,
			response -> {
				if (!overlay.isLoadingFor(playerName)) {
					return;
				}
				if (response.getData() == null) {
					overlay.showError("No Reval profile found for " + playerName);
					return;
				}
				showCard(playerName, response.getData());
			},
			error -> {
				if (overlay.isLoadingFor(playerName)) {
					overlay.showError("Couldn't load the Reval profile");
				}
			});
	}

	private void showCard(String playerName, ProfileCardResponse.CardData profile) {
		PlayerCardData data = PlayerCardData.from(profile);
		overlay.show(data, RankColors.forSlug(profile.getClanRank()));
		rankIconResolver.resolve(data.getRankName(), spriteId ->
			spriteManager.getSpriteAsync(spriteId, 0, overlay::setRankSprite));
		if (data.getNextRankName() != null) {
			rankIconResolver.resolve(data.getNextRankName(), spriteId ->
				spriteManager.getSpriteAsync(spriteId, 0, overlay::setNextRankSprite));
		}
	}

	private void unregisterListeners() {
		mouseManager.unregisterMouseListener(clickCloser);
		keyManager.unregisterKeyListener(escCloser);
		listenersRegistered = false;
		clickPhase = null;
	}

	/** Closes an open card; called when the plugin shuts down. */
	public void shutDown() {
		overlay.close();
		if (listenersRegistered) {
			unregisterListeners();
		}
	}
}

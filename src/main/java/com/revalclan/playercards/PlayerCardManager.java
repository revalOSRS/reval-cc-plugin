package com.revalclan.playercards;

import com.revalclan.RevalClanConfig;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.playercards.ProfileCardResponse;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.RankColors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
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
@Slf4j
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

	/** Options that mark a player-name chat menu (the Hiscore lookup pattern). */
	private static final Set<String> CHAT_PLAYER_OPTIONS = Set.of(
		"Add friend", "Remove friend", "Add ignore", "Message");

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		Set<String> seen = new HashSet<>();
		for (MenuEntry entry : event.getMenuEntries()) {
			// Players in the world
			Player player = entry.getPlayer();
			if (player != null && player.isClanMember() && player.getName() != null) {
				if (config.profileCardsOnPlayers()) {
					addProfileEntry(seen, cleanName(player.getName()), entry.getTarget());
				}
				continue;
			}
			// Rows in the clan sidepanel member list (the "Hop-to" menu)
			if (entry.getParam1() == InterfaceID.ClansSidepanel.PLAYERLIST && entry.getTarget() != null) {
				if (config.profileCardsInClanList()) {
					String name = cleanName(entry.getTarget());
					if (!name.isEmpty()) {
						addProfileEntry(seen, name, entry.getTarget());
					}
				}
				continue;
			}
			// Player names in chat lines, restricted to clanmates in the channel
			if (config.profileCardsInChat() && entry.getTarget() != null && !entry.getTarget().isEmpty()
				&& CHAT_PLAYER_OPTIONS.contains(entry.getOption())) {
				String name = cleanName(entry.getTarget());
				if (!name.isEmpty() && isClanChannelMember(name)) {
					addProfileEntry(seen, name, entry.getTarget());
				}
			}
		}

		// The !revalprofile message itself: right-clicking anywhere on the
		// rewritten chat line offers the card
		if (config.profileCardsInChat()) {
			String name = profileLineUnderMouse();
			if (name != null && !name.isEmpty()) {
				addProfileEntry(seen, name, name);
			}
		}
	}

	/** Name from a "View X's Reval Profile" chat line under the mouse, or null. */
	private String profileLineUnderMouse() {
		Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		if (scrollArea == null || scrollArea.isHidden()) {
			return null;
		}
		Widget[] children = scrollArea.getDynamicChildren();
		if (children == null) {
			return null;
		}
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		for (Widget child : children) {
			String text = child.getText();
			if (text == null || text.isEmpty()) {
				continue;
			}
			String plain = cleanName(text);
			if (!plain.startsWith(ProfileChatCommands.PROFILE_PREFIX)) {
				continue;
			}
			int suffix = plain.indexOf(ProfileChatCommands.PROFILE_SUFFIX);
			if (suffix <= ProfileChatCommands.PROFILE_PREFIX.length()) {
				continue;
			}
			java.awt.Rectangle bounds = child.getBounds();
			if (bounds != null && bounds.contains(mouse.getX(), mouse.getY())) {
				return plain.substring(ProfileChatCommands.PROFILE_PREFIX.length(), suffix);
			}
		}
		return null;
	}

	private boolean isClanChannelMember(String name) {
		ClanChannel clanChannel = client.getClanChannel();
		if (clanChannel == null) {
			return false;
		}
		String standardized = Text.standardize(name);
		for (ClanChannelMember member : clanChannel.getMembers()) {
			if (member.getName() != null && Text.standardize(member.getName()).equals(standardized)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Menu names need both cleanups: sidepanel targets carry color tags,
	 * in-game names carry NBSPs - the backend lookup matches neither.
	 */
	private static String cleanName(String raw) {
		return Text.removeTags(raw).replace('\u00A0', ' ');
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
				if (!overlay.isLoadingFor(playerName)) {
					return;
				}
				String message = error.getMessage();
				log.warn("Profile card fetch failed for {}: {}", playerName, message);
				overlay.showError("Player not found".equals(message)
					? "No Reval profile found for " + playerName
					: "Couldn't load the Reval profile" + (message != null ? " (" + message + ")" : ""));
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

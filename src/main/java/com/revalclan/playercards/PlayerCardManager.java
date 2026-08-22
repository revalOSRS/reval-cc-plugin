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
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * Adds a "View Reval Profile" right-click option on clan members and opens
 * their {@link PlayerCardWindow}. Card data is mocked until the profile
 * endpoint exists.
 */
@Singleton
public class PlayerCardManager {
	private static final Color DEFAULT_ACCENT = UIConstants.ACCENT_GOLD;

	private final Client client;
	private final MockTeamColorProvider teamColors;
	private final ClanRankIconResolver rankIconResolver;

	private PlayerCardWindow window;

	@Inject
	public PlayerCardManager(Client client, MockTeamColorProvider teamColors, ClanRankIconResolver rankIconResolver) {
		this.client = client;
		this.teamColors = teamColors;
		this.rankIconResolver = rankIconResolver;
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
		Color accent = teamColor != null ? teamColor : DEFAULT_ACCENT;
		SwingUtilities.invokeLater(() -> {
			if (window != null) {
				window.dispose();
			}
			window = new PlayerCardWindow(PlayerCardData.mock(playerName), accent, rankIconResolver);
			window.setVisible(true);
		});
	}

	/** Closes an open card; called when the plugin shuts down. */
	public void shutDown() {
		SwingUtilities.invokeLater(() -> {
			if (window != null) {
				window.dispose();
				window = null;
			}
		});
	}
}

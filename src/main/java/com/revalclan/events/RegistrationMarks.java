package com.revalclan.events;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.util.PlayerNames;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ScriptID;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin-only: appends a checkmark to clan sidepanel names of members who have
 * registered for an upcoming event. {@link RegistrationMarksOverlay} shows
 * which event(s) on hover.
 */
@Slf4j
@Singleton
public class RegistrationMarks {
	/** Same in-game gate as the side panel's admin button (Deputy Owner+). */
	private static final int ADMIN_MIN_CLAN_RANK = 125;
	private static final long REFRESH_MS = 5 * 60_000;

	private final Client client;
	private final ClientThread clientThread;
	private final RevalApiService apiService;

	/** Normalized nickname -> comma-joined upcoming event names */
	private volatile Map<String, String> registrations = Map.of();
	private volatile long fetchedAt;
	private volatile boolean fetching;
	private int iconIdx = -1;

	@Inject
	public RegistrationMarks(Client client, ClientThread clientThread, RevalApiService apiService) {
		this.client = client;
		this.clientThread = clientThread;
		this.apiService = apiService;
	}

	public void startUp() {
		clientThread.invoke(() -> {
			if (client.getGameState() == GameState.LOGGED_IN) {
				loadIcon();
				refresh();
			}
		});
	}

	Map<String, String> getRegistrations() {
		return registrations;
	}

	boolean isAdminViewer() {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return false;
		}
		ClanChannel clanChannel = client.getClanChannel();
		String name = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		if (clanChannel == null || name == null) {
			return false;
		}
		ClanChannelMember member = clanChannel.findMember(name);
		return member != null && member.getRank().getRank() >= ADMIN_MIN_CLAN_RANK;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			loadIcon();
			refresh();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() != ScriptID.CLAN_SIDEPANEL_DRAW) {
			return;
		}
		if (System.currentTimeMillis() - fetchedAt > REFRESH_MS) {
			refresh();
		}
		decorate();
	}

	private void refresh() {
		if (fetching) {
			return;
		}
		fetching = true;
		apiService.fetchEvents(
			response -> {
				fetching = false;
				fetchedAt = System.currentTimeMillis();
				Map<String, String> map = new HashMap<>();
				if (response.getData() != null && response.getData().getEvents() != null) {
					for (EventsResponse.EventSummary event : response.getData().getEvents()) {
						if (!event.isUpcoming() || event.getRegistrations() == null) {
							continue;
						}
						for (EventsResponse.EventRegistration reg : event.getRegistrations()) {
							if (!"registered".equalsIgnoreCase(reg.getStatus()) || reg.getOsrsNickname() == null) {
								continue;
							}
							map.merge(PlayerNames.normalize(reg.getOsrsNickname()), event.getName(),
								(a, b) -> a + ", " + b);
						}
					}
				}
				registrations = map;
				clientThread.invoke(this::decorate);
			},
			error -> {
				fetching = false;
				// Keep the stale map and back off until the next refresh window
				fetchedAt = System.currentTimeMillis();
				log.debug("Failed to fetch event registrations", error);
			}
		);
	}

	private void decorate() {
		if (iconIdx == -1 || registrations.isEmpty() || !isAdminViewer()) {
			return;
		}
		Widget list = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		if (list == null) {
			return;
		}
		Widget[] children = list.getDynamicChildren();
		if (children == null) {
			return;
		}
		String tag = " <img=" + iconIdx + ">";
		for (Widget child : children) {
			String text = child.getText();
			// Rows hold a name widget and a world widget; skip worlds and
			// names already decorated on a previous pass
			if (text == null || text.isEmpty() || text.matches("W\\d+") || text.contains(tag)) {
				continue;
			}
			if (registrations.containsKey(PlayerNames.normalize(Text.removeTags(text)))) {
				child.setText(text + tag);
			}
		}
	}

	/** Registers the checkmark as a chat mod icon so widget text can embed it. */
	private void loadIcon() {
		if (iconIdx != -1) {
			return;
		}
		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null) {
			return;
		}
		BufferedImage image = ImageUtil.loadImageResource(RegistrationMarks.class, "/com/revalclan/ui/assets/checkmark.png");
		BufferedImage scaled = ImageUtil.resizeImage(image, 11, 11);
		IndexedSprite[] newIcons = Arrays.copyOf(modIcons, modIcons.length + 1);
		newIcons[modIcons.length] = ImageUtil.getImageIndexedSprite(scaled, client);
		client.setModIcons(newIcons);
		iconIdx = modIcons.length;
	}
}

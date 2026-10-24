package com.revalclan.util;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.announcements.AnnouncementsResponse.Announcement;
import com.revalclan.api.notifications.NotificationsResponse.Notification;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class AnnouncementService {
	private static final int NOTIFICATION_INTERVAL_TICKS = 6000; // ~60 minutes; login and heartbeat hints fetch sooner.
	private int notificationTicksRemaining = NOTIFICATION_INTERVAL_TICKS;
	private static final int INITIAL_DELAY_TICKS = 5;
	private static final int RETRY_TICKS = 100; // ~1 minute.

	@Inject private ChatMessageManager chatMessageManager;
	@Inject private RevalApiService revalApiService;
	@Inject private Client client;
	@Inject private com.revalclan.RevalClanConfig config;

	private int sessionGeneration;
	private String appliedNotificationVersion;
	private int tickCounter = 0;
	private boolean initialFetchDone = false;
	private boolean announcementFetchInProgress = false;
	private boolean notificationFetchInProgress = false;

	private final Set<Integer> shownBroadcastIds = new HashSet<>();
	private final Map<Integer, Long> lastChatShownTime = new HashMap<>();
	private final List<Announcement> cachedAnnouncements = new CopyOnWriteArrayList<>();

	public synchronized void onGameTick() {
		if (!config.showAnnouncements()) {
			return;
		}

		tickCounter++;

		if (tickCounter < INITIAL_DELAY_TICKS) {
			return;
		}

		if (!initialFetchDone) {
			initialFetchDone = true;
			fetchAnnouncements();
			fetchNotifications();
			return;
		}

		if (!notificationFetchInProgress && --notificationTicksRemaining <= 0) {
			fetchNotifications();
		}

		processChatAnnouncements();
	}

	/** Called on the client thread after a current login/heartbeat response. */
	public synchronized void onServerVersion(String version) {
		if (config.showAnnouncements() && initialFetchDone
			&& (version == null || !version.equals(appliedNotificationVersion))) fetchNotifications();
	}

	private void fetchAnnouncements() {
		if (announcementFetchInProgress) {
			return;
		}
		announcementFetchInProgress = true;
		final int generation = sessionGeneration;

		revalApiService.fetchAnnouncements(
			response -> {
				synchronized (AnnouncementService.this) {
					if (generation != sessionGeneration) return;
					announcementFetchInProgress = false;
					if (response.getData() != null && response.getData().getAnnouncements() != null) {
						cachedAnnouncements.clear();
						cachedAnnouncements.addAll(response.getData().getAnnouncements());
						processBroadcasts();
					}
				}
			},
			error -> {
				synchronized (AnnouncementService.this) {
					if (generation != sessionGeneration) return;
					announcementFetchInProgress = false;
				}
			}
		);
	}

	private void fetchNotifications() {
		if (notificationFetchInProgress) {
			return;
		}

		long accountHash = client.getAccountHash();
		if (accountHash == -1) {
			return;
		}

		notificationFetchInProgress = true;
		notificationTicksRemaining = NOTIFICATION_INTERVAL_TICKS;
		final int generation = sessionGeneration;
		revalApiService.fetchNotifications(accountHash,
			response -> {
				synchronized (AnnouncementService.this) {
					if (generation != sessionGeneration) return;
					notificationFetchInProgress = false;
					if (response.getData() != null && response.getData().getNotifications() != null) {
						appliedNotificationVersion = response.getData().getVersion();
					} else {
						notificationTicksRemaining = RETRY_TICKS;
					}
					if (response.getData() != null && response.getData().getNotifications() != null
						&& !response.getData().getNotifications().isEmpty()) {
						displayAndAcknowledgeNotifications(accountHash, response.getData().getNotifications());
					}
				}
			},
			error -> {
				synchronized (AnnouncementService.this) {
					if (generation != sessionGeneration) return;
					notificationFetchInProgress = false;
					notificationTicksRemaining = RETRY_TICKS;
				}
			}
		);
	}

	private void processBroadcasts() {
		cachedAnnouncements.stream()
			.filter(Announcement::isBroadcast)
			.filter(a -> !shownBroadcastIds.contains(a.getId()))
			.sorted(Comparator.comparingInt(Announcement::getPriority).reversed())
			.forEach(a -> {
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.BROADCAST)
					.runeLiteFormattedMessage(formatMessage(a.getTitle(), a.getMessage()))
					.build());
				shownBroadcastIds.add(a.getId());
			});
	}

	private void processChatAnnouncements() {
		long now = System.currentTimeMillis();

		for (Announcement a : cachedAnnouncements) {
			if (!a.isChat()) {
				continue;
			}

			long intervalMs = a.getIntervalMinutes() * 60L * 1000L;
			Long lastShown = lastChatShownTime.get(a.getId());

			if (lastShown != null && (now - lastShown) < intervalMs) {
				continue;
			}

			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CLAN_MESSAGE)
				.sender("Reval")
				.runeLiteFormattedMessage("<col=FFD700>" + a.getMessage() + "</col>")
				.build());
			lastChatShownTime.put(a.getId(), now);
		}
	}

	private void displayAndAcknowledgeNotifications(long accountHash, List<Notification> notifications) {
		List<Integer> idsToAck = new ArrayList<>();

		for (Notification notification : notifications) {
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.BROADCAST)
				.runeLiteFormattedMessage(formatNotificationMessage(notification))
				.build());
			idsToAck.add(notification.getId());
		}

		if (!idsToAck.isEmpty()) {
			revalApiService.acknowledgeNotifications(accountHash, idsToAck,
				ackResponse -> {},
				error -> {}
			);
		}
	}

	private String formatNotificationMessage(Notification notification) {
		return formatMessage(notification.getTitle(), notification.getMessage());
	}

	/** Broadcasts and notifications share one look: gold "[Reval] Title:" (or "[Reval]") then the message. */
	private static String formatMessage(String title, String message) {
		if (title != null && !title.isEmpty()) {
			return "<col=FFD700>[Reval] " + title + ":</col> " + message;
		}
		return "<col=FFD700>[Reval]</col> " + message;
	}

	public synchronized void reset() {
		sessionGeneration++;
		appliedNotificationVersion = null;
		notificationTicksRemaining = NOTIFICATION_INTERVAL_TICKS;
		tickCounter = 0;
		initialFetchDone = false;
		announcementFetchInProgress = false;
		notificationFetchInProgress = false;
		shownBroadcastIds.clear();
		lastChatShownTime.clear();
		cachedAnnouncements.clear();
	}
}

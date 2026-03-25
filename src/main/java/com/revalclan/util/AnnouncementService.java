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
	private static final int POLL_INTERVAL_TICKS = 500;  // ~5 minutes
	private static final int INITIAL_DELAY_TICKS = 5;

	@Inject private ChatMessageManager chatMessageManager;
	@Inject private RevalApiService revalApiService;
	@Inject private Client client;

	private int tickCounter = 0;
	private boolean initialFetchDone = false;
	private volatile boolean announcementFetchInProgress = false;
	private volatile boolean notificationFetchInProgress = false;

	private final Set<Integer> shownBroadcastIds = new HashSet<>();
	private final Map<Integer, Long> lastChatShownTime = new HashMap<>();
	private final List<Announcement> cachedAnnouncements = new CopyOnWriteArrayList<>();

	public void onGameTick() {
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

		if (tickCounter % POLL_INTERVAL_TICKS == 0) {
			fetchNotifications();
		}

		processChatAnnouncements();
	}

	private void fetchAnnouncements() {
		if (announcementFetchInProgress) {
			return;
		}
		announcementFetchInProgress = true;

		revalApiService.fetchAnnouncements(
			response -> {
				announcementFetchInProgress = false;
				if (response.getData() != null && response.getData().getAnnouncements() != null) {
					cachedAnnouncements.clear();
					cachedAnnouncements.addAll(response.getData().getAnnouncements());
					processBroadcasts();
				}
			},
			error -> announcementFetchInProgress = false
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
		revalApiService.fetchNotifications(accountHash,
			response -> {
				notificationFetchInProgress = false;
				if (response.getData() != null && response.getData().getNotifications() != null
					&& !response.getData().getNotifications().isEmpty()) {
					displayAndAcknowledgeNotifications(response.getData().getNotifications());
				}
			},
			error -> notificationFetchInProgress = false
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
					.runeLiteFormattedMessage(formatBroadcastMessage(a))
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

	private void displayAndAcknowledgeNotifications(List<Notification> notifications) {
		List<Integer> idsToAck = new ArrayList<>();

		for (Notification notification : notifications) {
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.BROADCAST)
				.runeLiteFormattedMessage(formatNotificationMessage(notification))
				.build());
			idsToAck.add(notification.getId());
		}

		if (!idsToAck.isEmpty()) {
			revalApiService.acknowledgeNotifications(client.getAccountHash(), idsToAck,
				ackResponse -> {},
				error -> {}
			);
		}
	}

	private String formatBroadcastMessage(Announcement announcement) {
		if (announcement.getTitle() != null && !announcement.getTitle().isEmpty()) {
			return "<col=FFD700>[Reval] " + announcement.getTitle() + ":</col> " + announcement.getMessage();
		}
		return "<col=FFD700>[Reval]</col> " + announcement.getMessage();
	}

	private String formatNotificationMessage(Notification notification) {
		if (notification.getTitle() != null && !notification.getTitle().isEmpty()) {
			return "<col=FFD700>[Reval] " + notification.getTitle() + ":</col> " + notification.getMessage();
		}
		return "<col=FFD700>[Reval]</col> " + notification.getMessage();
	}

	public void reset() {
		tickCounter = 0;
		initialFetchDone = false;
		announcementFetchInProgress = false;
		notificationFetchInProgress = false;
		shownBroadcastIds.clear();
		lastChatShownTime.clear();
		cachedAnnouncements.clear();
	}
}

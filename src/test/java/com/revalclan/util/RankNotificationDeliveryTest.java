package com.revalclan.util;

import com.google.gson.Gson;
import com.revalclan.RevalClanConfig;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.announcements.AnnouncementsResponse;
import com.revalclan.api.notifications.NotificationAckResponse;
import com.revalclan.api.notifications.NotificationsResponse;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RankNotificationDeliveryTest {
	private final AnnouncementService service = new AnnouncementService();
	private final FakeApi api = new FakeApi();
	private final Client client = mock(Client.class);
	private final RevalClanConfig config = mock(RevalClanConfig.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final ClanRankAnnouncements ranks = mock(ClanRankAnnouncements.class);
	private final ChatMessageManager chat = mock(ChatMessageManager.class);
	private final List<Runnable> jobs = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		inject("revalApiService", api); inject("client", client); inject("config", config);
		inject("clientThread", clientThread); inject("clanRankAnnouncements", ranks);
		inject("chatMessageManager", chat);
		when(client.getAccountHash()).thenReturn(42L);
		when(config.showAnnouncements()).thenReturn(true);
		doAnswer(call -> { jobs.add(call.getArgument(0)); return null; }).when(clientThread).invoke(any(Runnable.class));
		for (int i = 0; i < 5; i++) service.onGameTick();
	}

	@Test
	public void rendersEarnedPromotionOnClientThreadAndAcknowledgesIt() {
		deliver("{\"type\":\"rank_earned\",\"playerName\":\"Shafli\",\"newRank\":\"sapphire\"}");
		verifyNoInteractions(ranks);
		assertTrue(api.acks.isEmpty());
		jobs.get(0).run();
		verify(ranks).announceEarned("Shafli", "sapphire");
		verifyNoInteractions(chat);
		assertEquals(List.of(List.of(7)), api.acks);
	}

	@Test
	public void repeatedDeliveryRetriesAcknowledgementWithoutRepeatingChat() {
		deliver("{\"type\":\"rank_earned\",\"playerName\":\"Shafli\",\"newRank\":\"sapphire\"}");
		jobs.get(0).run();
		service.onServerVersion("v2");
		deliver("{\"type\":\"rank_earned\",\"playerName\":\"Shafli\",\"newRank\":\"sapphire\"}");
		jobs.get(1).run();
		verify(ranks, times(1)).announceEarned("Shafli", "sapphire");
		assertEquals(2, api.acks.size());
	}

	@Test
	public void queuedResponseFromPreviousLoginDoesNotRenderOrAcknowledge() {
		deliver("{\"type\":\"rank_earned\",\"playerName\":\"Shafli\",\"newRank\":\"sapphire\"}");
		service.reset(); jobs.get(0).run();
		verifyNoInteractions(ranks, chat);
		assertTrue(api.acks.isEmpty());
	}

	@Test
	public void disablingNotificationsBeforeClientThreadDeliveryDoesNotConsumeThem() {
		deliver("{\"type\":\"rank_earned\",\"playerName\":\"Shafli\",\"newRank\":\"sapphire\"}");
		when(config.showAnnouncements()).thenReturn(false); jobs.get(0).run();
		verifyNoInteractions(ranks, chat);
		assertTrue(api.acks.isEmpty());
	}

	@Test
	public void ordinaryOrMalformedRankNotificationsRetainBroadcastFallback() {
		deliver("{\"type\":\"rank_earned\",\"playerName\":123}");
		jobs.get(0).run();
		ArgumentCaptor<QueuedMessage> message = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chat).queue(message.capture());
		assertEquals(ChatMessageType.BROADCAST, message.getValue().getType());
		assertTrue(message.getValue().getRuneLiteFormattedMessage().contains("Fallback message"));
		verifyNoInteractions(ranks);
		assertEquals(List.of(List.of(7)), api.acks);
	}

	private void deliver(String metadata) {
		NotificationsResponse response = new Gson().fromJson("{\"status\":\"success\",\"data\":{\"version\":\"v1\",\"notifications\":[{\"id\":7,\"message\":\"Fallback message\",\"metadata\":" + metadata + "}]}}", NotificationsResponse.class);
		api.responses.get(api.responses.size() - 1).accept(response);
	}

	private void inject(String name, Object value) throws Exception {
		Field field = AnnouncementService.class.getDeclaredField(name);
		field.setAccessible(true); field.set(service, value);
	}

	private static class FakeApi extends RevalApiService {
		final List<Consumer<NotificationsResponse>> responses = new ArrayList<>();
		final List<List<Integer>> acks = new ArrayList<>();
		FakeApi() { super(null, new Gson()); }
		@Override public void fetchAnnouncements(Consumer<AnnouncementsResponse> ok, Consumer<Exception> error) {}
		@Override public void fetchNotifications(long account, Consumer<NotificationsResponse> ok, Consumer<Exception> error) { responses.add(ok); }
		@Override public void acknowledgeNotifications(long account, List<Integer> ids, Consumer<NotificationAckResponse> ok, Consumer<Exception> error) { acks.add(new ArrayList<>(ids)); }
	}
}

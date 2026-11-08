package com.revalclan.notifiers;

import com.revalclan.collectionlog.GreenlogCounterSnapshot;
import com.revalclan.session.SessionState;
import com.revalclan.util.ClanMembership;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.WebhookService;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PetNotifierTest {
	@Mock private Client client;
	@Mock private ClanMembership membership;
	@Mock private EventFilterManager filters;
	@Mock private GreenlogCounterSnapshot counters;
	@Mock private WebhookService webhooks;
	@InjectMocks private PetNotifier pets;
	private int dropCounter;

	@Before public void setUp() {
		when(filters.getFilters()).thenReturn(new EventFilterManager.EventFilters());
		doAnswer(invocation -> {
			Map<String, Object> payload = invocation.getArgument(0);
			payload.put("greenlogCounters", dropCounter);
			return null;
		}).when(counters).attachTo(anyMap(), isNull());
	}

	@Test public void aDelayedPetNameKeepsTheDropTicksCounters() {
		when(membership.isMember()).thenReturn(true);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		when(webhooks.sendDataAsync(anyMap())).thenReturn(new CompletableFuture<>());
		dropCounter = 123;
		pets.onChatMessage("You have a funny feeling like you're being followed.");
		pets.onGameTick();
		dropCounter = 999;
		for (int tick = 0; tick < 10; tick++) pets.onGameTick();

		verify(counters).attachTo(anyMap(), isNull());
		verify(webhooks).sendDataAsync(argThat(payload ->
			"PET".equals(payload.get("eventType"))
				&& Integer.valueOf(123).equals(payload.get("greenlogCounters"))));
	}

	@Test public void logoutDropsThePendingPetAndItsCounterSnapshot() {
		SessionState session = new SessionState();
		pets.registerSessionState(session);
		pets.onChatMessage("You have a funny feeling like you're being followed.");
		pets.onGameTick();
		verify(counters).attachTo(anyMap(), isNull());

		session.reset();
		for (int tick = 0; tick < 20; tick++) pets.onGameTick();

		verifyNoInteractions(webhooks);
	}
}

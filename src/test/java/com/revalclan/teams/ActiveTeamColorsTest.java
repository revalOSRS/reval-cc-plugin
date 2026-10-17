package com.revalclan.teams;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.ActiveTeamsResponse;
import java.util.function.Consumer;
import org.mockito.ArgumentCaptor;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ActiveTeamColorsTest {
	@Test public void redrawsShareOnePendingRequest() {
		RevalApiService api = mock(RevalApiService.class);
		ActiveTeamColors colors = new ActiveTeamColors(api);
		for (int i = 0; i < 1000; i++) colors.refresh();
		verify(api, times(1)).fetchActiveTeams(any(), any());
	}

	@Test public void aFailingEndpointDoesNotRetryOnEveryRedraw() {
		RevalApiService api = mock(RevalApiService.class);
		doAnswer(invocation -> {
			invocation.<Consumer<Exception>>getArgument(1).accept(new Exception("offline"));
			return null;
		}).when(api).fetchActiveTeams(any(), any());
		ActiveTeamColors colors = new ActiveTeamColors(api);
		for (int i = 0; i < 1000; i++) colors.refresh();
		verify(api, times(1)).fetchActiveTeams(any(), any());
	}

	@Test public void anOldResponseCannotThrottleTheNextPluginStart() {
		RevalApiService api = mock(RevalApiService.class);
		ActiveTeamColors colors = new ActiveTeamColors(api);
		colors.refresh();
		ArgumentCaptor<Consumer<ActiveTeamsResponse>> success = ArgumentCaptor.forClass(Consumer.class);
		verify(api).fetchActiveTeams(success.capture(), any());
		colors.reset();
		success.getValue().accept(new ActiveTeamsResponse());
		colors.refresh();
		verify(api, times(2)).fetchActiveTeams(any(), any());
	}

	@Test public void cachedRosterIsNotRebuiltForEveryRedraw() {
		RevalApiService api = mock(RevalApiService.class);
		ActiveTeamsResponse response = mock(ActiveTeamsResponse.class);
		doAnswer(invocation -> {
			invocation.<Consumer<ActiveTeamsResponse>>getArgument(0).accept(response);
			return null;
		}).when(api).fetchActiveTeams(any(), any());
		ActiveTeamColors colors = new ActiveTeamColors(api);
		for (int i = 0; i < 1000; i++) colors.refresh();
		verify(api, times(1)).fetchActiveTeams(any(), any());
	}
}

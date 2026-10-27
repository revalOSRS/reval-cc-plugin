package com.revalclan.ui.leaguesbingo;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.api.leaguesbingo.LeaguesBingoResponse;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class LeaguesBingoPanelTest {
	@Test public void aLateBoardResponseDoesNotRepopulateAResetPanel() throws Exception {
		RevalApiService api = mock(RevalApiService.class);
		LeaguesBingoPanel[] holder = new LeaguesBingoPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			holder[0] = new LeaguesBingoPanel(api, mock(Client.class), mock(ItemManager.class), mock(SpriteManager.class));
			EventsResponse.EventSummary event = new EventsResponse.EventSummary();
			event.setId("old");
			event.setName("Old event");
			holder[0].open(event);
		});
		ArgumentCaptor<Consumer<LeaguesBingoResponse>> callback = ArgumentCaptor.forClass(Consumer.class);
		verify(api).fetchLeaguesBingoEvent(eq("old"), callback.capture(), any());
		SwingUtilities.invokeAndWait(holder[0]::reset);
		LeaguesBingoResponse response = mock(LeaguesBingoResponse.class);
		callback.getValue().accept(response);
		SwingUtilities.invokeAndWait(() -> {});
		verifyNoInteractions(response);
	}
}

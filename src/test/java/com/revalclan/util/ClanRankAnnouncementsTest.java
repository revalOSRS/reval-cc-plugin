package com.revalclan.util;

import com.revalclan.RevalClanConfig;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.ChatIconManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ClanRankAnnouncementsTest {
	private final Client client = mock(Client.class);
	private final RevalClanConfig config = mock(RevalClanConfig.class);
	private final ClanSettings settings = mock(ClanSettings.class);
	private final ClanChannel channel = mock(ClanChannel.class);
	private final ChatMessageManager chat = mock(ChatMessageManager.class);
	private final ChatIconManager icons = mock(ChatIconManager.class);
	private final ClanMember member = mock(ClanMember.class);
	private final ClanTitle sapphire = new ClanTitle(200, "Sapphire");
	private final ClanRankAnnouncements announcements = new ClanRankAnnouncements(client, config, chat, icons);

	@Before
	public void setUp() {
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClanSettings()).thenReturn(settings);
		when(client.getClanChannel()).thenReturn(channel);
		when(settings.getName()).thenReturn("Reval");
		when(channel.getName()).thenReturn("Reval");
		when(settings.getMembers()).thenReturn(List.of(member));
		when(member.getName()).thenReturn("Shafli");
		when(config.showAnnouncements()).thenReturn(true);
		when(settings.titleForRank(any())).thenReturn(sapphire);
		when(icons.getIconNumber(sapphire)).thenReturn(512);
		rank(10);
	}

	@Test
	public void announcesPromotionOnceWithNewRankIconInClanChat() {
		tick();
		rank(20);
		tick();
		tick();
		ArgumentCaptor<QueuedMessage> message = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chat).queue(message.capture());
		assertEquals(ChatMessageType.CLAN_MESSAGE, message.getValue().getType());
		assertEquals("Reval", message.getValue().getSender());
		assertEquals("Shafli's clan rank is now <img=512> Sapphire.", message.getValue().getRuneLiteFormattedMessage());
	}

	@Test
	public void initialLoadAndNewMembersDoNotAnnounce() {
		tick();
		ClanMember joined = mock(ClanMember.class);
		when(joined.getName()).thenReturn("New Member");
		when(joined.getRank()).thenReturn(new ClanRank(50));
		when(settings.getMembers()).thenReturn(List.of(member, joined));
		tick();
		verifyNoInteractions(chat);
	}

	@Test
	public void demotionIsSilentButSubsequentPromotionAnnounces() {
		tick(); rank(5); tick();
		verifyNoInteractions(chat);
		rank(10); tick();
		verify(chat).queue(any());
	}

	@Test
	public void catchesOfflineMemberPromotionWithoutOnlineRosterLookup() {
		when(channel.findMember("Shafli")).thenReturn(null);
		tick(); rank(20); tick();
		verify(chat).queue(any());
		verify(channel, never()).getMembers();
	}

	@Test
	public void disabledAnnouncementsTrackChangesWithoutReplayingOnEnable() {
		tick();
		when(config.showAnnouncements()).thenReturn(false);
		rank(20); tick();
		when(config.showAnnouncements()).thenReturn(true);
		tick();
		verifyNoInteractions(chat);
		rank(30); tick();
		verify(chat).queue(any());
	}

	@Test
	public void worldHopAndLoginEstablishFreshBaseline() {
		for (GameState state : List.of(GameState.HOPPING, GameState.LOGGING_IN, GameState.LOGIN_SCREEN, GameState.CONNECTION_LOST)) {
			tick();
			GameStateChanged event = new GameStateChanged();
			event.setGameState(state);
			announcements.onGameStateChanged(event);
			rank(member.getRank().getRank() + 10); tick();
		}
		verifyNoInteractions(chat);
	}

	@Test
	public void regionLoadingDoesNotLoseBaseline() {
		tick();
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOADING);
		announcements.onGameStateChanged(event);
		rank(20); tick();
		verify(chat).queue(any());
	}

	@Test
	public void leavingChatAndReturningDoesNotReplayMissedPromotions() {
		tick();
		when(client.getClanChannel()).thenReturn(null);
		tick(); rank(20);
		when(client.getClanChannel()).thenReturn(channel);
		tick();
		verifyNoInteractions(chat);
	}

	@Test
	public void unrelatedClansAndGuestChannelsNeverAnnounce() {
		when(settings.getName()).thenReturn("Another clan");
		tick(); rank(20); tick();
		when(settings.getName()).thenReturn("Reval");
		when(client.getClanChannel()).thenReturn(null);
		when(client.getGuestClanChannel()).thenReturn(channel);
		tick(); rank(30); tick();
		verifyNoInteractions(chat);
	}

	@Test
	public void noIconStillShowsPromotionTitle() {
		when(icons.getIconNumber(sapphire)).thenReturn(-1);
		tick(); rank(20); tick();
		ArgumentCaptor<QueuedMessage> message = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chat).queue(message.capture());
		assertEquals("Shafli's clan rank is now Sapphire.", message.getValue().getRuneLiteFormattedMessage());
	}

	@Test
	public void usesConfiguredTitleAndEscapesChatMarkup() {
		ClanTitle custom = new ClanTitle(200, "Custom <img=1>");
		when(settings.titleForRank(any())).thenReturn(custom);
		when(icons.getIconNumber(custom)).thenReturn(600);
		tick(); rank(20); tick();
		ArgumentCaptor<QueuedMessage> message = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chat).queue(message.capture());
		assertEquals("Shafli's clan rank is now <img=600> Custom <lt>img=1<gt>.", message.getValue().getRuneLiteFormattedMessage());
	}

	@Test
	public void resetForPluginRestartDoesNotReplayChanges() {
		tick(); announcements.reset(); rank(20); tick();
		verifyNoInteractions(chat);
	}

	@Test
	public void earnedRankUsesItsOwnIconBeforeTheClanRosterIsUpdated() {
		EnumComposition names = mock(EnumComposition.class);
		when(client.getEnum(EnumID.CLAN_RANK_NAME)).thenReturn(names);
		when(names.getKeys()).thenReturn(new int[]{200});
		when(names.getStringValue(200)).thenReturn("Sapphire");
		announcements.announceEarned("Shafli", "sapphire");
		ArgumentCaptor<QueuedMessage> message = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chat).queue(message.capture());
		assertEquals(ChatMessageType.CLAN_MESSAGE, message.getValue().getType());
		assertEquals("Shafli has earned <img=512> Sapphire.", message.getValue().getRuneLiteFormattedMessage());
	}

	@Test
	public void earnedRankWithoutAnIconStillShowsItsTitle() {
		announcements.announceEarned("Shafli", "red_topaz");
		ArgumentCaptor<QueuedMessage> message = ArgumentCaptor.forClass(QueuedMessage.class);
		verify(chat).queue(message.capture());
		assertEquals("Shafli has earned Red Topaz.", message.getValue().getRuneLiteFormattedMessage());
	}

	private void rank(int rank) { when(member.getRank()).thenReturn(new ClanRank(rank)); }
	private void tick() { announcements.onGameTick(new GameTick()); }
}

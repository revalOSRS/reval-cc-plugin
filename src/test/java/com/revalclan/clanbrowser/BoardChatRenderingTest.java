package com.revalclan.clanbrowser;

import java.lang.reflect.Constructor;
import com.revalclan.RevalClanConfig;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.PostMenuSort;
import net.runelite.client.events.ConfigChanged;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IterableHashTable;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Replays the actual per-message build callback, including RuneLite's core recoloring. */
public class BoardChatRenderingTest {
    @Test public void sharesAreFormattedBeforeTheirFirstLayoutAndStayFormattedAfterNotifications() throws Exception {
        Fixture f = new Fixture();
        f.add(40, ChatMessageType.PUBLICCHAT, "<img=2>Valgusruum", "!Reval luck araxxor");
        f.add(41, ChatMessageType.PUBLICCHAT, "<img=2>Valgusruum", "!Reval luck yama");
        String notification = "<col=ff0000>PhosaniSpoon has left.</col>";
        f.add(42, ChatMessageType.CLAN_MESSAGE, "Reval", notification);
        for (int pass=0;pass<5;pass++) {
            assertEquals("<col=2b7d77>Click to view Araxxor luck board</col>", f.render(40));
            assertEquals("<col=2b7d77>Click to view Yama luck board</col>", f.render(41));
            assertEquals(notification, f.render(42));
        }
        // Formatting is local and leaves history, the sender and timestamps untouched.
        verify(f.nodes.get(40), never()).setValue(anyString());
        verify(f.nodes.get(40), never()).setRuneLiteFormatMessage(anyString());
        f.render(40);
        assertEquals("<img=2>Valgusruum", f.stack[1]);
        assertEquals("[19:48]", f.stack[3]);
    }

    @Test public void chatBackgroundsUseReadableTealAndDisableReturnsToOriginalMessages() throws Exception {
        Fixture f = new Fixture();
        f.add(1, ChatMessageType.PUBLICCHAT, "Valgusruum", "!reval luck yama");
        assertEquals("<col=2b7d77>Click to view Yama luck board</col>",f.render(1));
        when(f.client.isResized()).thenReturn(true);
        when(f.client.getVarbitValue(net.runelite.api.gameval.VarbitID.CHATBOX_TRANSPARENCY)).thenReturn(1);
        assertEquals("<col=63c5b5>Click to view Yama luck board</col>",f.render(1));
        f.add(2, ChatMessageType.GAMEMESSAGE, "Valgusruum", "!reval luck yama");
        assertEquals("System notices must not turn into player shares","!reval luck yama",f.render(2));
        f.add(3, ChatMessageType.CLAN_CHAT, "SomeoneElse", "Click to view Yama luck board");
        assertEquals("Click to view Yama luck board",f.render(3));
        f.manager.shutDown();
        assertEquals("!reval luck yama",f.render(1));
        verify(f.client,times(2)).refreshChat();
    }

    @Test public void chatSettingTakesEffectImmediatelyAndPrivateCommandsStillWork() throws Exception {
        Fixture f = new Fixture();
        f.add(1,ChatMessageType.PUBLICCHAT,"Valgusruum","!reval luck araxxor");
        assertTrue(f.render(1).contains("Click to view Araxxor luck board"));
        when(f.config.enableChatCommands()).thenReturn(false);
        f.configChanged("anotherPlugin","enableChatCommands");
        f.configChanged("revalclan","showAnnouncements");
        assertTrue("Unrelated settings must leave the current link mode unchanged",f.render(1).contains("Click to view"));
        f.configChanged("revalclan","enableChatCommands");
        assertEquals("!reval luck araxxor",f.render(1));
        f.events.post(new PostMenuSort());verify(f.client,never()).getMenu();
        f.events.post(new CommandExecuted("reval",new String[]{"leaderboard","points"}));
        assertEquals("leaderboard",f.privateCommand.get().kind);assertEquals("points",f.privateCommand.get().metric);
        when(f.config.enableChatCommands()).thenReturn(true);f.configChanged("revalclan","enableChatCommands");
        assertTrue("Re-enabling must format existing history without receiving a new message",f.render(1).contains("Click to view Araxxor luck board"));
        verify(f.client,times(3)).refreshChat();
        verify(f.nodes.get(1),never()).setValue(anyString());
    }

    @Test public void aClientStartingWithChatCommandsDisabledKeepsPrivateCommandsAvailable() throws Exception {
        Fixture f = new Fixture(false);
        f.add(1,ChatMessageType.CLAN_CHAT,"Valgusruum","!reval clog forestry");
        assertEquals("!reval clog forestry",f.render(1));
        f.events.post(new PostMenuSort());verify(f.client,never()).getMenu();
        f.events.post(new CommandExecuted("reval",new String[]{"clog","forestry"}));
        assertEquals("clog",f.privateCommand.get().kind);assertEquals("forestry",f.privateCommand.get().category);
        verify(f.client,never()).refreshChat();
        when(f.config.enableChatCommands()).thenReturn(true);
        f.events.post(new net.runelite.client.events.ProfileChanged());
        assertTrue("Changing RuneLite profiles must use that profile's chat preference",f.render(1).contains("Click to view collection log"));
        verify(f.client).refreshChat();
    }

    private static class Fixture {
        final Client client=mock(Client.class);
        final RevalClanConfig config=mock(RevalClanConfig.class);
        final AtomicReference<BrowserCommand> privateCommand=new AtomicReference<>();
        final ClientThread clientThread=mock(ClientThread.class);
        final EventBus events=new EventBus();
        final Map<Integer,MessageNode> nodes=new HashMap<>();
        final Object[] stack=new Object[4];
        final int[] ints=new int[2];
        final ClanBrowserManager manager;
        Fixture() throws Exception { this(true); }
        @SuppressWarnings("unchecked") Fixture(boolean enabled) throws Exception {
            when(config.enableChatCommands()).thenReturn(enabled);
            IterableHashTable<MessageNode> messages=mock(IterableHashTable.class);
            when(client.getMessages()).thenReturn(messages);
            when(messages.get(anyLong())).thenAnswer(i->nodes.get(((Long)i.getArgument(0)).intValue()));
            when(client.getObjectStack()).thenReturn(stack); when(client.getObjectStackSize()).thenReturn(4);
            when(client.getIntStack()).thenReturn(ints); when(client.getIntStackSize()).thenReturn(2);
            Player local=mock(Player.class);when(local.getName()).thenReturn("Valgusruum");when(client.getLocalPlayer()).thenReturn(local);
            doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(clientThread).invoke(any(Runnable.class));
            Constructor<ChatMessageManager> core=ChatMessageManager.class.getDeclaredConstructor(Client.class, ChatColorConfig.class, ClientThread.class, EventBus.class);
            core.setAccessible(true);core.newInstance(client,mock(ChatColorConfig.class),clientThread,events);
            manager=new ClanBrowserManager(client,clientThread,mock(ItemManager.class),mock(BrowserApi.class),config) {
                @Override public void open(BrowserCommand command) { privateCommand.set(command); }
            };
            events.register(manager);manager.startUp();
        }
        void configChanged(String group,String key) {
            ConfigChanged event=new ConfigChanged();event.setGroup(group);event.setKey(key);events.post(event);
        }
        void add(int uid,ChatMessageType type,String name,String text) {
            MessageNode node=mock(MessageNode.class);when(node.getId()).thenReturn(uid);when(node.getType()).thenReturn(type);
            when(node.getName()).thenReturn(name);when(node.getValue()).thenReturn(text);nodes.put(uid,node);
        }
        String render(int uid) {
            MessageNode node=nodes.get(uid);ints[0]=0;ints[1]=uid;
            stack[0]="";stack[1]=node.getName();stack[2]=node.getValue();stack[3]="[19:48]";
            events.post(new ScriptPreFired(BoardChatLinks.CHAT_BUILDER));
            ScriptCallbackEvent event=new ScriptCallbackEvent();event.setEventName("chatMessageBuilding");events.post(event);
            events.post(new ScriptPostFired(BoardChatLinks.CHAT_BUILDER));
            return (String)stack[2];
        }
    }
}

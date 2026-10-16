package com.revalclan.clanbrowser;

import java.lang.reflect.Constructor;
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

    private static class Fixture {
        final Client client=mock(Client.class);
        final ClientThread clientThread=mock(ClientThread.class);
        final EventBus events=new EventBus();
        final Map<Integer,MessageNode> nodes=new HashMap<>();
        final Object[] stack=new Object[4];
        final int[] ints=new int[2];
        final ClanBrowserManager manager;
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            IterableHashTable<MessageNode> messages=mock(IterableHashTable.class);
            when(client.getMessages()).thenReturn(messages);
            when(messages.get(anyLong())).thenAnswer(i->nodes.get(((Long)i.getArgument(0)).intValue()));
            when(client.getObjectStack()).thenReturn(stack); when(client.getObjectStackSize()).thenReturn(4);
            when(client.getIntStack()).thenReturn(ints); when(client.getIntStackSize()).thenReturn(2);
            Player local=mock(Player.class);when(local.getName()).thenReturn("Valgusruum");when(client.getLocalPlayer()).thenReturn(local);
            doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(clientThread).invoke(any(Runnable.class));
            Constructor<ChatMessageManager> core=ChatMessageManager.class.getDeclaredConstructor(Client.class, ChatColorConfig.class, ClientThread.class, EventBus.class);
            core.setAccessible(true);core.newInstance(client,mock(ChatColorConfig.class),clientThread,events);
            manager=new ClanBrowserManager(client,clientThread,mock(ItemManager.class),mock(BrowserApi.class));
            events.register(manager);manager.startUp();
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

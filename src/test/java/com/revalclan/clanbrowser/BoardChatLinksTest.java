package com.revalclan.clanbrowser;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.IterableHashTable;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class BoardChatLinksTest {
    @Test public void readableLabelsKeepTheOriginalBoardAndRestoreTheOriginalChatText() {
        for (String[] example : new String[][] {
            {"luck araxxor", "Araxxor luck board"},
            {"deserved general graardor", "General Graardor deserved board"},
            {"dryboard vorkath", "Vorkath dryboard"},
            {"clog forestry", "collection log: Forestry"},
            {"clog forestry player:Valgusruum", "collection log: Forestry (Valgusruum)"},
            {"clog forestry Valgusruum", "collection log: Forestry Valgusruum"},
            {"leaderboard points", "Total points leaderboard"},
            {"leaderboard xp", "Total XP leaderboard"},
            {"leaderboard clues hard", "Hard clues leaderboard"},
            {"leaderboard bosses vorkath", "Vorkath kills leaderboard"}
        }) {
            Fixture f = new Fixture(); PublicMenu menu = new PublicMenu(f);
            String original = "<col=0000ff>!Reval " + example[0] + "</col>";
            f.receive(original); f.links.startUp();
            String expected = "<col=2b7d77>Click to view " + example[1] + "</col>";
            assertEquals(expected, f.text.get());
            f.links.rebuild(); assertEquals(expected, f.text.get());
            f.links.prioritize(); menu.click.get().accept(menu.link);
            assertEquals(BoardChatLinks.parse(original).parameters(0), f.command.get().parameters(0));
            assertEquals("Alice Smith", f.author.get());
            f.links.clear(); assertEquals(original, f.text.get());
        }
        Fixture ordinary = new Fixture(); PublicMenu menu = new PublicMenu(ordinary);
        ordinary.receive("Click to view Araxxor luck board"); ordinary.links.startUp(); ordinary.links.prioritize();
        verify(menu.menu, never()).createMenuEntry(anyInt());
    }

    @Test public void timestampedPublicSharesRegisterLinksAndKeepTheActualSender() {
        for (String name : Arrays.asList("Valgusruum", "Alice Smith")) {
            for (String time : Arrays.asList("[16:14]", "[16:14:02]", "[4:14 PM]", "[4:14:02 PM]", "2026-10-16 16:14:02", "16:14")) {
                for (String command : Arrays.asList("clog forestry", "luck araxxor", "deserved vorkath", "dryboard vorkath", "leaderboard clues hard")) {
                    Fixture f = new Fixture(); PublicMenu menu = new PublicMenu(f);
                    // Exact rendered shape from the live client, including Timestamp and rank icon.
                    when(f.sender.getText()).thenReturn("<col=000000>" + time + "</col> <img=2>" + name + ":");
                    when(f.row.getName()).thenReturn("<col=ffffff><img=2>" + name + "</col>");
                    f.receive("<col=0000ff>!Reval " + command + "</col>");
                    f.links.startUp();
                    assertTrue("Timestamped shares should be visibly linked", f.text.get().contains("<col=2b7d77>Click to view "));
                    f.links.prioritize();
                    verify(menu.menu).createMenuEntry(-1);
                    menu.click.get().accept(menu.link);
                    assertEquals(name, f.author.get());
                    assertEquals(1, f.opened.get());
                    assertEquals(command.split(" ")[0], f.command.get().kind);
                }
            }
        }
    }

    @Test public void publicChatLinksAreClickableWithoutAReportMenuIncludingYourOwnMessages() {
        for (String name : Arrays.asList("Valgusruum", "Alice Smith")) {
            for (String[] command : new String[][]{{"clog forestry","clog","forestry"},{"luck araxxor","luck","araxxor"},
                  {"deserved vorkath","deserved","vorkath"},{"dryboard vorkath","dryboard","vorkath"},{"leaderboard clues hard","leaderboard","clues"}}) {
                Fixture f = new Fixture(); PublicMenu menu = new PublicMenu(f);
                when(f.sender.getText()).thenReturn(name + ":"); when(f.row.getName()).thenReturn(name);
                f.receive("!reval " + command[0]);
                f.links.startUp(); f.links.prioritize(); f.links.prioritize();
                verify(menu.menu,times(1)).createMenuEntry(-1);
                verify(menu.link).setOption("Open Reval board"); verify(menu.link).setForceLeftClick(true);
                assertEquals("Hovering a public share must not open or fetch a board",0,f.opened.get());
                f.text.set("Another message after scrolling"); when(f.sender.getText()).thenReturn("RecycledName:");
                when(f.row.getName()).thenReturn("RecycledName");
                menu.click.get().accept(menu.link);
                assertEquals(1,f.opened.get()); assertEquals(name,f.author.get());
                assertEquals(command[1],f.command.get().kind);
                assertEquals(command[2],f.command.get().kind.equals("leaderboard") ? f.command.get().metric : f.command.get().category);
                if (f.command.get().kind.equals("leaderboard")) assertEquals("hard",f.command.get().tier);
                f.links.clear(); menu.click.get().accept(menu.link); assertEquals(1,f.opened.get());
            }
        }
    }

    @Test public void publicChatFallbackIgnoresHiddenClippedAndRecycledMessages() {
        Fixture f = new Fixture(); PublicMenu menu = new PublicMenu(f); f.links.startUp();
        when(f.area.isHidden()).thenReturn(true); f.links.prioritize(); when(f.area.isHidden()).thenReturn(false);
        when(f.message.isHidden()).thenReturn(true); f.links.prioritize(); when(f.message.isHidden()).thenReturn(false);
        when(f.message.getBounds()).thenReturn(new java.awt.Rectangle(100,150,300,120));
        when(f.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(140,170)); f.links.prioritize();
        when(f.message.getBounds()).thenReturn(new java.awt.Rectangle(100,200,300,20));
        when(f.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(450,210)); f.links.prioritize();
        when(f.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(140,210));
        f.text.set("An ordinary message"); f.links.prioritize();
        f.text.set("!reval luck araxxor"); when(f.row.getName()).thenReturn(""); f.links.prioritize();
        verify(menu.menu,never()).createMenuEntry(anyInt()); assertEquals(0,f.opened.get());
    }

    @Test public void aReadableLabelDoesNotCarryItsCommandIntoAnotherAccountsRecycledRow() {
        Fixture f = new Fixture(); PublicMenu menu = new PublicMenu(f); f.links.startUp();
        when(f.row.getName()).thenReturn("SomeoneElse");
        f.links.prioritize();
        verify(menu.menu, never()).createMenuEntry(anyInt());
        assertEquals(0, f.opened.get());
    }

    @Test public void notificationsMoveTheRenderedShareWithoutChangingItsClickTarget() {
        Fixture f = new Fixture(); PublicMenu menu = new PublicMenu(f);
        Widget notice = mock(Widget.class), noticeSender = mock(Widget.class), noticeRow = mock(Widget.class);
        when(f.area.getDynamicChildren()).thenReturn(new Widget[]{noticeSender,notice,null,null,f.sender,f.message,null,null});
        when(f.client.getWidget(InterfaceID.Chatbox.LINE0)).thenReturn(noticeRow);
        when(f.client.getWidget(InterfaceID.Chatbox.LINE0+1)).thenReturn(f.row);
        MessageNode notification = mock(MessageNode.class);
        when(notification.getType()).thenReturn(ChatMessageType.CLAN_MESSAGE);
        when(notification.getValue()).thenReturn("Someone has left.");
        when(f.client.getMessages().get(2)).thenReturn(notification);
        f.links.startUp();
        for (int pass=0;pass<3;pass++) {
            f.links.beginBuild();
            f.client.getIntStack()[1]=2; f.client.getObjectStack()[2]="Someone has left.";
            ScriptCallbackEvent event=new ScriptCallbackEvent();event.setEventName("chatMessageBuilding");f.links.format(event);
            String noticeText=(String)f.client.getObjectStack()[2];when(notice.getText()).thenReturn(noticeText);
            f.client.getIntStack()[1]=1;f.client.getObjectStack()[2]="!reval clog forestry";f.links.format(event);
            // The game's outer chat color wraps our label after the callback.
            f.text.set("<col=0000ff>"+f.client.getObjectStack()[2]+"</col>");f.links.finishBuild();
            assertTrue(f.text.get().contains("Click to view collection log: Forestry"));
        }
        f.links.prioritize();menu.click.get().accept(menu.link);
        assertEquals("Alice Smith",f.author.get());assertEquals("forestry",f.command.get().category);
        verify(f.message,never()).setText(anyString());
    }

    @Test public void thePublicChatFilterHeaderDoesNotShiftASharesClickTarget() {
        Fixture f = new Fixture(); PublicMenu menu = new PublicMenu(f);
        Widget header=mock(Widget.class),headerRow=mock(Widget.class);
        when(header.getText()).thenReturn("Public chat filtering:</col> <lt>reval<gt>");
        when(f.area.getDynamicChildren()).thenReturn(new Widget[]{null,header,null,null,f.sender,f.message,null,null});
        when(f.client.getWidget(InterfaceID.Chatbox.LINE0)).thenReturn(headerRow);
        when(f.client.getWidget(InterfaceID.Chatbox.LINE0+1)).thenReturn(f.row);
        f.links.startUp();f.links.prioritize();menu.click.get().accept(menu.link);
        assertEquals("forestry",f.command.get().category);assertEquals("Alice Smith",f.author.get());
    }

    @Test public void sharedCommandsKeepThePageAndUseTheSenderOnlyWhenNoAccountWasSpecified() {
        BrowserModels.Page forestry = new BrowserModels.Page(); forestry.value="forestry"; forestry.label="Forestry";
        BrowserCommand self = BoardChatLinks.parse("<col=ff00ff>!reval clog forestry</col>").resolveShared(Arrays.asList(forestry),"Alice Smith");
        assertEquals("forestry",self.category); assertEquals("Alice Smith",self.player);
        BrowserCommand explicit = BoardChatLinks.parse("!reval clog forestry Valgusruum").resolveShared(Arrays.asList(forestry),"Alice Smith");
        assertEquals("valgusruum",explicit.player);
        assertEquals("hard", BoardChatLinks.parse("!REVAL leaderboard clues hard").tier);
        assertEquals("araxxor", BoardChatLinks.parse("!reval luck araxxor").category);
        for (String invalid : Arrays.asList("hello !reval luck vorkath","!revaluation luck vorkath","!reval help","!reval delete","!reval leaderboard bank")) assertNull(invalid,BoardChatLinks.parse(invalid));
    }

    @Test public void decoratingChatDoesNotOpenAnythingOrOverwriteRecycledMessages() {
        Fixture f = new Fixture(); f.links.startUp();
        assertEquals(0,f.opened.get()); assertTrue(f.text.get().contains("<col=2b7d77>Click to view collection log: Forestry</col>"));
        f.links.rebuild(); assertEquals(1,count(f.text.get(),"Click to view")); assertFalse(f.text.get().contains("<u>"));
        f.receive("An ordinary new message"); f.links.rebuild(); assertEquals("An ordinary new message",f.text.get());
        f.receive("!reval dryboard vorkath"); f.links.rebuild(); f.links.clear();
        assertEquals("!reval dryboard vorkath",f.text.get()); assertEquals(0,f.opened.get());
        verify(f.client,never()).getMenu();
        verify(f.message,never()).setAction(anyInt(),anyString());
    }

    @Test public void nativeChatActionIsTheLeftClickAndCapturesTheSharedPageBeforeWidgetsChange() {
        Fixture f = new Fixture();
        when(f.sender.getText()).thenReturn("[16:14] <img=2>Alice\u00a0Smith: ");
        f.links.startUp();
        Menu menu=mock(Menu.class); when(f.client.getMenu()).thenReturn(menu);
        MenuEntry report=mock(MenuEntry.class),copy=mock(MenuEntry.class),link=mock(MenuEntry.class,RETURNS_SELF);
        when(report.getOption()).thenReturn("Report"); when(report.getType()).thenReturn(MenuAction.CC_OP);
        when(report.getParam1()).thenReturn(InterfaceID.Chatbox.LINE0);
        AtomicReference<MenuEntry[]> entries=new AtomicReference<>(new MenuEntry[]{report});
        AtomicReference<Consumer<MenuEntry>> click=new AtomicReference<>();
        when(menu.getMenuEntries()).thenAnswer(i->entries.get());
        when(menu.createMenuEntry(-1)).thenAnswer(i->{ entries.set(new MenuEntry[]{report,link}); return link; });
        doAnswer(i->{click.set(i.getArgument(0));return link;}).when(link).onClick(any());
        when(link.onClick()).thenAnswer(i->click.get());
        doAnswer(i->{entries.set(i.getArgument(0));return null;}).when(menu).setMenuEntries(any());
        MenuEntryAdded event=new MenuEntryAdded(report);
        f.text.set("An ordinary message"); f.links.menuEntryAdded(event); verify(menu,never()).createMenuEntry(anyInt());
        f.receive("!reval clog forestry"); f.links.rebuild();
        f.links.menuEntryAdded(event); f.links.menuEntryAdded(event);
        verify(menu,times(1)).createMenuEntry(-1); assertEquals(0,f.opened.get());
        verify(link).setForceLeftClick(true); verify(link).setOption("Open Reval board");
        entries.set(new MenuEntry[]{report,link,copy}); f.links.prioritize(); assertSame(link,entries.get()[2]);
        f.text.set("Different row after scroll"); when(f.sender.getText()).thenReturn("SomeoneElse:");
        when(f.row.getName()).thenReturn("SomeoneElse");
        Consumer<MenuEntry> saved=click.get(); saved.accept(link);
        assertEquals(1,f.opened.get()); assertEquals("Alice Smith",f.author.get()); assertEquals("forestry",f.command.get().category);
        // Menu entries are reused every frame: never promote an entry whose callback has changed.
        when(link.onClick()).thenReturn(null); entries.set(new MenuEntry[]{link,report}); f.links.prioritize(); assertSame(report,entries.get()[1]);
        f.links.clear(); saved.accept(link); assertEquals(1,f.opened.get());
        f.links.startUp(); saved.accept(link); assertEquals(1,f.opened.get());
    }

    private static int count(String text,String value) { return (text.length()-text.replace(value,"").length())/value.length(); }
    private static class PublicMenu {
        final Menu menu=mock(Menu.class);
        final MenuEntry cancel=mock(MenuEntry.class),link=mock(MenuEntry.class,RETURNS_SELF);
        final AtomicReference<MenuEntry[]> entries=new AtomicReference<>(new MenuEntry[]{cancel});
        final AtomicReference<Consumer<MenuEntry>> click=new AtomicReference<>();
        PublicMenu(Fixture f) {
            when(f.area.getBounds()).thenReturn(new java.awt.Rectangle(0,180,500,180));
            when(f.message.getBounds()).thenReturn(new java.awt.Rectangle(100,200,300,20));
            when(f.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(140,210));
            when(f.client.getMenu()).thenReturn(menu); when(cancel.getType()).thenReturn(MenuAction.CANCEL);
            when(menu.getMenuEntries()).thenAnswer(i->entries.get());
            when(menu.createMenuEntry(-1)).thenAnswer(i->{entries.set(new MenuEntry[]{cancel,link});return link;});
            doAnswer(i->{click.set(i.getArgument(0));return link;}).when(link).onClick(any());
            when(link.onClick()).thenAnswer(i->click.get());
        }
    }
    private static class Fixture {
        final Client client=mock(Client.class);
        final Widget area=mock(Widget.class),sender=mock(Widget.class),message=mock(Widget.class),row=mock(Widget.class);
        final AtomicReference<String> text=new AtomicReference<>("!reval clog forestry"), source=new AtomicReference<>("!reval clog forestry"),author=new AtomicReference<>();
        final AtomicReference<BrowserCommand> command=new AtomicReference<>();
        final AtomicInteger opened=new AtomicInteger();
        final BoardChatLinks links=new BoardChatLinks(client,(value,name)->{opened.incrementAndGet();command.set(value);author.set(name);});
        void receive(String value) { source.set(value); text.set(value); }
        @SuppressWarnings("unchecked") Fixture() {
            IterableHashTable<MessageNode> messages=mock(IterableHashTable.class);
            MessageNode node=mock(MessageNode.class);
            when(client.getMessages()).thenReturn(messages); when(messages.get(1)).thenReturn(node);
            when(node.getName()).thenAnswer(i->net.runelite.client.util.Text.removeTags(row.getName()));
            when(node.getType()).thenReturn(ChatMessageType.PUBLICCHAT); when(node.getValue()).thenAnswer(i->source.get());
            Object[] stack=new Object[4]; when(client.getObjectStack()).thenReturn(stack); when(client.getObjectStackSize()).thenReturn(4);
            when(client.getIntStack()).thenReturn(new int[]{0,1}); when(client.getIntStackSize()).thenReturn(2);
            doAnswer(i->{
                links.beginBuild(); stack[2]=source.get();
                ScriptCallbackEvent event=new ScriptCallbackEvent();event.setEventName("chatMessageBuilding");links.format(event);
                text.set((String)stack[2]);links.finishBuild();return null;
            }).when(client).refreshChat();
            when(client.getWidget(InterfaceID.Chatbox.SCROLLAREA)).thenReturn(area);
            when(client.getWidget(InterfaceID.Chatbox.LINE0)).thenReturn(row); when(row.getParent()).thenReturn(area);
            when(row.getName()).thenReturn("<col=ffffff><img=2>Alice\u00a0Smith</col>");
            when(area.getId()).thenReturn(InterfaceID.Chatbox.SCROLLAREA);
            when(area.getDynamicChildren()).thenReturn(new Widget[]{sender,message,null,null});
            when(sender.getText()).thenReturn("<img=2>Alice\u00a0Smith: ");
            when(message.getText()).thenAnswer(i->text.get());
            doAnswer(i->{text.set(i.getArgument(0));return message;}).when(message).setText(anyString());
        }
    }
}

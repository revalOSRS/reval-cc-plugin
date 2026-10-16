package com.revalclan.clanbrowser;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.runelite.api.Client;
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
        assertEquals(0,f.opened.get()); assertTrue(f.text.get().contains("<u>!reval clog forestry</u>"));
        f.links.rebuild(); assertEquals(1,count(f.text.get(),"<u>"));
        f.text.set("An ordinary new message"); f.links.rebuild(); assertEquals("An ordinary new message",f.text.get());
        f.text.set("!reval dryboard vorkath"); f.links.rebuild(); f.links.clear();
        assertEquals("!reval dryboard vorkath",f.text.get()); assertEquals(0,f.opened.get());
        verify(f.client,never()).getMenu();
        verify(f.message,never()).setAction(anyInt(),anyString());
    }

    @Test public void nativeChatActionIsTheLeftClickAndCapturesTheSharedPageBeforeWidgetsChange() {
        Fixture f = new Fixture(); f.links.startUp();
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
        f.text.set("!reval clog forestry"); f.links.menuEntryAdded(event); f.links.menuEntryAdded(event);
        verify(menu,times(1)).createMenuEntry(-1); assertEquals(0,f.opened.get());
        verify(link).setForceLeftClick(true); verify(link).setOption("Open Reval board");
        entries.set(new MenuEntry[]{report,link,copy}); f.links.prioritize(); assertSame(link,entries.get()[2]);
        f.text.set("Different row after scroll"); when(f.sender.getText()).thenReturn("SomeoneElse:");
        Consumer<MenuEntry> saved=click.get(); saved.accept(link);
        assertEquals(1,f.opened.get()); assertEquals("Alice Smith",f.author.get()); assertEquals("forestry",f.command.get().category);
        // Menu entries are reused every frame: never promote an entry whose callback has changed.
        when(link.onClick()).thenReturn(null); entries.set(new MenuEntry[]{link,report}); f.links.prioritize(); assertSame(report,entries.get()[1]);
        f.links.clear(); saved.accept(link); assertEquals(1,f.opened.get());
        f.links.startUp(); saved.accept(link); assertEquals(1,f.opened.get());
    }

    private static int count(String text,String value) { return (text.length()-text.replace(value,"").length())/value.length(); }
    private static class Fixture {
        final Client client=mock(Client.class);
        final Widget area=mock(Widget.class),sender=mock(Widget.class),message=mock(Widget.class),row=mock(Widget.class);
        final AtomicReference<String> text=new AtomicReference<>("!reval clog forestry"),author=new AtomicReference<>();
        final AtomicReference<BrowserCommand> command=new AtomicReference<>();
        final AtomicInteger opened=new AtomicInteger();
        final BoardChatLinks links=new BoardChatLinks(client,(value,name)->{opened.incrementAndGet();command.set(value);author.set(name);});
        Fixture() {
            when(client.getWidget(InterfaceID.Chatbox.SCROLLAREA)).thenReturn(area);
            when(client.getWidget(InterfaceID.Chatbox.LINE0)).thenReturn(row); when(row.getParent()).thenReturn(area);
            when(area.getId()).thenReturn(InterfaceID.Chatbox.SCROLLAREA);
            when(area.getDynamicChildren()).thenReturn(new Widget[]{sender,message,null,null});
            when(sender.getText()).thenReturn("<img=2>Alice\u00a0Smith: ");
            when(message.getText()).thenAnswer(i->text.get());
            doAnswer(i->{text.set(i.getArgument(0));return message;}).when(message).setText(anyString());
        }
    }
}

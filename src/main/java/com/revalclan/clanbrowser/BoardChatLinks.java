package com.revalclan.clanbrowser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Local chat decorations only: the typed !reval message travels through normal game chat. */
final class BoardChatLinks {
    private static final String OPEN = "Open Reval board";
    private final Client client;
    private final BiConsumer<BrowserCommand, String> open;
    private final List<Link> links = new ArrayList<>();
    private MenuEntry boardEntry;
    private Consumer<MenuEntry> boardClick;
    private boolean enabled;
    private int generation;

    BoardChatLinks(Client client, BiConsumer<BrowserCommand, String> open) { this.client = client; this.open = open; }

    static BrowserCommand parse(String text) {
        String raw = clean(text);
        if (raw.length() > 150 || !raw.matches("(?is)^!reval\\s+.+")) return null;
        try {
            BrowserCommand command = BrowserCommand.parse(raw.substring(1));
            return "help".equals(command.kind) ? null : command;
        } catch (IllegalArgumentException ignored) { return null; }
    }

    void startUp() { enabled = true; rebuild(); }

    void rebuild() {
        restoreText();
        if (!enabled) return;
        Widget area = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (area == null || area.isHidden()) return;
        Widget[] children = area.getDynamicChildren();
        if (children == null) return;
        // Each visible chat line has sender, message, clan name and rank icon.
        for (int i = 1; i < children.length; i += 4) {
            Widget message = children[i], sender = children[i - 1];
            if (message == null || message.isHidden() || !BrowserCommand.validPlayer(author(sender))) continue;
            if (parse(message.getText()) == null) continue;
            Link link = new Link(message, sender);
            message.setText(link.decorated);
            links.add(link);
        }
    }

    void menuEntryAdded(MenuEntryAdded event) {
        if (!enabled || !"Report".equals(event.getOption()) ||
            event.getType() != MenuAction.CC_OP.getId() && event.getType() != MenuAction.CC_OP_LOW_PRIORITY.getId()) return;
        int component = event.getActionParam1();
        if ((component >>> 16) != InterfaceID.CHATBOX) return;
        Widget row = client.getWidget(component);
        Widget area = row == null ? null : row.getParent();
        if (area == null || area.getId() != InterfaceID.Chatbox.SCROLLAREA) return;
        int index = ((component & 0xffff) - (InterfaceID.Chatbox.LINE0 & 0xffff)) * 4;
        Widget[] children = area.getDynamicChildren();
        if (index < 0 || children == null || index + 1 >= children.length) return;
        Widget message = children[index + 1];
        if (message == null || message.isHidden()) return;
        BrowserCommand command = parse(message.getText());
        String sender = author(children[index]);
        if (command == null || !BrowserCommand.validPlayer(sender)) return;
        Menu menu = client.getMenu();
        for (MenuEntry entry : menu.getMenuEntries()) if (isOurEntry(entry)) return;
        addEntry(menu, command, sender, message);
    }

    private void addEntry(Menu menu, BrowserCommand command, String sender, Widget message) {
        int version = generation;
        // Capture the command and sender now: chat widgets may be recycled while the menu is open.
        boardClick = entry -> { if (enabled && version == generation) open.accept(command, sender); };
        boardEntry = menu.createMenuEntry(-1).setOption(OPEN).setTarget("<col=ffcf63>" + clean(message.getText()) + "</col>")
            .setType(MenuAction.RUNELITE).setForceLeftClick(true)
            .onClick(boardClick);
    }

    void prioritize() {
        if (!enabled || (links.isEmpty() && boardEntry == null)) return;
        Menu menu = client.getMenu();
        if (menu == null) return;
        MenuEntry[] entries = menu.getMenuEntries();
        for (int i = 0; i < entries.length; i++) {
            if (!isOurEntry(entries[i])) continue;
            if (i < entries.length - 1) {
                System.arraycopy(entries, i + 1, entries, i, entries.length - i - 1);
                entries[entries.length - 1] = boardEntry;
                menu.setMenuEntries(entries);
            }
            return;
        }
        // Public/self chat rows need not expose Report. Hit-test the rendered message
        // instead of relying on an unrelated menu option to make the share clickable.
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        Widget area = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (mouse == null || area == null || area.isHidden() || !area.getBounds().contains(mouse.getX(), mouse.getY())) return;
        for (Link link : links) {
            Widget message = link.widget;
            if (message.isHidden() || !message.getBounds().contains(mouse.getX(), mouse.getY())) continue;
            BrowserCommand command = parse(message.getText());
            String sender = author(link.sender);
            if (command != null && BrowserCommand.validPlayer(sender)) addEntry(menu, command, sender, message);
            return;
        }
    }

    void clear() { enabled = false; generation++; boardEntry = null; boardClick = null; restoreText(); }

    private boolean isOurEntry(MenuEntry entry) { return entry == boardEntry && boardClick != null && entry.onClick() == boardClick; }

    private void restoreText() {
        for (Link link : links) if (link.decorated.equals(link.widget.getText())) link.widget.setText(link.text);
        links.clear();
    }

    private static String author(Widget sender) { return clean(sender == null ? null : sender.getText()).replaceFirst(":\\s*$", "").trim(); }
    private static String clean(String value) { return value == null ? "" : Text.removeTags(value).replace('\u00a0', ' ').trim(); }
    private static final class Link {
        final Widget widget, sender;
        final String text, decorated;
        Link(Widget widget, Widget sender) {
            this.widget = widget; this.sender = sender; text = widget.getText();
            decorated = "<col=ffcf63><u>" + clean(text) + "</u></col>";
        }
    }
}

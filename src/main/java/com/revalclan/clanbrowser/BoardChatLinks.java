package com.revalclan.clanbrowser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.VarbitID;
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
    private final List<Link> splitLinks = new ArrayList<>();
    private final List<Shared> building = new ArrayList<>();
    private final List<Shared> splitBuilding = new ArrayList<>();
    private List<BrowserModels.Page> pages = java.util.Collections.emptyList();
    private MenuEntry boardEntry;
    private Consumer<MenuEntry> boardClick;
    private boolean enabled;
    private int generation;

    BoardChatLinks(Client client, BiConsumer<BrowserCommand, String> open) { this.client = client; this.open = open; }

    static BrowserCommand parse(String text) {
        String raw = clean(text);
        if (raw.length() > 150 || !raw.matches("(?is)^!reval(?:\\s+.*)?$")) return null;
        try {
            return BrowserCommand.parse(raw.substring(1));
        } catch (IllegalArgumentException ignored) { return null; }
    }

    // RuneLite's per-message callback belongs to ChatBuilder (84), which also runs
    // for new messages/notifications without the BUILD_CHATBOX wrapper (216).
    static final int CHAT_BUILDER = 84;
    static final int CHAT_SPLIT_BUILDER = 89;

    void startUp() { enabled = true; rebuild(); }
    void catalogue(List<BrowserModels.Page> pages) {
        this.pages = new ArrayList<>(pages);
        if (enabled) rebuild();
    }
    void rebuild() {
        client.refreshChat();
        if (client.getGameState() == GameState.LOGGED_IN) client.runScript(ScriptID.SPLITPM_CHANGED);
    }
    void beginBuild(int scriptId) {
        if (scriptId == CHAT_BUILDER) building.clear();
        else if (scriptId == CHAT_SPLIT_BUILDER) splitBuilding.clear();
    }

    void format(ScriptCallbackEvent event) {
        if (!enabled || !"chatMessageBuilding".equals(event.getEventName())) return;
        int ints = client.getIntStackSize(), objects = client.getObjectStackSize();
        if (ints < 2 || objects < 4) return;
        boolean split = client.getIntStack()[ints - 2] == 1;
        MessageNode node = client.getMessages().get(client.getIntStack()[ints - 1]);
        Shared shared = null;
        if (node != null && playerChat(node.getType()) && BrowserCommand.validPlayer(clean(node.getName()))) {
            String source = node.getValue();
            boolean group = node.getType() == ChatMessageType.CLAN_GIM_CHAT;
            if (group && source != null && source.startsWith("|")) source = source.substring(1);
            BrowserCommand command = parse(source);
            // Outgoing PM nodes name the recipient, but an implicit clog belongs
            // to the logged-in sender on both sides of the conversation.
            String sender = node.getType() == ChatMessageType.PRIVATECHATOUT
                ? clean(client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName()) : clean(node.getName());
            if (command != null && BrowserCommand.validPlayer(sender)) {
                shared = new Shared(command, clean(node.getName()), sender, (split ? "<col=63c5b5>" : color()) + "Click to view " + label(command, sender) + "</col>");
                // Format before the game measures/wraps the row. The source node stays
                // unchanged, so every redraw starts from the same original command.
                client.getObjectStack()[objects - 2] = (group && node.getValue().startsWith("|") ? "|" : "") + shared.decorated;
            }
        }
        // Every built message occupies one row, including ordinary notifications.
        // Keeping those empty positions prevents a notice moving another link's target.
        (split ? splitBuilding : building).add(shared);
    }

    void finishBuild(int scriptId) {
        if (!enabled) return;
        if (scriptId == CHAT_SPLIT_BUILDER) { finishSplitBuild(); return; }
        if (scriptId != CHAT_BUILDER) return;
        Widget area = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (area == null || area.isHidden()) { links.clear(); return; }
        Widget[] children = area.getDynamicChildren();
        if (children == null) { links.clear(); return; }
        // ChatBuilder can return early without rebuilding anything this cycle.
        if (building.isEmpty()) return;
        links.clear();
        int offset = children.length > 1 && children[1] != null && clean(children[1].getText()).startsWith("Public chat filtering:") ? 1 : 0;
        for (int i = 0; i < building.size(); i++) {
            Shared shared = building.get(i);
            int rowIndex = i + offset, messageIndex = rowIndex * 4 + 1;
            if (shared == null || messageIndex >= children.length) continue;
            Widget message = children[messageIndex];
            Widget sender = client.getWidget(InterfaceID.Chatbox.LINE0 + rowIndex);
            if (message == null || message.isHidden() || sender == null) continue;
            Link link = new Link(message, sender, shared);
            if (link.command() != null) links.add(link);
        }
        building.clear();
    }

    private void finishSplitBuild() {
        Widget area = client.getWidget(InterfaceID.PmChat.CONTAINER);
        splitLinks.clear();
        if (area == null || area.isHidden()) { splitBuilding.clear(); return; }
        Widget[] children = area.getDynamicChildren();
        if (children == null) { splitBuilding.clear(); return; }
        // System-update and broadcast notices can precede private messages without
        // a message callback. Match the rendered row instead of its ordinal.
        for (int i = 1; i < children.length; i += 4) {
            Widget message = children[i];
            Widget sender = client.getWidget(InterfaceID.PmChat.PM1 + i / 4);
            if (message == null || message.isHidden() || sender == null) continue;
            for (Shared shared : splitBuilding) {
                if (shared == null) continue;
                Link link = new Link(message, sender, shared);
                if (link.command() != null) { splitLinks.add(link); break; }
            }
        }
        splitBuilding.clear();
    }

    private static boolean playerChat(ChatMessageType type) {
        return type == ChatMessageType.PUBLICCHAT || type == ChatMessageType.MODCHAT || type == ChatMessageType.FRIENDSCHAT ||
            type == ChatMessageType.CLAN_CHAT || type == ChatMessageType.CLAN_GUEST_CHAT || type == ChatMessageType.CLAN_GIM_CHAT ||
            type == ChatMessageType.PRIVATECHAT || type == ChatMessageType.PRIVATECHATOUT || type == ChatMessageType.MODPRIVATECHAT;
    }

    private String color() {
        boolean transparent = client.isResized() && client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;
        return transparent ? "<col=63c5b5>" : "<col=2b7d77>";
    }

    void menuEntryAdded(MenuEntryAdded event) {
        if (!enabled || !"Report".equals(event.getOption()) ||
            event.getType() != MenuAction.CC_OP.getId() && event.getType() != MenuAction.CC_OP_LOW_PRIORITY.getId()) return;
        int component = event.getActionParam1();
        boolean split = (component >>> 16) == InterfaceID.PM_CHAT;
        if (!split && (component >>> 16) != InterfaceID.CHATBOX) return;
        Widget row = client.getWidget(component);
        Widget area = row == null ? null : row.getParent();
        if (area == null || area.getId() != (split ? InterfaceID.PmChat.CONTAINER : InterfaceID.Chatbox.SCROLLAREA)) return;
        int index = ((component & 0xffff) - ((split ? InterfaceID.PmChat.PM1 : InterfaceID.Chatbox.LINE0) & 0xffff)) * 4;
        Widget[] children = area.getDynamicChildren();
        if (index < 0 || children == null || index + 1 >= children.length) return;
        Widget message = children[index + 1];
        if (message == null || message.isHidden()) return;
        Link link = link(message);
        if (link == null) return;
        BrowserCommand command = link.command();
        String sender = link.shared.sender;
        if (command == null || !BrowserCommand.validPlayer(sender)) return;
        Menu menu = client.getMenu();
        for (MenuEntry entry : menu.getMenuEntries()) if (isOurEntry(entry)) return;
        addEntry(menu, command, sender);
    }

    private void addEntry(Menu menu, BrowserCommand command, String sender) {
        int version = generation;
        // Capture the command and sender now: chat widgets may be recycled while the menu is open.
        boardClick = entry -> { if (enabled && version == generation) open.accept(command, sender); };
        // RuneLite opens the chat menu on left-click for this action type.
        boardEntry = menu.createMenuEntry(-1).setOption(OPEN).setTarget(color() + label(command, sender) + "</col>")
            .setType(MenuAction.RUNELITE_LOW_PRIORITY).setForceLeftClick(false)
            .onClick(boardClick);
    }

    void prioritize() {
        if (!enabled || (links.isEmpty() && splitLinks.isEmpty() && boardEntry == null)) return;
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
        if (mouse == null) return;
        if (!addHovered(menu, splitLinks, InterfaceID.PmChat.CONTAINER, mouse))
            addHovered(menu, links, InterfaceID.Chatbox.SCROLLAREA, mouse);
    }

    private boolean addHovered(Menu menu, List<Link> candidates, int container, net.runelite.api.Point mouse) {
        Widget area = client.getWidget(container);
        if (area == null || area.isHidden() || !area.getBounds().contains(mouse.getX(), mouse.getY())) return false;
        for (Link link : candidates) {
            Widget message = link.widget;
            if (message.isHidden() || !message.getBounds().contains(mouse.getX(), mouse.getY())) continue;
            BrowserCommand command = link.command();
            String sender = link.shared.sender;
            if (command != null && BrowserCommand.validPlayer(sender)) {
                addEntry(menu, command, sender);
                return true;
            }
        }
        return false;
    }

    void clear() {
        enabled = false; generation++; boardEntry = null; boardClick = null;
        links.clear(); splitLinks.clear(); building.clear(); splitBuilding.clear(); rebuild();
    }

    private boolean isOurEntry(MenuEntry entry) { return entry == boardEntry && boardClick != null && entry.onClick() == boardClick; }

    private Link link(Widget message) {
        for (Link link : links) if (link.widget == message) return link;
        for (Link link : splitLinks) if (link.widget == message) return link;
        return null;
    }

    private String label(BrowserCommand command, String sender) {
        command = command.resolveShared(pages, sender);
        switch (command.kind) {
            case "help": return "Reval command guide";
            case "clog": return command.player + "'s collection log: " + title(command.category);
            case "luck": return title(command.category) + " luck board";
            case "deserved": return title(command.category) + " deserved board";
            case "dryboard": return title(command.category) + " dryboard";
            default:
                String metric;
                switch (command.metric) {
                    case "clog": metric = "Collection log"; break;
                    case "pets": metric = "Pets"; break;
                    case "points": metric = "Total points"; break;
                    case "greenlogs": metric = "Greenlogs"; break;
                    case "monthly-clog": metric = "Monthly collection log"; break;
                    case "monthly-points": metric = "Monthly points"; break;
                    case "clues": metric = command.tier == null || command.tier.equals("all") ? "All clues" : title(command.tier) + " clues"; break;
                    case "combat": metric = "Combat Achievements"; break;
                    case "bosses":
                        metric = command.boss == null || java.util.Arrays.asList("all", "overall", "all bosses").contains(command.boss.toLowerCase(Locale.ROOT))
                            ? "Boss kills" : title(command.boss) + " kills";
                        break;
                    default: metric = "Total XP";
                }
                return metric + " leaderboard";
        }
    }

    private static String title(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.replace('-', ' ').split("\\s+")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }

    // The static row's operation target is the actual account name. The dynamic
    // sender text is presentation and can include timestamps or other decorations.
    private static String author(Widget sender) { return clean(sender == null ? null : sender.getName()); }
    private static String clean(String value) { return value == null ? "" : Text.removeTags(value).replace('\u00a0', ' ').trim(); }
    private static final class Shared {
        final BrowserCommand command;
        final String account, sender, decorated;
        Shared(BrowserCommand command, String account, String sender, String decorated) {
            this.command = command; this.account = account; this.sender = sender; this.decorated = decorated;
        }
    }
    private static final class Link {
        final Widget widget, sender;
        final Shared shared;
        Link(Widget widget, Widget sender, Shared shared) {
            this.widget = widget; this.sender = sender; this.shared = shared;
        }
        BrowserCommand command() {
            String current = widget.getText();
            // Game chat colors wrap the callback's formatted text. This exact row
            // must still contain our label and the original row's account name
            // (the recipient for outgoing private messages).
            return current != null && current.contains(shared.decorated) && shared.account.equals(author(sender)) ? shared.command : null;
        }
    }
}

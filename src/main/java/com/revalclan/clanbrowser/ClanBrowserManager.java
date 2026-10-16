package com.revalclan.clanbrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PostMenuSort;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/** Owns the command subscription and native window lifecycle, not game data or calculations. */
@Singleton
public class ClanBrowserManager {
    private final Client client;
    private final ClientThread clientThread;
    private final ItemManager items;
    private final BrowserApi api;
    private final BoardChatLinks chatLinks;
    private ClanBrowserWindow window;
    private volatile boolean running;

    @Inject public ClanBrowserManager(Client client, ClientThread clientThread, ItemManager items, BrowserApi api) {
        this.client = client; this.clientThread = clientThread; this.items = items; this.api = api;
        chatLinks = new BoardChatLinks(client, this::openShared);
    }
    public void startUp() { running = true; clientThread.invoke(() -> { if (running) chatLinks.startUp(); }); }
    public void shutDown() {
        running = false;
        clientThread.invoke(chatLinks::clear);
        SwingUtilities.invokeLater(() -> { if (window != null) { window.dispose(); window = null; } });
    }
    @Subscribe public void onCommandExecuted(CommandExecuted event) {
        if (!running || !"reval".equalsIgnoreCase(event.getCommand())) return;
        String command = "reval " + String.join(" ", event.getArguments());
        try { open(BrowserCommand.parse(command)); }
        catch (IllegalArgumentException error) { open(BrowserCommand.parse("reval help")); }
    }
    @Subscribe public void onScriptPostFired(ScriptPostFired event) {
        if (running && event.getScriptId() == ScriptID.BUILD_CHATBOX) chatLinks.rebuild();
    }
    @Subscribe public void onMenuEntryAdded(MenuEntryAdded event) { if (running) chatLinks.menuEntryAdded(event); }
    @Subscribe public void onPostMenuSort(PostMenuSort event) { if (running) chatLinks.prioritize(); }
    public void open(BrowserCommand command) { openShared(command, null); }
    private void openShared(BrowserCommand command, String sender) {
        if (!running) return;
        clientThread.invoke(() -> {
            final long accountHash = client.getGameState() == GameState.LOGGED_IN ? client.getAccountHash() : 0;
            final String name = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
            SwingUtilities.invokeLater(() -> {
                if (!running) return;
                if (window == null || !window.isDisplayable()) window = new ClanBrowserWindow(SwingUtilities.getWindowAncestor(client.getCanvas()), api, id -> items.getImage(id));
                window.identity(accountHash, name); window.open(command, sender);
            });
        });
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        if (!running || event.getGameState() != GameState.LOGIN_SCREEN && event.getGameState() != GameState.LOGGED_IN) return;
        clientThread.invoke(() -> {
            long hash = client.getGameState() == GameState.LOGGED_IN ? client.getAccountHash() : 0;
            String name = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
            SwingUtilities.invokeLater(() -> { if (window != null && window.isDisplayable()) window.identity(hash, name); });
        });
    }
}

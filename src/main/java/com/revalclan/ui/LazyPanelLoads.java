package com.revalclan.ui;

import java.util.HashSet;
import java.util.Set;

/** Tracks which optional panels have been requested in the current login. EDT only. */
final class LazyPanelLoads {
    private final Set<String> loaded = new HashSet<>();
    private boolean loggedIn;

    void onLogin() {
        loaded.clear();
        loggedIn = true;
    }

    void onLogout() {
        loaded.clear();
        loggedIn = false;
    }

    void open(String panel, boolean requiresLogin, Runnable load) {
        if ((requiresLogin && !loggedIn) || !loaded.add(panel)) return;
        try {
            load.run();
        } catch (RuntimeException error) {
            loaded.remove(panel);
            throw error;
        }
    }
}

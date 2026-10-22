package com.revalclan.clanbrowser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import okhttp3.Call;

/** Independent window: the game can regain focus and stack above it while it stays open. */
public final class ClanBrowserWindow extends JFrame {
    private final BrowserApi api;
    private final IntFunction<BufferedImage> icons;
    private final JPanel body = panel(new BorderLayout());
    private final Title title = new Title();
    private final SearchPicker selection = new SearchPicker("Category");
    private final SearchPicker boss = new SearchPicker("Boss");
    private final JPanel choices = panel(new BorderLayout(8, 0));
    private final JPanel statistics = panel(new BorderLayout(0, 8));
    private final JPanel extra = panel(new BorderLayout());
    private final JPanel tools = panel(new BorderLayout(8, 0));
    private final JComboBox<String> tiers = new JComboBox<>(new String[]{"All tiers", "Beginner", "Easy", "Medium", "Hard", "Elite", "Master"});
    private final JTextField search = new JTextField();
    private final JButton findMe = BrowserTheme.button("Find me");
    private final JProgressBar loading = progressBar(10);
    private final Map<String, JButton> modeButtons = new LinkedHashMap<>();
    private final Map<String, JButton> metricButtons = new LinkedHashMap<>();
    private final Map<String, String> lastLogPages = new LinkedHashMap<>();
    private final Map<String, JButton> logTabs = new LinkedHashMap<>();
    private final Map<String, JButton> logPages = new LinkedHashMap<>();
    private final java.util.Set<String> completedLogPages = new java.util.HashSet<>();
    private String completionAccount, completionGeneratedAt;
    private JPanel logContent, logSidebar, logDetail;
    private Strip logStrip;
    private String logCategory, logAccount, logMenuMode;
    private BrowserModels.Catalogue catalogue;
    private BrowserCommand request;
    private ViewRequest activeRead;
    private BrowserModels.View data;
    private LeaderboardView standings;
    private Call dataCall, catalogueCall;
    private long accountHash;
    private String viewer;
    private String kind = "clog", lastCategory = "vorkath", lastMetric = "clog";
    private boolean adjusting, closed;
    private int generation;

    private static final class ViewRequest {
        final BrowserCommand command;
        final long accountHash;
        ViewRequest(BrowserCommand command, long accountHash) { this.command = command; this.accountHash = accountHash; }
        boolean usesViewer() { return command.kind.equals("clog") && command.player == null; }
        String accountKey() { return command.player == null ? "account:" + accountHash : "player:" + command.player.toLowerCase(Locale.ROOT); }
    }

    public ClanBrowserWindow(Window anchor, BrowserApi api, IntFunction<BufferedImage> icons) {
        super("Reval · Clan browser");
        // Retain component buffering without the separate native window's stale swap buffer.
        getRootPane().setDoubleBuffered(false);
        this.api = api; this.icons = icons;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(1180, 820);
        setLocationRelativeTo(anchor);
        java.awt.GraphicsConfiguration screen = anchor != null ? anchor.getGraphicsConfiguration() : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        java.awt.Rectangle available = new java.awt.Rectangle(screen.getBounds());
        Insets screenInsets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(screen);
        available.x += screenInsets.left; available.y += screenInsets.top; available.width -= screenInsets.left + screenInsets.right; available.height -= screenInsets.top + screenInsets.bottom;
        setMinimumSize(new Dimension(Math.min(960, available.width - 24), Math.min(620, available.height - 24)));
        setSize(Math.min(1180, available.width - 24), Math.min(820, available.height - 24));
        setLocation(Math.max(available.x + 12, Math.min(getX(), available.x + available.width - getWidth() - 12)), Math.max(available.y + 12, Math.min(getY(), available.y + available.height - getHeight() - 12)));
        JPanel frame = panel(new BorderLayout(0, 10));
        frame.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BrowserTheme.FRAME, 7),
            BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BrowserTheme.BORDER, 2), BorderFactory.createEmptyBorder(10, 12, 12, 12))));
        setContentPane(frame);
        JPanel top = panel(new BorderLayout(0, 9)); top.add(title, BorderLayout.NORTH);
        JPanel controls = panel(new BorderLayout());
        controls.setLayout(new javax.swing.BoxLayout(controls, javax.swing.BoxLayout.Y_AXIS));
        JPanel modes = panel(new GridLayout(1, 5, 7, 0));
        String[][] names = {{"clog", "Collection log"}, {"luck", "Luck"}, {"deserved", "Deserved"}, {"dryboard", "Dryboard"}, {"leaderboard", "Leaderboards"}};
        for (String[] name : names) {
            JButton button = BrowserTheme.button(name[1]); button.addActionListener(event -> chooseMode(name[0]));
            modes.add(button); modeButtons.put(name[0], button);
        }
        controls.add(modes); controls.add(javax.swing.Box.createVerticalStrut(9));
        choices.setBorder(BorderFactory.createEmptyBorder(0, 0, 9, 0));
        choices.add(selection, BorderLayout.CENTER);
        selection.onSubmit(value -> { if (!adjusting) { configureExtra(); submitSelection(); } });
        boss.onSubmit(value -> { if (!adjusting) submitSelection(); });
        tiers.addActionListener(event -> { if (!adjusting) submitSelection(); });
        tiers.setFont(BrowserTheme.font(20, false)); tiers.setForeground(BrowserTheme.TEXT); tiers.setBackground(BrowserTheme.HEADER); tiers.setFocusable(false);
        controls.add(choices);
        BrowserTheme.field(search); search.getAccessibleContext().setAccessibleName("Find a clan member");
        tools.add(search, BorderLayout.CENTER);
        JPanel toolsRight = panel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        toolsRight.add(findMe);
        JButton refresh = BrowserTheme.button("Refresh"); refresh.setToolTipText("Reload this view from the server"); refresh.addActionListener(event -> submitSelection(true)); toolsRight.add(refresh);
        JButton help = BrowserTheme.button("?"); help.setToolTipText("In-game command help"); help.addActionListener(event -> showHelp()); toolsRight.add(help);
        tools.add(toolsRight, BorderLayout.EAST); controls.add(tools);
        JPanel metrics = panel(new GridLayout(0, 5, 7, 7));
        metrics.getAccessibleContext().setAccessibleName("Leaderboard statistics");
        String[][] stats = {{"clog", "Collection log"}, {"pets", "Pets"}, {"points", "Points"}, {"greenlogs", "Greenlogs"}, {"xp", "Total XP"},
            {"monthly-clog", "Monthly clog"}, {"monthly-points", "Monthly points"}, {"clues", "Clues"}, {"combat", "Combat points"}, {"bosses", "Boss kills"}};
        for (String[] stat : stats) {
            JButton button = BrowserTheme.button(stat[1]); button.addActionListener(event -> chooseMetric(stat[0]));
            metrics.add(button); metricButtons.put(stat[0], button);
        }
        statistics.setBorder(BorderFactory.createEmptyBorder(9, 0, 0, 0));
        statistics.add(metrics, BorderLayout.NORTH); statistics.add(extra, BorderLayout.CENTER);
        statistics.setVisible(false); controls.add(statistics);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { filter(); }
            public void removeUpdate(DocumentEvent event) { filter(); }
            public void changedUpdate(DocumentEvent event) { filter(); }
        });
        search.addActionListener(event -> { if (clog()) submitSelection(); });
        findMe.addActionListener(event -> {
            search.setText("");
            if (clog()) { configure(request.withPlayer(null)); perform(); }
            else if (standings != null) standings.findViewer();
        });
        loading.setVisible(false);
        top.add(controls, BorderLayout.CENTER); top.add(loading, BorderLayout.SOUTH);
        frame.add(top, BorderLayout.NORTH); frame.add(body, BorderLayout.CENTER);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close-browser");
        getRootPane().getActionMap().put("close-browser", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent event) { dispose(); } });
        addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent event) { closed = true; generation++; cancel(); } });
    }

    public void identity(long accountHash, String viewer) {
        boolean changed = this.accountHash != accountHash;
        this.accountHash = accountHash; this.viewer = viewer;
        findMe.setEnabled(viewer != null); if (standings != null) standings.viewer(viewer);
        if (changed && activeRead != null && activeRead.usesViewer()) {
            generation++; cancel(); activeRead = null; data = null; standings = null;
            if (!closed) perform();
        }
    }
    public void open(BrowserCommand command) { open(command, null); }
    public void open(BrowserCommand command, String sharedBy) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Browser window must open on the EDT");
        closed = false; search.setText("");
        setExtendedState(getExtendedState() & ~ICONIFIED); setVisible(true); toFront();
        if (command.kind.equals("help")) { showHelp(); return; }
        cancel();
        if (catalogue != null) { configure(command.resolveShared(catalogue.pages, sharedBy)); perform(); return; }
        request = command; activeRead = null; data = null; busy(true); message("Loading clan categories…", null);
        final int ticket = ++generation;
        catalogueCall = api.catalogue(result -> {
            if (closed || ticket != generation) return;
            catalogue = result;
            configure(command.resolveShared(catalogue.pages, sharedBy)); perform();
        }, error -> { if (!closed && ticket == generation) { busy(false); message(error, () -> open(command, sharedBy)); } });
    }

    private void configure(BrowserCommand command) {
        command = command.resolve(catalogue);
        adjusting = true; request = command; kind = command.kind; tools.setVisible(!kind.equals("help"));
        if (command.category != null) lastCategory = command.category;
        if (command.metric != null) lastMetric = command.metric;
        modeButtons.forEach((mode, button) -> highlight(button, mode.equals(kind)));
        List<BrowserModels.Page> pages = new ArrayList<>();
        if (catalogue != null) for (BrowserModels.Page page : catalogue.pages) if (!kind.equals("dryboard") || page.category.equals("Bosses") || page.category.equals("Raids")) pages.add(page);
        choices.setVisible(kind.equals("dryboard")); statistics.setVisible(kind.equals("leaderboard"));
        selection.choices(pages);
        selection.placeholder("Search a category, or type cox / tob / toa…");
        selection.value(lastCategory);
        metricButtons.forEach((metric, button) -> highlight(button, metric.equals(lastMetric)));
        boss.choices(catalogue.bosses); boss.placeholder("All bosses, or search a boss…"); boss.value(command.boss == null ? "all" : command.boss);
        tiers.setSelectedItem(command.tier == null || command.tier.equals("all") ? "All tiers" : BrowserTheme.capitalize(command.tier));
        if (clog()) search.setText(command.player == null ? "" : command.player);
        search.putClientProperty("JTextField.placeholderText", "Find a clan member…");
        search.setToolTipText(clog() ? "Enter an OSRS name and press Enter to view their collection log" : "Filter the displayed clan standings");
        configureExtra(); adjusting = false;
    }

    private void configureExtra() {
        extra.removeAll(); String metric = lastMetric;
        boolean show = kind.equals("leaderboard") && (metric.equals("bosses") || metric.equals("clues"));
        if (show) {
            JPanel filter = panel(new BorderLayout(8, 0)); filter.setPreferredSize(new Dimension(420, 36));
            filter.add(label(metric.equals("bosses") ? "Boss" : "Clue tier", 20, BrowserTheme.MUTED), BorderLayout.WEST);
            filter.add(metric.equals("bosses") ? boss : tiers, BorderLayout.CENTER); extra.add(filter, BorderLayout.WEST);
        }
        extra.setVisible(show); extra.revalidate();
    }
    private void chooseMetric(String metric) {
        if (catalogue == null) return;
        configure(new BrowserCommand("leaderboard", null, metric, null, null, null)); perform();
    }
    private void chooseMode(String mode) {
        // An explicit Collection log tab click always returns to the logged-in account.
        // Category buttons and commands still preserve a deliberately selected clan member.
        String category = catalogue == null ? lastCategory : categoryForMode(mode, lastCategory, catalogue.pages);
        open(new BrowserCommand(mode, category, lastMetric, null, null, null));
    }
    static String categoryForMode(String mode, String category, List<BrowserModels.Page> pages) {
        if (mode.equals("leaderboard") || mode.equals("clog")) return category;
        String needle = BrowserCommand.normalize(category);
        for (BrowserModels.Page page : pages) {
            if (mode.equals("dryboard") && !page.category.equals("Bosses") && !page.category.equals("Raids")) continue;
            if (named(page, needle)) return page.value;
        }
        return needle.equals("clues") && !mode.equals("dryboard") ? "hard-clues" : "vorkath";
    }
    private void submitSelection() { submitSelection(false); }
    private void submitSelection(boolean refresh) {
        if (catalogue == null || adjusting) return;
        String metric = kind.equals("leaderboard") ? lastMetric : null;
        request = new BrowserCommand(kind, kind.equals("leaderboard") ? null : categoryBrowser() ? lastCategory : selection.value(), metric,
            "bosses".equals(metric) ? boss.value() : null,
            "clues".equals(metric) ? "All tiers".equals(tiers.getSelectedItem()) ? "all" : tiers.getSelectedItem().toString().toLowerCase(Locale.ROOT) : null,
            clog() ? search.getText() : null);
        if (request.category != null) lastCategory = request.category;
        if (request.metric != null) lastMetric = request.metric;
        configureExtra(); perform(refresh);
    }
    private void perform() { perform(false); }
    private void perform(boolean refresh) {
        if (request == null) return;
        if (request.kind.equals("help")) { showHelp(); return; }
        request = request.resolve(catalogue);
        if (dataCall != null) dataCall.cancel();
        final ViewRequest read = new ViewRequest(request, accountHash);
        activeRead = read;
        final int ticket = ++generation;
        dataCall = api.view(read.command, read.accountHash, refresh, result -> {
            if (!accepts(read, ticket)) return;
            data = result; busy(false); render();
        }, error -> {
            if (!accepts(read, ticket)) return;
            busy(false);
            if (categoryBrowser()) collectionMessage(error, () -> submitSelection(refresh));
            else message(error, () -> submitSelection(refresh));
        });
        // Cache hits have already rendered in this EDT event. Never clear them for a loading frame.
        if (dataCall != null) {
            data = null; standings = null;
            if (categoryBrowser()) { busy(false); loadingDetails(); }
            else {
                busy(true); title.text = titleFor(request.kind); title.repaint();
                message("Loading clan standings…", null);
            }
        }
    }

    private boolean accepts(ViewRequest read, int ticket) {
        return !closed && ticket == generation && activeRead == read && (!read.usesViewer() || read.accountHash == accountHash);
    }

    private void render() {
        standings = null;
        boolean clog = "clog".equals(data.kind);
        if (clog && data.account != null) {
            title.text = "Collection Log  (" + BrowserTheme.number(data.account.obtained) + " / " + BrowserTheme.number(data.account.total) + ")";
            if (data.page.value != null) { lastCategory = data.page.value; lastLogPages.put(data.page.category, data.page.value); }
            if (request.player != null) { search.setText(data.account.name); request = request.withPlayer(data.account.name); }
            collectionLayout(); rememberCompletions(); collectionNavigation(data.page);
            logAccount = logAccountKey();
            logStrip.update(data.account.name, "Last synced: " + BrowserTheme.timestamp(data.account.lastSyncedAt));
            logDetail.removeAll(); logDetail.add(new LogSummary(data), BorderLayout.NORTH);
            logDetail.add(BrowserTheme.scroll(new CollectionGrid(data.groups, icons)), BorderLayout.CENTER);
            setTitle("Reval · " + data.account.name + " · " + data.page.label);
        } else {
            JPanel destination = body;
            if (categoryBrowser()) { collectionLayout(); collectionNavigation(data.page); logDetail.removeAll(); destination = logDetail; }
            else body.removeAll();
            String page = data.page != null ? data.page.label : null;
            title.text = java.util.Arrays.asList("dryboard", "deserved", "luck").contains(data.kind) ? titleFor(data.kind) + " · " + page : data.title;
            String left = "dryboard".equals(data.kind) ? "Kills since last recorded unique" : page != null ? page : "Full clan standings";
            if ("luck".equals(data.kind) || "deserved".equals(data.kind)) {
                boolean chasingOnly = !data.rows.isEmpty() && data.rows.stream().allMatch(row -> "progress".equals(row.status));
                left = chasingOnly ? "Closest to greenlog · Most slots first" : data.metric != null && data.metric.isJsonObject()
                    ? ("deserved".equals(data.kind) ? "Highest" : "Lowest") + " completion " + LeaderboardView.metricLabel(data) + " first" : "Completed logs first";
            }
            String right = data.completedTotal != null ? BrowserTheme.number(data.completedTotal) + " greenlogged" : data.subtitle != null ? data.subtitle : "Updated: " + BrowserTheme.timestamp(data.generatedAt);
            destination.add(new Strip(left, right), BorderLayout.NORTH);
            standings = new LeaderboardView(data, viewer, data.page == null ? null : name -> {
                configure(new BrowserCommand("clog", data.page.value != null ? data.page.value : data.page.subcategory, null, null, null, name)); perform();
            });
            destination.add(standings, BorderLayout.CENTER);
            setTitle("Reval · " + title.text);
        }
        title.repaint(); filter(); body.revalidate(); body.repaint();
    }

    private void collectionLayout() {
        if (logContent == null) {
            logContent = panel(new BorderLayout(0, 9));
            JPanel above = panel(new BorderLayout(0, 8)); logStrip = new Strip("", "");
            above.add(logStrip, BorderLayout.NORTH);
            JPanel tabs = panel(new GridLayout(1, 5, 7, 0));
            for (String tab : new String[]{"Bosses", "Raids", "Clues", "Minigames", "Other"}) {
                JButton button = BrowserTheme.button(tab); button.addActionListener(event -> selectTab(tab));
                logTabs.put(tab, button); tabs.add(button);
            }
            above.add(tabs, BorderLayout.CENTER); logContent.add(above, BorderLayout.NORTH);
            JPanel pages = panel(new BorderLayout(9, 0));
            logSidebar = panel(new BorderLayout()); logDetail = panel(new BorderLayout(0, 6));
            pages.add(logSidebar, BorderLayout.WEST); pages.add(logDetail, BorderLayout.CENTER);
            logContent.add(pages, BorderLayout.CENTER);
        }
        logStrip.setVisible(clog());
        if (logContent.getParent() != body) { body.removeAll(); body.add(logContent, BorderLayout.CENTER); }
    }

    private void collectionNavigation(BrowserModels.Page page) {
        if (page == null) return;
        String menuMode = clog() ? "clog" : "board";
        if (!page.category.equals(logCategory) || !menuMode.equals(logMenuMode)) {
            logCategory = page.category; logMenuMode = menuMode; logPages.clear(); logSidebar.removeAll();
            logSidebar.add(categoryList(page.category, page.value), BorderLayout.CENTER);
        }
        if (page.value != null) lastLogPages.put(page.category, page.value);
        logTabs.forEach((category, button) -> highlight(button, category.equals(page.category)));
        logPages.forEach((value, button) -> {
            BrowserModels.Page entry = value.equals("clues") ? null : catalogue.pages.stream().filter(item -> value.equals(item.value)).findFirst().orElse(null);
            boolean complete = clog() && (entry == null ? value.equals("clues") && allCluesComplete() : completedLogPages.contains(completionKey(entry.category, entry.subcategory)));
            BrowserTheme.collectionCategory(button, value.equals(page.value), complete);
        });
    }

    private static String completionKey(String category, String subcategory) { return category + "\0" + subcategory; }
    private void useCompletionAccount() {
        String key = logAccountKey();
        if (!key.equals(completionAccount)) { completedLogPages.clear(); completionGeneratedAt = null; completionAccount = key; }
    }
    private void rememberCompletions() {
        useCompletionAccount();
        if (completionGeneratedAt != null && data.generatedAt != null && data.generatedAt.compareTo(completionGeneratedAt) < 0) return;
        if (data.completedPages != null) {
            completedLogPages.clear();
            for (BrowserModels.Page page : data.completedPages) completedLogPages.add(completionKey(page.category, page.subcategory));
        } else {
            for (BrowserModels.Group group : data.groups) {
                String key = completionKey(group.category, group.subcategory);
                if (group.complete) completedLogPages.add(key); else completedLogPages.remove(key);
            }
        }
        completionGeneratedAt = data.generatedAt;
    }
    private boolean allCluesComplete() {
        List<BrowserModels.Page> clues = new ArrayList<>();
        for (BrowserModels.Page page : catalogue.pages) if ("Clues".equals(page.category)) clues.add(page);
        return !clues.isEmpty() && clues.stream().allMatch(page -> completedLogPages.contains(completionKey(page.category, page.subcategory)));
    }

    private BrowserModels.Page selectedLogPage() {
        String needle = BrowserCommand.normalize(request.category);
        if (clog() && java.util.Arrays.asList("clues", "all clues", "all clue tiers").contains(needle)) {
            BrowserModels.Page page = new BrowserModels.Page(); page.value = "clues"; page.label = "All clue tiers"; page.category = "Clues"; return page;
        }
        for (BrowserModels.Page page : catalogue.pages) if (named(page, needle)) return page;
        return null;
    }
    /** True when the page's value, label or one of its aliases normalizes to the needle. */
    private static boolean named(BrowserModels.Page page, String needle) {
        return BrowserCommand.normalize(page.value).equals(needle) || BrowserCommand.normalize(page.label).equals(needle)
            || page.aliases.stream().anyMatch(alias -> BrowserCommand.normalize(alias).equals(needle));
    }

    private String logAccountKey() { return (activeRead != null ? activeRead : new ViewRequest(request, accountHash)).accountKey(); }

    /** The Collection log mode, as opposed to the luck, deserved, dryboard and leaderboard boards. */
    private boolean clog() { return kind.equals("clog"); }
    private boolean categoryBrowser() { return clog() || kind.equals("luck") || kind.equals("deserved"); }

    private void loadingDetails() {
        collectionLayout(); if (clog()) useCompletionAccount();
        BrowserModels.Page page = selectedLogPage(); collectionNavigation(page);
        String account = request.player != null ? request.player : viewer != null ? viewer : "Your account";
        if (clog() && !logAccountKey().equals(logAccount)) {
            logStrip.update(account, "");
            title.text = "Collection Log";
        }
        String name = page == null ? request.category : page.label;
        JPanel summary = panel(new BorderLayout()); summary.setPreferredSize(new Dimension(100, 88));
        JLabel category = label(name, 29, BrowserTheme.ORANGE); category.setHorizontalAlignment(SwingConstants.CENTER);
        category.setPreferredSize(new Dimension(100, 38)); summary.add(category, BorderLayout.NORTH);
        logDetail.removeAll(); logDetail.add(summary, BorderLayout.NORTH);
        JPanel pending = messagePanel(clog() ? "Loading items…" : "Loading standings…", null);
        pending.add(progressBar(100), BorderLayout.SOUTH);
        logDetail.add(pending, BorderLayout.CENTER);
        if (!clog()) title.text = titleFor(request.kind) + " · " + name;
        setTitle("Reval · " + (clog() ? account : titleFor(request.kind)) + " · " + name);
        title.repaint(); body.revalidate(); body.repaint();
    }

    private void collectionMessage(String text, Runnable retry) {
        collectionLayout(); standings = null;
        java.awt.Component previous = ((BorderLayout) logDetail.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if (previous != null) logDetail.remove(previous);
        logDetail.add(messagePanel(text, retry), BorderLayout.CENTER); body.revalidate(); body.repaint();
    }

    private static void highlight(JButton button, boolean selected) {
        button.setBackground(selected ? new Color(0x6b5a3d) : BrowserTheme.HEADER);
        button.setForeground(selected ? BrowserTheme.GOLD : BrowserTheme.TEXT);
    }

    private javax.swing.JScrollPane categoryList(String category, String selected) {
        JPanel list = panel(null); list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        List<BrowserModels.Page> pages = new ArrayList<>();
        if (clog() && category.equals("Clues")) { BrowserModels.Page all = new BrowserModels.Page(); all.value = "clues"; all.label = "All clue tiers"; pages.add(all); }
        for (BrowserModels.Page page : catalogue.pages) if (category.equals(page.category)) pages.add(page);
        JButton active = null;
        for (BrowserModels.Page page : pages) {
            JButton button = BrowserTheme.button(page.label); button.setFont(BrowserTheme.font(18, true)); button.setToolTipText(page.label);
            button.setAlignmentX(LEFT_ALIGNMENT); button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            button.setPreferredSize(new Dimension(206, 36));
            boolean chosen = page.value.equals(selected);
            highlight(button, chosen);
            logPages.put(page.value, button);
            button.addActionListener(event -> selectPage(page.value)); list.add(button); list.add(javax.swing.Box.createVerticalStrut(5));
            if (chosen) active = button;
        }
        javax.swing.JScrollPane scroll = BrowserTheme.scroll(list); scroll.setPreferredSize(new Dimension(230, 100));
        scroll.getAccessibleContext().setAccessibleName(category + " collection categories");
        JButton chosen = active;
        if (chosen != null) SwingUtilities.invokeLater(() -> { if (chosen.isShowing()) list.scrollRectToVisible(chosen.getBounds()); });
        return scroll;
    }
    private void selectPage(String value) {
        configure(new BrowserCommand(categoryBrowser() ? kind : "clog", value, null, null, null, clog() ? request.player : null)); perform();
    }
    private void selectTab(String category) {
        String remembered = lastLogPages.get(category);
        if (remembered != null && (clog() || !remembered.equals("clues"))) { selectPage(remembered); return; }
        if (clog() && category.equals("Clues")) { selectPage("clues"); return; }
        for (BrowserModels.Page page : catalogue.pages) if (category.equals(page.category)) { selectPage(page.value); return; }
    }
    private void filter() { if (!clog() && standings != null) standings.filter(search.getText()); }
    private void busy(boolean value) { loading.setVisible(value); }
    private void message(String text, Runnable retry) {
        standings = null; body.removeAll();
        body.add(messagePanel(text, retry), BorderLayout.CENTER); body.revalidate(); body.repaint();
    }
    private JPanel messagePanel(String text, Runnable retry) {
        JPanel box = panel(new GridBagLayout()); JPanel inner = panel(new BorderLayout(0, 16));
        JLabel label = label(BrowserTheme.plainText(text), 23, BrowserTheme.TEXT); label.setHorizontalAlignment(SwingConstants.CENTER); inner.add(label, BorderLayout.CENTER);
        if (retry != null) { JButton button = BrowserTheme.button("Try again"); button.addActionListener(event -> retry.run()); inner.add(button, BorderLayout.SOUTH); }
        box.add(inner);
        JPanel content = panel(new BorderLayout()); content.add(box, BorderLayout.CENTER); return content;
    }
    private void showHelp() {
        cancel(); generation++; activeRead = null; request = BrowserCommand.parse("reval help"); kind = "help";
        data = null; busy(false); standings = null;
        title.text = "Reval · Command guide"; title.repaint(); setTitle("Reval · Command guide"); body.removeAll();
        tools.setVisible(false); choices.setVisible(false); statistics.setVisible(false);
        modeButtons.values().forEach(button -> highlight(button, false));
        javax.swing.JScrollPane guide = BrowserTheme.scroll(new BrowserHelpView()); guide.setFocusable(true);
        body.add(guide, BorderLayout.CENTER); body.revalidate(); body.repaint();
        SwingUtilities.invokeLater(() -> {
            if (!guide.isShowing()) return;
            guide.requestFocusInWindow(); guide.getViewport().setViewPosition(new java.awt.Point(0, 0));
        });
    }
    private void cancel() {
        if (dataCall != null) { dataCall.cancel(); dataCall = null; }
        if (catalogueCall != null) { catalogueCall.cancel(); catalogueCall = null; }
    }
    BrowserModels.View currentView() { return loading.isVisible() ? null : data; }
    private static String titleFor(String kind) {
        switch (kind) {
            case "clog": return "Collection Log";
            case "deserved": return "Deserved Greenlogs";
            case "dryboard": return "Dryboard";
            case "luck": return "Greenlog Luck";
            default: return "Clan Leaderboards";
        }
    }
    private static JProgressBar progressBar(int width) {
        JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true); bar.setForeground(BrowserTheme.GOLD); bar.setBackground(BrowserTheme.PANEL);
        bar.setPreferredSize(new Dimension(width, 3)); return bar;
    }
    private static JPanel panel(java.awt.LayoutManager layout) { JPanel panel = new JPanel(layout); panel.setBackground(BrowserTheme.BACKGROUND); return panel; }
    private static JLabel label(String text, int size, Color color) { JLabel label = new JLabel(text); label.setFont(BrowserTheme.font(size, false)); label.setForeground(color); return label; }

    private static final class Title extends JComponent {
        String text = "Reval · Clan browser";
        Title() { setPreferredSize(new Dimension(100, 66)); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = BrowserTheme.pixel(graphics); g.setColor(BrowserTheme.HEADER); g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(BrowserTheme.DARK_BORDER); g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            BrowserTheme.centered(g, text == null ? "Clan standings" : text, getWidth() / 2, 0, getHeight(), getWidth() - 20, 36, BrowserTheme.ORANGE, true); g.dispose();
        }
    }
    private static final class Strip extends JComponent {
        private String left, right;
        Strip(String left, String right) { this.left = left; this.right = right; setPreferredSize(new Dimension(100, 42)); }
        void update(String left, String right) { this.left = left; this.right = right; repaint(); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = BrowserTheme.pixel(graphics);
            BrowserTheme.text(g, left, 2, 0, getHeight(), getWidth() / 2 - 12, 25, BrowserTheme.TEXT, true, false);
            BrowserTheme.text(g, right, getWidth() - 2, 0, getHeight(), getWidth() / 2 - 12, 20, BrowserTheme.MUTED, false, true); g.dispose();
        }
    }
    private static final class LogSummary extends JComponent {
        private final BrowserModels.View view;
        LogSummary(BrowserModels.View view) { this.view = view; setPreferredSize(new Dimension(100, view.groups.size() == 1 && view.groups.get(0).rank != null ? 112 : 88)); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = BrowserTheme.pixel(graphics); int width = getWidth();
            boolean complete = !view.groups.isEmpty() && view.groups.stream().allMatch(group -> group.complete);
            String title = view.page.label;
            BrowserTheme.centered(g, title, width / 2, 0, 38, width - (complete ? 340 : 24), 29, complete ? BrowserTheme.GREEN : BrowserTheme.ORANGE, true);
            if (complete) BrowserTheme.text(g, "GREENLOGGED", width - 10, 0, 38, 150, 22, BrowserTheme.GREEN, true, true);
            long obtained = view.groups.stream().flatMap(group -> group.items.stream()).filter(item -> item.quantity != null && item.quantity > 0).count();
            long total = view.groups.stream().mapToLong(group -> group.items.size()).sum();
            BrowserTheme.text(g, (view.groups.size() > 1 ? "Entries obtained: " : "Items obtained: ") + BrowserTheme.number(obtained) + " / " + BrowserTheme.number(total), 12, 38, 34, width / 2 - 24, 22, complete ? BrowserTheme.GREEN : BrowserTheme.YELLOW, false, false);
            BrowserModels.Metric metric = view.groups.size() == 1 ? view.groups.get(0).metric : view.overviewMetric;
            if (metric != null) BrowserTheme.text(g, BrowserTheme.counter(metric), width - 12, 38, 34, width / 2 - 24, 22, BrowserTheme.TEXT, false, true);
            if (view.groups.size() == 1 && view.groups.get(0).rank != null) {
                BrowserTheme.text(g, BrowserTheme.luckRank(view.groups.get(0).rank), 12, 72, 30, width - 24, 22, BrowserTheme.GOLD, true, false);
            }
            g.dispose();
        }
    }
}

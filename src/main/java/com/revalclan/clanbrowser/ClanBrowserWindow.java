package com.revalclan.clanbrowser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
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
import javax.swing.JDialog;
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

/** One owned, modeless window. Game ticks/network work never run on the Swing event thread. */
public final class ClanBrowserWindow extends JDialog {
    private final BrowserApi api;
    private final IntFunction<BufferedImage> icons;
    private final JPanel body = panel(new BorderLayout());
    private final Title title = new Title();
    private final SearchPicker selection = new SearchPicker("Category");
    private final SearchPicker boss = new SearchPicker("Boss");
    private final JTextField player = new JTextField(12);
    private final JPanel playerBox = panel(new BorderLayout(6, 0));
    private final JPanel choices = panel(new BorderLayout(8, 0));
    private final JPanel statistics = panel(new BorderLayout(0, 8));
    private final JPanel extra = panel(new BorderLayout());
    private final JComboBox<String> tiers = new JComboBox<>(new String[]{"All tiers", "Beginner", "Easy", "Medium", "Hard", "Elite", "Master"});
    private final JTextField search = new JTextField();
    private final JComboBox<String> itemFilter = new JComboBox<>(new String[]{"All items", "Obtained", "Missing"});
    private final JButton findMe = BrowserTheme.button("Find me");
    private final JProgressBar loading = new JProgressBar();
    private final Map<String, JButton> modeButtons = new LinkedHashMap<>();
    private final Map<String, JButton> metricButtons = new LinkedHashMap<>();
    private final Map<String, String> lastLogPages = new LinkedHashMap<>();
    private final Map<String, JButton> logTabs = new LinkedHashMap<>();
    private final Map<String, JButton> logPages = new LinkedHashMap<>();
    private JPanel logContent, logSidebar, logDetail;
    private Strip logStrip;
    private String logCategory, logAccount;
    private BrowserModels.Catalogue catalogue;
    private BrowserCommand request;
    private BrowserModels.View data;
    private CollectionGrid grid;
    private LeaderboardView standings;
    private Call dataCall, catalogueCall;
    private long accountHash;
    private String viewer;
    private String kind = "clog", lastCategory = "vorkath", lastMetric = "clog";
    private boolean adjusting, closed;
    private int generation;

    public ClanBrowserWindow(Window owner, BrowserApi api, IntFunction<BufferedImage> icons) {
        super(owner, "Reval · Clan browser", Dialog.ModalityType.MODELESS);
        this.api = api; this.icons = icons;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); setMinimumSize(new Dimension(960, 620)); setSize(1180, 820);
        setLocationRelativeTo(owner);
        java.awt.GraphicsConfiguration screen = owner != null ? owner.getGraphicsConfiguration() : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
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
        BrowserTheme.field(player); player.putClientProperty("JTextField.placeholderText", "Your account");
        player.setToolTipText("Press Enter to load this account's collection log");
        player.getAccessibleContext().setAccessibleName("OSRS player name");
        JLabel playerLabel = label("Player", 20, BrowserTheme.MUTED); playerBox.add(playerLabel, BorderLayout.WEST); playerBox.add(player, BorderLayout.CENTER);
        player.addActionListener(event -> submitSelection());
        selection.onSubmit(value -> { if (!adjusting) { configureExtra(); submitSelection(); } });
        boss.onSubmit(value -> { if (!adjusting) submitSelection(); });
        tiers.addActionListener(event -> { if (!adjusting) submitSelection(); });
        styleCombo(tiers); styleCombo(itemFilter);
        controls.add(choices);
        JPanel tools = panel(new BorderLayout(8, 0)); BrowserTheme.field(search); search.getAccessibleContext().setAccessibleName("Search displayed items or clan members");
        tools.add(search, BorderLayout.CENTER);
        JPanel toolsRight = panel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        toolsRight.add(playerBox); toolsRight.add(itemFilter); toolsRight.add(findMe);
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
        itemFilter.addActionListener(event -> filter()); findMe.addActionListener(event -> { search.setText(""); if (standings != null) standings.findViewer(); });
        loading.setIndeterminate(true); loading.setForeground(BrowserTheme.GOLD); loading.setBackground(BrowserTheme.PANEL); loading.setPreferredSize(new Dimension(10, 3)); loading.setVisible(false);
        top.add(controls, BorderLayout.CENTER); top.add(loading, BorderLayout.SOUTH);
        frame.add(top, BorderLayout.NORTH); frame.add(body, BorderLayout.CENTER);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close-browser");
        getRootPane().getActionMap().put("close-browser", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent event) { dispose(); } });
        addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent event) { closed = true; generation++; cancel(); } });
    }

    public void identity(long accountHash, String viewer) { this.accountHash = accountHash; this.viewer = viewer; findMe.setEnabled(viewer != null); if (standings != null) standings.viewer(viewer); }
    public void open(BrowserCommand command) { open(command, null); }
    public void open(BrowserCommand command, String sharedBy) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Browser window must open on the EDT");
        closed = false; search.setText(""); setVisible(true); toFront();
        if (catalogue != null) { configure(command.resolveShared(catalogue.pages, sharedBy)); perform(); return; }
        request = command; busy(true); message("Loading clan categories…", null);
        final int ticket = ++generation;
        catalogueCall = api.catalogue(result -> {
            if (closed) return;
            catalogue = result;
            if (ticket != generation) return;
            configure(command.resolveShared(catalogue.pages, sharedBy)); perform();
        }, error -> { if (!closed && ticket == generation) { busy(false); message(error, () -> open(command, sharedBy)); } });
    }

    private void configure(BrowserCommand command) {
        adjusting = true; request = command; kind = command.kind;
        if (command.category != null) lastCategory = command.category;
        if (command.metric != null) lastMetric = command.metric;
        for (Map.Entry<String, JButton> entry : modeButtons.entrySet()) {
            boolean active = entry.getKey().equals(kind); entry.getValue().setBackground(active ? new Color(0x6b5a3d) : BrowserTheme.HEADER);
            entry.getValue().setForeground(active ? BrowserTheme.GOLD : BrowserTheme.TEXT);
        }
        List<BrowserModels.Page> pages = new ArrayList<>();
        if (catalogue != null) for (BrowserModels.Page page : catalogue.pages) if (!kind.equals("dryboard") || page.category.equals("Bosses") || page.category.equals("Raids")) pages.add(page);
        boolean board = kind.equals("leaderboard"); choices.setVisible(!board && !kind.equals("clog")); statistics.setVisible(board);
        selection.choices(pages);
        selection.placeholder("Search a category, or type cox / tob / toa…");
        selection.value(lastCategory);
        for (Map.Entry<String, JButton> entry : metricButtons.entrySet()) {
            boolean active = entry.getKey().equals(lastMetric);
            entry.getValue().setBackground(active ? new Color(0x6b5a3d) : BrowserTheme.HEADER);
            entry.getValue().setForeground(active ? BrowserTheme.GOLD : BrowserTheme.TEXT);
        }
        boss.choices(catalogue.bosses); boss.placeholder("All bosses, or search a boss…"); boss.value(command.boss == null ? "all" : command.boss);
        tiers.setSelectedItem(command.tier == null || command.tier.equals("all") ? "All tiers" : capitalize(command.tier));
        player.setText(command.player == null ? "" : command.player); playerBox.setVisible(kind.equals("clog"));
        itemFilter.setVisible(kind.equals("clog")); findMe.setVisible(!kind.equals("clog"));
        search.putClientProperty("JTextField.placeholderText", kind.equals("clog") ? "Find an item…" : "Find a clan member…");
        configureExtra(); adjusting = false;
    }

    private void configureExtra() {
        extra.removeAll(); boolean board = kind.equals("leaderboard"); String metric = lastMetric;
        boolean show = board && (metric.equals("bosses") || metric.equals("clues"));
        if (show) {
            JPanel filter = panel(new BorderLayout(8, 0)); filter.setPreferredSize(new Dimension(420, 36));
            filter.add(label(metric.equals("bosses") ? "Boss" : "Clue tier", 20, BrowserTheme.MUTED), BorderLayout.WEST);
            filter.add(metric.equals("bosses") ? boss : tiers, BorderLayout.CENTER); extra.add(filter, BorderLayout.WEST);
        }
        extra.setVisible(show); extra.revalidate();
    }
    private void chooseMetric(String metric) {
        if (catalogue == null) return;
        configure(new BrowserCommand("leaderboard", null, metric, null, null, player.getText())); perform();
    }
    private void chooseMode(String mode) {
        if (catalogue == null) return;
        configure(new BrowserCommand(mode, categoryForMode(mode, lastCategory, catalogue.pages), lastMetric, null, null, player.getText())); search.setText(""); perform();
    }
    static String categoryForMode(String mode, String category, List<BrowserModels.Page> pages) {
        if (mode.equals("leaderboard") || mode.equals("clog")) return category;
        String needle = BrowserCommand.normalize(category);
        for (BrowserModels.Page page : pages) {
            if (mode.equals("dryboard") && !page.category.equals("Bosses") && !page.category.equals("Raids")) continue;
            if (BrowserCommand.normalize(page.value).equals(needle) || BrowserCommand.normalize(page.label).equals(needle)
                || page.aliases.stream().anyMatch(alias -> BrowserCommand.normalize(alias).equals(needle))) return page.value;
        }
        return needle.equals("clues") && !mode.equals("dryboard") ? "hard-clues" : "vorkath";
    }
    private void submitSelection() { submitSelection(false); }
    private void submitSelection(boolean refresh) {
        if (catalogue == null || adjusting) return;
        String metric = kind.equals("leaderboard") ? lastMetric : null;
        request = new BrowserCommand(kind, kind.equals("leaderboard") ? null : kind.equals("clog") ? lastCategory : selection.value(), metric,
            "bosses".equals(metric) ? boss.value() : null,
            "clues".equals(metric) ? "All tiers".equals(tiers.getSelectedItem()) ? "all" : tiers.getSelectedItem().toString().toLowerCase(Locale.ROOT) : null,
            kind.equals("clog") ? player.getText() : null);
        if (request.category != null) lastCategory = request.category;
        if (request.metric != null) lastMetric = request.metric;
        configureExtra(); perform(refresh);
    }
    private void perform() { perform(false); }
    private void perform(boolean refresh) {
        if (request == null) return;
        if (request.kind.equals("help")) { showHelp(); return; }
        if (dataCall != null) dataCall.cancel();
        final int ticket = ++generation;
        dataCall = api.view(request, accountHash, refresh, result -> {
            if (closed || ticket != generation) return;
            data = result; busy(false); render();
        }, error -> {
            if (closed || ticket != generation) return;
            busy(false);
            if (request.kind.equals("clog")) collectionMessage(error, () -> submitSelection(refresh));
            else message(error, () -> submitSelection(refresh));
        });
        // Cache hits have already rendered in this EDT event. Never clear them for a loading frame.
        if (dataCall != null) {
            data = null; grid = null; standings = null;
            if (request.kind.equals("clog")) { busy(false); loadingItems(); }
            else {
                busy(true); title.text = titleFor(request); title.repaint();
                message("Loading clan standings…", null);
            }
        }
    }

    private void render() {
        grid = null; standings = null;
        boolean clog = "clog".equals(data.kind);
        if (clog && data.account != null) {
            title.text = "Collection Log  (" + BrowserTheme.number(data.account.obtained) + " / " + BrowserTheme.number(data.account.total) + ")";
            if (data.page.value != null) { lastCategory = data.page.value; lastLogPages.put(data.page.category, data.page.value); }
            if (request.player != null) player.setText(data.account.name);
            collectionLayout(); collectionNavigation(data.page);
            logAccount = logAccountKey();
            logStrip.update(data.account.name, "Last synced: " + BrowserTheme.timestamp(data.account.lastSyncedAt));
            logDetail.removeAll(); logDetail.add(new LogSummary(data), BorderLayout.NORTH);
            grid = new CollectionGrid(data.groups, icons); logDetail.add(BrowserTheme.scroll(grid), BorderLayout.CENTER);
            setTitle("Reval · " + data.account.name + " · " + data.page.label);
        } else {
            body.removeAll();
            String page = data.page != null ? data.page.label : null;
            title.text = "dryboard".equals(data.kind) ? "Dryboard · " + page : "deserved".equals(data.kind) ? "Deserved Greenlogs · " + page : "luck".equals(data.kind) ? "Greenlog Luck · " + page : data.title;
            String left = "dryboard".equals(data.kind) ? "Kills since last recorded unique" : page != null ? page : "Full clan standings";
            if ("luck".equals(data.kind) || "deserved".equals(data.kind)) {
                boolean chasingOnly = !data.rows.isEmpty() && data.rows.stream().allMatch(row -> "progress".equals(row.status));
                left = chasingOnly ? "Closest to greenlog · Most slots first" : data.metric != null && data.metric.isJsonObject()
                    ? ("deserved".equals(data.kind) ? "Highest" : "Lowest") + " completion " + LeaderboardView.metricLabel(data) + " first" : "Completed logs first";
            }
            String right = data.completedTotal != null ? BrowserTheme.number(data.completedTotal) + " greenlogged" : data.subtitle != null ? data.subtitle : "Updated: " + BrowserTheme.timestamp(data.generatedAt);
            body.add(new Strip(left, right), BorderLayout.NORTH);
            standings = new LeaderboardView(data, viewer, data.page == null ? null : name -> {
                configure(new BrowserCommand("clog", data.page.value != null ? data.page.value : data.page.subcategory, null, null, null, name)); perform();
            });
            body.add(standings, BorderLayout.CENTER);
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
        if (logContent.getParent() != body) { body.removeAll(); body.add(logContent, BorderLayout.CENTER); }
    }

    private void collectionNavigation(BrowserModels.Page page) {
        if (page == null) return;
        if (!page.category.equals(logCategory)) {
            logCategory = page.category; logPages.clear(); logSidebar.removeAll();
            logSidebar.add(categoryList(page.category, page.value), BorderLayout.CENTER);
        }
        if (page.value != null) lastLogPages.put(page.category, page.value);
        logTabs.forEach((category, button) -> highlight(button, category.equals(page.category)));
        logPages.forEach((value, button) -> highlight(button, value.equals(page.value)));
    }

    private BrowserModels.Page selectedLogPage() {
        String needle = BrowserCommand.normalize(request.category);
        if (java.util.Arrays.asList("clues", "all clues", "all clue tiers").contains(needle)) {
            BrowserModels.Page page = new BrowserModels.Page(); page.value = "clues"; page.label = "All clue tiers"; page.category = "Clues"; return page;
        }
        for (BrowserModels.Page page : catalogue.pages) {
            if (BrowserCommand.normalize(page.value).equals(needle) || BrowserCommand.normalize(page.label).equals(needle)
                || page.aliases.stream().anyMatch(alias -> BrowserCommand.normalize(alias).equals(needle))) return page;
        }
        return null;
    }

    private String logAccountKey() { return request.player == null ? "account:" + accountHash : "player:" + request.player.toLowerCase(Locale.ROOT); }

    private void loadingItems() {
        collectionLayout(); BrowserModels.Page page = selectedLogPage(); collectionNavigation(page);
        if (!logAccountKey().equals(logAccount)) {
            logStrip.update(request.player != null ? request.player : viewer != null ? viewer : "Your account", "");
            title.text = "Collection Log";
        }
        String name = page == null ? request.category : page.label;
        JPanel summary = panel(new BorderLayout()); summary.setPreferredSize(new Dimension(100, 88));
        JLabel category = label(name, 29, BrowserTheme.ORANGE); category.setHorizontalAlignment(SwingConstants.CENTER);
        category.setPreferredSize(new Dimension(100, 38)); summary.add(category, BorderLayout.NORTH);
        logDetail.removeAll(); logDetail.add(summary, BorderLayout.NORTH);
        JPanel pending = messagePanel("Loading items…", null);
        JProgressBar progress = new JProgressBar(); progress.setIndeterminate(true); progress.setPreferredSize(new Dimension(100, 3));
        progress.setForeground(BrowserTheme.GOLD); progress.setBackground(BrowserTheme.PANEL); pending.add(progress, BorderLayout.SOUTH);
        logDetail.add(pending, BorderLayout.CENTER);
        setTitle("Reval · " + (request.player != null ? request.player : viewer != null ? viewer : "Your account") + " · " + name);
        title.repaint(); body.revalidate(); body.repaint();
    }

    private void collectionMessage(String text, Runnable retry) {
        collectionLayout(); grid = null; standings = null;
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
        if (category.equals("Clues")) { BrowserModels.Page all = new BrowserModels.Page(); all.value = "clues"; all.label = "All clue tiers"; pages.add(all); }
        for (BrowserModels.Page page : catalogue.pages) if (category.equals(page.category)) pages.add(page);
        JButton active = null;
        for (BrowserModels.Page page : pages) {
            JButton button = BrowserTheme.button(page.label); button.setFont(BrowserTheme.font(18, true)); button.setToolTipText(page.label);
            button.setAlignmentX(LEFT_ALIGNMENT); button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            button.setPreferredSize(new Dimension(206, 36));
            boolean chosen = page.value.equals(selected);
            button.setBackground(chosen ? new Color(0x6b5a3d) : BrowserTheme.HEADER); button.setForeground(chosen ? BrowserTheme.GOLD : BrowserTheme.TEXT);
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
        configure(new BrowserCommand("clog", value, null, null, null, player.getText())); perform();
    }
    private void selectTab(String category) {
        String remembered = lastLogPages.get(category);
        if (remembered != null) { selectPage(remembered); return; }
        if (category.equals("Clues")) { selectPage("clues"); return; }
        for (BrowserModels.Page page : catalogue.pages) if (category.equals(page.category)) { selectPage(page.value); return; }
    }
    private void filter() { if (grid != null) grid.filter(search.getText(), itemFilter.getSelectedItem().toString()); if (standings != null) standings.filter(search.getText()); }
    private void busy(boolean value) { loading.setVisible(value); }
    private void message(String text, Runnable retry) {
        grid = null; standings = null; body.removeAll();
        body.add(messagePanel(text, retry), BorderLayout.CENTER); body.revalidate(); body.repaint();
    }
    private JPanel messagePanel(String text, Runnable retry) {
        JPanel box = panel(new GridBagLayout()); JPanel inner = panel(new BorderLayout(0, 16));
        JLabel label = label(text, 23, BrowserTheme.TEXT); label.setHorizontalAlignment(SwingConstants.CENTER); inner.add(label, BorderLayout.CENTER);
        if (retry != null) { JButton button = BrowserTheme.button("Try again"); button.addActionListener(event -> retry.run()); inner.add(button, BorderLayout.SOUTH); }
        box.add(inner);
        JPanel content = panel(new BorderLayout()); content.add(box, BorderLayout.CENTER); return content;
    }
    private void showHelp() {
        if (dataCall != null) dataCall.cancel(); generation++; data = null; busy(false); grid = null; standings = null;
        title.text = "Reval · Command guide"; title.repaint(); body.removeAll();
        JPanel help = panel(new java.awt.GridLayout(0, 1, 0, 12));
        String[][] examples = {{"::reval clog forestry", "Your collection log; hover an item for its name."},
            {"::reval clog forestry Valgusruum", "View another clan account. Player names with spaces can be quoted."},
            {"::reval luck vorkath", "Lowest greenlog completion counters first."},
            {"::reval deserved vorkath", "Highest greenlog completion counters first."},
            {"::reval dryboard vorkath", "Kills since the last recorded unique, including duplicate drops."},
            {"::reval leaderboard clues hard", "Full clue-tier standings, split into account types."},
            {"::reval leaderboard bosses vorkath", "A specific boss, or leave its name out for all bosses."},
            {"::reval leaderboard clog | pets | points | greenlogs | xp", "All-time standings. Use the statistic buttons below the member search to switch."},
            {"::reval leaderboard monthly-clog | monthly-points | clues | combat | bosses", "Monthly standings use the clan's Tallinn calendar month."},
            {"!reval luck araxxor", "Share through clan chat. Updated Reval clients can click the message to open the board."},
            {"!reval clog forestry", "Share your log, or add a player name to share another account's log."},
            {"Refresh", "Revisits use your session cache. Refresh reloads this view from clan data."}};
        for (String[] example : examples) {
            JPanel row = panel(new BorderLayout(0, 3)); row.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            row.add(label(example[0], 22, BrowserTheme.GOLD), BorderLayout.NORTH); row.add(label(example[1], 18, BrowserTheme.MUTED), BorderLayout.SOUTH); help.add(row);
        }
        body.add(BrowserTheme.scroll(help), BorderLayout.CENTER); body.revalidate(); body.repaint();
    }
    private void cancel() { if (dataCall != null) dataCall.cancel(); if (catalogueCall != null) catalogueCall.cancel(); }
    BrowserModels.View currentView() { return loading.isVisible() ? null : data; }
    private static String titleFor(BrowserCommand command) { return command.kind.equals("clog") ? "Collection Log" : command.kind.equals("deserved") ? "Deserved Greenlogs" : command.kind.equals("dryboard") ? "Dryboard" : command.kind.equals("luck") ? "Greenlog Luck" : "Clan Leaderboards"; }
    private static String capitalize(String value) { return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1); }
    private static JPanel panel(java.awt.LayoutManager layout) { JPanel panel = new JPanel(layout); panel.setBackground(BrowserTheme.BACKGROUND); return panel; }
    private static JLabel label(String text, int size, Color color) { JLabel label = new JLabel(text); label.setFont(BrowserTheme.font(size, false)); label.setForeground(color); return label; }
    private static void styleCombo(JComboBox<?> combo) { combo.setFont(BrowserTheme.font(20, false)); combo.setForeground(BrowserTheme.TEXT); combo.setBackground(BrowserTheme.HEADER); combo.setFocusable(false); }

    private static final class Title extends JComponent {
        String text = "Reval · Clan browser";
        Title() { setPreferredSize(new Dimension(100, 66)); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); BrowserTheme.pixel(g); g.setColor(BrowserTheme.HEADER); g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(BrowserTheme.DARK_BORDER); g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            BrowserTheme.centered(g, text == null ? "Clan standings" : text, getWidth() / 2, 0, getHeight(), getWidth() - 20, 36, BrowserTheme.ORANGE, true); g.dispose();
        }
    }
    private static final class Strip extends JComponent {
        private String left, right;
        Strip(String left, String right) { this.left = left; this.right = right; setPreferredSize(new Dimension(100, 42)); }
        void update(String left, String right) { this.left = left; this.right = right; repaint(); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); BrowserTheme.pixel(g);
            BrowserTheme.text(g, left, 2, 0, getHeight(), getWidth() / 2 - 12, 25, BrowserTheme.TEXT, true, false);
            BrowserTheme.text(g, right, getWidth() - 2, 0, getHeight(), getWidth() / 2 - 12, 20, BrowserTheme.MUTED, false, true); g.dispose();
        }
    }
    private static final class LogSummary extends JComponent {
        private final BrowserModels.View view;
        LogSummary(BrowserModels.View view) { this.view = view; setPreferredSize(new Dimension(100, view.groups.size() == 1 && view.groups.get(0).rank != null ? 112 : 88)); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); BrowserTheme.pixel(g); int width = getWidth();
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
                BrowserModels.LuckRank rank = view.groups.get(0).rank;
                BrowserTheme.text(g, "Clan luck: #" + rank.rank + " / " + rank.total + (rank.estimated ? " (est.)" : ""), 12, 72, 30, width - 24, 22, BrowserTheme.GOLD, true, false);
            }
            g.dispose();
        }
    }
}

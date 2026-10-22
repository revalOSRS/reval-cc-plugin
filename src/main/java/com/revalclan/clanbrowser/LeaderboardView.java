package com.revalclan.clanbrowser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

/** Full, virtualized tables: scores keep their clan rank when the local search filters them. */
final class LeaderboardView extends JPanel {
    private final List<Rows> models = new ArrayList<>();
    private final List<JTable> tables = new ArrayList<>();
    private final boolean dry;
    private final String valueLabel;
    private String viewer;

    LeaderboardView(BrowserModels.View view, String viewer, Consumer<String> openLog) {
        super(new BorderLayout(0, 10)); setOpaque(false); this.viewer = viewer;
        dry = "dryboard".equals(view.kind);
        boolean progress = view.rows != null && view.rows.stream().anyMatch(row -> "progress".equals(row.status));
        valueLabel = view.valueLabel != null ? view.valueLabel : dry ? "Dry KC" : (view.metric != null && view.metric.isJsonObject() ? metricLabel(view) : "Status") + (progress ? " / slots" : "");
        JPanel body = new JPanel(); body.setOpaque(false); body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        if (view.sections != null && !view.sections.isEmpty()) {
            JTabbedPane tabs = new JTabbedPane(); tabs.setFont(BrowserTheme.font(21, true)); tabs.setForeground(BrowserTheme.TEXT); tabs.setBackground(BrowserTheme.HEADER);
            for (BrowserModels.Section section : view.sections) {
                String label = section.tier == null || section.tier.equals("all") ? "Total" : BrowserTheme.capitalize(section.tier);
                tabs.addTab(label, null, section(section.panels, openLog), section.label);
            }
            body.add(tabs);
        } else if (view.panels != null && !view.panels.isEmpty()) {
            body.add(section(view.panels, openLog));
        } else {
            BrowserModels.Panel panel = new BrowserModels.Panel(); panel.label = view.page != null ? view.page.label : "Clan standings";
            panel.rows = view.rows; panel.color = "#ffbb48"; panel.total = view.rows.size();
            body.add(section(java.util.Collections.singletonList(panel), openLog));
        }
        add(body, BorderLayout.CENTER);
    }

    static String metricLabel(BrowserModels.View view) {
        String kind = view.metric.getAsJsonObject().has("kind") ? view.metric.getAsJsonObject().get("kind").getAsString() : "kc";
        return BrowserTheme.metricName(kind);
    }

    private JPanel section(List<BrowserModels.Panel> panels, Consumer<String> openLog) {
        JPanel section = new JPanel(new BorderLayout(0, 6)); section.setOpaque(false);
        JPanel columns = new JPanel(new GridLayout(1, Math.max(1, panels.size()), 12, 0)); columns.setOpaque(false);
        for (BrowserModels.Panel panel : panels) {
            JPanel card = new JPanel(new BorderLayout()); card.setBackground(BrowserTheme.PANEL); card.setBorder(BorderFactory.createLineBorder(BrowserTheme.BORDER));
            JLabel heading = new JLabel(panel.label + "  (" + BrowserTheme.number(panel.total) + ")", SwingConstants.LEFT);
            heading.setToolTipText(heading.getText());
            heading.setOpaque(true); heading.setBackground(BrowserTheme.HEADER); heading.setForeground(color(panel.color)); heading.setFont(BrowserTheme.font(23, true));
            heading.setBorder(BorderFactory.createEmptyBorder(9, 12, 8, 8)); card.add(heading, BorderLayout.NORTH);
            Rows model = new Rows(panel.rows); models.add(model);
            JTable table = new JTable(model) {
                @Override public String getToolTipText(MouseEvent event) {
                    int index = rowAtPoint(event.getPoint());
                    if (index < 0 || index >= model.visible.size()) return null;
                    BrowserModels.Row row = model.visible.get(index);
                    if ("section".equals(row.status)) return "Closest unfinished collection logs";
                    return row.player + (row.estimated ? " · Estimated counter" : "")
                        + (dry ? " · " + row.item + " · Last unique KC: " + BrowserTheme.number(row.last) + " · Current KC: " + BrowserTheme.number(row.current) : "")
                        + (openLog != null ? " · Double-click to open their collection log" : "");
                }
            };
            table.setDefaultRenderer(Object.class, new RowCell()); table.setRowHeight(dry ? 70 : 42);
            table.setBackground(BrowserTheme.PANEL); table.setForeground(BrowserTheme.TEXT); table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0, 1));
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setFillsViewportHeight(true);
            table.getTableHeader().setFont(BrowserTheme.font(19, true)); table.getTableHeader().setBackground(BrowserTheme.HEADER); table.getTableHeader().setForeground(BrowserTheme.MUTED);
            table.getTableHeader().setReorderingAllowed(false); table.getTableHeader().setPreferredSize(new Dimension(100, 31));
            table.getColumnModel().getColumn(0).setPreferredWidth(48); table.getColumnModel().getColumn(0).setMaxWidth(64);
            table.getColumnModel().getColumn(1).setPreferredWidth(145); table.getColumnModel().getColumn(2).setPreferredWidth(125);
            table.getAccessibleContext().setAccessibleName(panel.label + " full leaderboard");
            if (openLog != null) table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    int row = table.rowAtPoint(event.getPoint());
                    if (event.getClickCount() == 2 && row >= 0 && row < model.visible.size() && !"section".equals(model.visible.get(row).status)) openLog.accept(model.visible.get(row).player);
                }
            });
            JScrollPane scroll = BrowserTheme.scroll(table); scroll.setBorder(null); scroll.setPreferredSize(new Dimension(300, 480));
            if (panel.rows.isEmpty()) {
                JLabel empty = new JLabel(dry ? "No comparable drop KC yet" : "No recorded scores", SwingConstants.CENTER);
                empty.setFont(BrowserTheme.font(22, false)); empty.setForeground(BrowserTheme.MUTED); card.add(empty, BorderLayout.CENTER);
            } else card.add(scroll, BorderLayout.CENTER);
            tables.add(table); columns.add(card);
        }
        section.add(columns, BorderLayout.CENTER); section.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        return section;
    }

    void filter(String text) { for (Rows model : models) model.filter(text); }
    void viewer(String name) { viewer = name; repaint(); }
    void findViewer() {
        if (viewer == null) return;
        for (int i = 0; i < models.size(); i++) {
            if (!tables.get(i).isShowing()) continue;
            Rows model = models.get(i);
            for (int row = 0; row < model.visible.size(); row++) if (BrowserCommand.normalize(model.visible.get(row).player).equals(BrowserCommand.normalize(viewer))) {
                JTable table = tables.get(i); table.getSelectionModel().setSelectionInterval(row, row); table.scrollRectToVisible(table.getCellRect(row, 1, true)); return;
            }
        }
    }

    private static Color color(String hex) { try { return Color.decode(hex); } catch (Exception ignored) { return BrowserTheme.GOLD; } }
    private final class Rows extends AbstractTableModel {
        final List<BrowserModels.Row> all;
        List<BrowserModels.Row> visible;
        Rows(List<BrowserModels.Row> rows) { all = rows == null ? new ArrayList<>() : new ArrayList<>(rows); filter(""); }
        void filter(String query) {
            String needle = BrowserCommand.normalize(query); visible = new ArrayList<>();
            boolean divider = false;
            for (BrowserModels.Row row : all) if (BrowserCommand.normalize(row.player).contains(needle)) {
                if (!divider && "progress".equals(row.status)) {
                    BrowserModels.Row heading = new BrowserModels.Row(); heading.status = "section"; heading.player = "Closest to greenlog"; visible.add(heading); divider = true;
                }
                visible.add(row);
            }
            fireTableDataChanged();
        }
        @Override public int getRowCount() { return visible.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int col) { return col == 0 ? "Rank" : col == 1 ? "Account" : valueLabel; }
        @Override public Object getValueAt(int row, int column) { return visible.get(row); }
    }

    private final class RowCell extends JPanel implements TableCellRenderer {
        private BrowserModels.Row row;
        private int column;
        private boolean selected;
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int index, int column) {
            row = (BrowserModels.Row) value; this.column = column; this.selected = selected; return this;
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = BrowserTheme.pixel(graphics);
            if ("section".equals(row.status)) {
                g.setColor(BrowserTheme.HEADER); g.fillRect(0, 0, getWidth(), getHeight());
                if (column == 1) BrowserTheme.text(g, row.player, 6, 0, getHeight(), getWidth() - 12, 21, BrowserTheme.GOLD, true, false);
                g.dispose(); return;
            }
            boolean progress = "progress".equals(row.status), mine = viewer != null && BrowserCommand.normalize(viewer).equals(BrowserCommand.normalize(row.player));
            g.setColor(selected ? new Color(0x596141) : progress ? BrowserTheme.MISSING : BrowserTheme.RECEIVED); g.fillRect(0, 0, getWidth(), getHeight());
            if (mine) { g.setColor(BrowserTheme.GOLD); g.fillRect(0, getHeight() - 2, getWidth(), 2); }
            int height = dry ? 34 : getHeight();
            if (column == 0) BrowserTheme.text(g, row.rank == null ? "—" : "#" + row.rank, 9, 0, height, getWidth() - 15, 23, BrowserTheme.GOLD, true, false);
            else if (column == 1) {
                BrowserTheme.text(g, row.player, 6, 0, height, getWidth() - 12, 24, BrowserTheme.TEXT, true, false);
                if (dry) BrowserTheme.text(g, row.item, 6, 34, 30, getWidth() - 12, 17, BrowserTheme.MUTED, false, false);
            } else {
                String value = progress ? BrowserTheme.number(row.obtained) + " / " + BrowserTheme.number(row.totalItems)
                    : row.value == null ? "Greenlogged" : (row.estimated ? "~" : "") + BrowserTheme.number(row.value);
                BrowserTheme.text(g, value, getWidth() - 9, 0, height, getWidth() - 15, 25, progress ? BrowserTheme.GOLD : BrowserTheme.GREEN, true, true);
                if (dry) BrowserTheme.text(g, BrowserTheme.number(row.last) + " / " + BrowserTheme.number(row.current), getWidth() - 9, 34, 30, getWidth() - 15, 17, BrowserTheme.MUTED, false, true);
            }
            g.dispose();
        }
    }
}

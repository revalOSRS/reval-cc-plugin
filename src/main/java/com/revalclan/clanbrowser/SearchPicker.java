package com.revalclan.clanbrowser;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Local autocomplete: typing never makes a network request. */
final class SearchPicker extends JPanel {
    private final JTextField field = new JTextField();
    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<BrowserModels.Choice> model = new DefaultListModel<>();
    private final JList<BrowserModels.Choice> list = new JList<>(model);
    private List<? extends BrowserModels.Choice> choices = new ArrayList<>();
    private BrowserModels.Choice selected;
    private Consumer<String> submit = ignored -> {};
    private boolean adjusting;

    SearchPicker(String accessibleName) {
        super(new BorderLayout());
        setOpaque(false); BrowserTheme.field(field);
        field.getAccessibleContext().setAccessibleName(accessibleName);
        JButton arrow = BrowserTheme.button("v");
        arrow.setFocusable(false); arrow.getAccessibleContext().setAccessibleName("Show " + accessibleName);
        add(field, BorderLayout.CENTER); add(arrow, BorderLayout.EAST);
        list.setFont(BrowserTheme.font(21, false)); list.setBackground(BrowserTheme.PANEL); list.setForeground(BrowserTheme.TEXT);
        list.setSelectionBackground(BrowserTheme.RECEIVED); list.setSelectionForeground(BrowserTheme.GOLD);
        list.setFixedCellHeight(32); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName(accessibleName + " suggestions");
        JScrollPane scroll = BrowserTheme.scroll(list);
        popup.setLayout(new BorderLayout()); popup.add(scroll); popup.setFocusable(false);
        arrow.addActionListener(event -> { if (popup.isVisible()) popup.setVisible(false); else updatePopup(true); });
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { changed(); }
            public void removeUpdate(DocumentEvent event) { changed(); }
            public void changedUpdate(DocumentEvent event) { changed(); }
        });
        field.addActionListener(event -> {
            if (popup.isVisible() && list.getSelectedValue() != null) choose(list.getSelectedValue());
            else submit.accept(value());
        });
        field.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_DOWN || event.getKeyCode() == KeyEvent.VK_UP) {
                    if (!popup.isVisible()) updatePopup(false);
                    int next = Math.max(0, Math.min(model.size() - 1, list.getSelectedIndex() + (event.getKeyCode() == KeyEvent.VK_DOWN ? 1 : -1)));
                    list.setSelectedIndex(next); list.ensureIndexIsVisible(next); event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_ESCAPE && popup.isVisible()) {
                    popup.setVisible(false); event.consume();
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(event.getPoint())) choose(model.get(index));
            }
        });
    }

    void onSubmit(Consumer<String> callback) { submit = callback; }
    void choices(List<? extends BrowserModels.Choice> values) { choices = new ArrayList<>(values); }
    void value(String value) {
        selected = choices.stream().filter(choice -> same(choice.value, value) || same(choice.label, value)).findFirst().orElse(null);
        adjusting = true; field.setText(selected == null ? value == null ? "" : value : selected.toString()); adjusting = false;
        field.setCaretPosition(0); popup.setVisible(false);
    }
    String value() { return selected != null && field.getText().equals(selected.toString()) ? selected.value : field.getText().trim(); }
    void placeholder(String text) { field.putClientProperty("JTextField.placeholderText", text); field.setToolTipText(text); }
    private static boolean same(String a, String b) { return a != null && b != null && BrowserCommand.normalize(a).equals(BrowserCommand.normalize(b)); }
    private void changed() { if (!adjusting && field.hasFocus()) { selected = null; updatePopup(false); } }
    private void choose(BrowserModels.Choice choice) { value(choice.value); submit.accept(choice.value); }

    static List<BrowserModels.Choice> matches(List<? extends BrowserModels.Choice> choices, String query) {
        String needle = BrowserCommand.normalize(query);
        List<BrowserModels.Choice> result = new ArrayList<>();
        for (BrowserModels.Choice choice : choices) if (score(choice, needle) < 3) result.add(choice);
        result.sort(Comparator.comparingInt((BrowserModels.Choice choice) -> score(choice, needle)).thenComparing(choice -> choice.toString(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    private static int score(BrowserModels.Choice choice, String needle) {
        List<String> names = new ArrayList<>(); names.add(choice.label); names.add(choice.value);
        names.addAll(choice.aliases);
        int score = needle.isEmpty() ? 2 : 3;
        for (String raw : names) {
            String value = BrowserCommand.normalize(raw);
            score = Math.min(score, value.equals(needle) ? 0 : value.startsWith(needle) ? 1 : value.contains(needle) ? 2 : 3);
        }
        return score;
    }
    private void updatePopup(boolean all) {
        List<BrowserModels.Choice> matches = matches(choices, all ? "" : field.getText());
        // Limit the popup height below, not the results: the full catalogue must be scrollable.
        model.clear(); matches.forEach(model::addElement);
        if (model.isEmpty() || !isShowing()) { popup.setVisible(false); return; }
        list.setSelectedIndex(0);
        popup.setPopupSize(new Dimension(Math.max(240, getWidth()), Math.min(285, model.size() * 32 + 4)));
        if (!popup.isVisible()) popup.show(this, 0, getHeight());
    }
}

package com.revalclan.clanbrowser;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Scrollable;

/** A compact guide: syntax, concrete examples, and local opening and shared commands. */
final class BrowserHelpView extends JPanel implements Scrollable {
    BrowserHelpView() {
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS)); setBackground(BrowserTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        JPanel intro=card("Start here",BrowserTheme.GOLD);
        intro.add(text("Send !reval in public, clan or private chat.\nClick its link, then choose Open Reval board.\nUse browser buttons to browse without sending more messages.",20,BrowserTheme.TEXT,3));
        add(intro); gap(10);
        add(text("<...> = a value you choose     [...] = optional     Do not type the brackets.",18,BrowserTheme.MUTED,1)); gap(12);

        JPanel logs=card("Collection logs",BrowserTheme.GREEN);
        logs.add(text("!reval clog <category> [player]",21,BrowserTheme.GOLD,1)); gap(logs,8);
        logs.add(text("Leave the player out for your own account.\nAdd a name to inspect another clan account.",18,BrowserTheme.TEXT,2)); gap(logs,8);
        logs.add(code("!reval clog forestry\n!reval clog forestry Valgusruum\n!reval clog clues Phosani55555",3));
        logs.add(text("Use quotes for names with spaces: \"Love Yourz\".\nClues shows all tiers; individual tiers are in the sidebar.",17,BrowserTheme.MUTED,2));

        JPanel categories=card("Luck, Deserved & Dryboard",BrowserTheme.ORANGE);
        categories.add(text("Use a boss or category after the board name.",18,BrowserTheme.TEXT,1)); gap(categories,8);
        categories.add(code("!reval luck vorkath",1));
        categories.add(text("Lowest counter to finish a greenlog first.",18,BrowserTheme.MUTED,1)); gap(categories,7);
        categories.add(code("!reval deserved vorkath",1));
        categories.add(text("Highest counter to finish a greenlog first.",18,BrowserTheme.MUTED,1)); gap(categories,7);
        categories.add(code("!reval dryboard vorkath",1));
        categories.add(text("Kills since the last recorded unique drop.\nDuplicate uniques reset the streak. Bosses and raids only.",18,BrowserTheme.MUTED,2));
        add(row(logs,categories)); gap(12);

        JPanel boards=card("Clan leaderboards",BrowserTheme.GOLD);
        boards.add(text("!reval leaderboard <statistic> [filter]",21,BrowserTheme.GOLD,1)); gap(boards,8);
        boards.add(code("!reval leaderboard xp\n!reval leaderboard clues hard\n!reval leaderboard bosses toa",3));
        boards.add(text("Leave the clue tier or boss out for the combined view.\nChoose statistics with the buttons below member search.",18,BrowserTheme.TEXT,2)); gap(boards,6);
        boards.add(text("Statistics: clog, pets, points, greenlogs, xp,\nmonthly-clog, monthly-points, clues, combat, bosses.\nClue tiers: beginner, easy, medium, hard, elite, master.",17,BrowserTheme.MUTED,3));

        JPanel sharing=card("Share a board in chat",BrowserTheme.GREEN);
        sharing.add(text("Send a command in public, clan or private chat.",18,BrowserTheme.TEXT,2)); gap(sharing,8);
        sharing.add(code("!reval luck araxxor\n!reval clog forestry\n!reval leaderboard clues hard",3));
        sharing.add(text("Click or right-click the message and choose\nOpen Reval board. A shared clog uses your account\nunless you add a different player's name.",18,BrowserTheme.TEXT,3)); gap(sharing,6);
        sharing.add(text("Use buttons to browse without sending more messages.\nPlayers need the updated Reval plugin to open shares.",17,BrowserTheme.MUTED,2));
        add(row(boards,sharing)); gap(12);
        add(text("Collection Log shortcuts work here too: amox, cox, tob, toa, bandos, wt, gotr.\nViews are cached. Refresh reloads the current view. Monthly boards use the clan's Tallinn calendar month.",18,BrowserTheme.MUTED,2));
    }

    private void gap(int size) { gap(this,size); }
    private static void gap(JPanel panel,int size) { panel.add(Box.createVerticalStrut(size)); }
    private static JPanel card(String title,Color accent) {
        JPanel panel=new JPanel(); panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS)); panel.setBackground(BrowserTheme.PANEL); panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setBorder(BrowserTheme.outline(BrowserTheme.BORDER,12,14));
        JLabel heading=new JLabel(title); heading.setForeground(accent); heading.setFont(BrowserTheme.font(25,true));
        heading.setAlignmentX(LEFT_ALIGNMENT); panel.add(heading); gap(panel,8); return panel;
    }
    private static JPanel row(JPanel left,JPanel right) {
        JPanel row=new JPanel(new GridLayout(1,2,12,0)); row.setOpaque(false); row.add(left); row.add(right);
        row.setAlignmentX(LEFT_ALIGNMENT); row.setMaximumSize(new Dimension(Integer.MAX_VALUE,row.getPreferredSize().height)); return row;
    }
    private static JTextArea code(String commands,int rows) { return text(commands,21,BrowserTheme.GOLD,rows); }
    private static JTextArea text(String value,int size,Color color,int rows) {
        JTextArea text=new JTextArea(value,rows,0); text.setEditable(false); text.setOpaque(false);
        text.setFont(BrowserTheme.font(size,false)); text.setForeground(color); text.setCaretColor(color);
        text.setLineWrap(true); text.setWrapStyleWord(true); text.setAlignmentX(LEFT_ALIGNMENT);
        int height=text.getFontMetrics(text.getFont()).getHeight()*rows+4;
        text.setPreferredSize(new Dimension(200,height)); text.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));
        return text;
    }
    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(1100,600); }
    @Override public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction) { return 30; }
    @Override public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction) { return Math.max(30,visible.height-30); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}

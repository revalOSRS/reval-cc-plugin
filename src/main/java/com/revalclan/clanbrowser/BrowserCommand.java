package com.revalclan.clanbrowser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-game command grammar, independent of the client, network and Swing. */
public final class BrowserCommand {
    private static final List<String> METRICS = Arrays.asList("clog", "pets", "points", "greenlogs", "monthly-clog", "monthly-points", "clues", "combat", "bosses", "xp");
    private static final List<String> TIERS = Arrays.asList("all", "beginner", "easy", "medium", "hard", "elite", "master");
    private static final Pattern TOKENS = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");
    public final String kind;
    public final String category;
    public final String metric;
    public final String boss;
    public final String tier;
    public final String player;

    public BrowserCommand(String kind, String category, String metric, String boss, String tier, String player) {
        this.kind = kind;
        this.category = clean(category);
        this.metric = clean(metric);
        this.boss = clean(boss);
        this.tier = clean(tier);
        this.player = clean(player);
    }

    public static BrowserCommand parse(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = TOKENS.matcher(text == null ? "" : text.trim());
        while (matcher.find()) words.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        if (!words.isEmpty() && words.get(0).replaceFirst("^::", "").equalsIgnoreCase("reval")) words.remove(0);
        if (words.isEmpty()) return new BrowserCommand("help", null, null, null, null, null);
        String kind = words.remove(0).toLowerCase(Locale.ROOT);
        if (kind.equals("help")) return new BrowserCommand("help", null, null, null, null, null);
        if (!Arrays.asList("clog", "luck", "deserved", "dryboard", "leaderboard").contains(kind)) {
            throw new IllegalArgumentException("Use clog, luck, deserved, dryboard or leaderboard. Try ::reval help.");
        }
        if (kind.equals("leaderboard")) {
            String metric = words.isEmpty() ? "clog" : words.remove(0).toLowerCase(Locale.ROOT);
            if (metric.equals("total-xp")) metric = "xp";
            if (!METRICS.contains(metric)) throw new IllegalArgumentException("Unknown statistic. Try ::reval leaderboard clog or ::reval help.");
            String rest = String.join(" ", words);
            if (metric.equals("clues") && !rest.isEmpty() && !TIERS.contains(rest.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Clue tiers: beginner, easy, medium, hard, elite, master or all.");
            if (!rest.isEmpty() && !metric.equals("clues") && !metric.equals("bosses")) throw new IllegalArgumentException("This statistic does not need a category.");
            return new BrowserCommand(kind, null, metric, metric.equals("bosses") ? rest : null, metric.equals("clues") ? rest.toLowerCase(Locale.ROOT) : null, null);
        }
        String player = null;
        int playerAt = -1;
        for (int i = 0; i < words.size(); i++) {
            if (words.get(i).equalsIgnoreCase("--player") || words.get(i).toLowerCase(Locale.ROOT).startsWith("player:")) { playerAt = i; break; }
        }
        if (playerAt >= 0) {
            String first = words.get(playerAt);
            List<String> name = new ArrayList<>();
            if (first.toLowerCase(Locale.ROOT).startsWith("player:") && first.length() > 7) name.add(first.substring(7));
            name.addAll(words.subList(playerAt + 1, words.size()));
            player = String.join(" ", name);
            words = new ArrayList<>(words.subList(0, playerAt));
            if (!kind.equals("clog") || !validPlayer(player)) throw new IllegalArgumentException("Use a valid OSRS name after player: (up to 12 characters).");
        }
        return new BrowserCommand(kind, words.isEmpty() ? "vorkath" : String.join(" ", words), null, null, null, player);
    }

    /** Also accept ::reval clog forestry Valgusruum, resolving the longest known page first. */
    public BrowserCommand resolvePlayerSuffix(List<BrowserModels.Page> pages) {
        if (!kind.equals("clog") || player != null || category == null) return this;
        String needle = normalize(category), best = null;
        List<BrowserModels.Page> candidates = new ArrayList<>(pages);
        BrowserModels.Page allClues = new BrowserModels.Page(); allClues.value = "clues"; allClues.label = "All clue tiers";
        allClues.aliases.addAll(Arrays.asList("clues", "all clues", "all clue tiers")); candidates.add(allClues);
        for (BrowserModels.Page page : candidates) {
            List<String> names = new ArrayList<>(page.aliases);
            names.add(page.value);
            names.add(page.label);
            for (String raw : names) {
                String name = normalize(raw);
                if (needle.equals(name)) return withCategory(page.value);
                if (!name.isEmpty() && needle.startsWith(name + " ") && (best == null || name.length() > best.length())) best = name;
            }
        }
        if (best == null) return this;
        // Match with normalized names, but retain the player's original spelling.
        Matcher original = Pattern.compile("[a-z0-9]+", Pattern.CASE_INSENSITIVE).matcher(category);
        int end = 0;
        for (int i = 0; i < best.split(" ").length && original.find(); i++) end = original.end();
        String tail = category.substring(end).trim();
        if (!validPlayer(tail)) return this;
        return new BrowserCommand(kind, best, metric, boss, tier, tail);
    }

    public BrowserCommand withCategory(String value) { return new BrowserCommand(kind, value, metric, boss, tier, player); }
    public BrowserCommand resolveShared(List<BrowserModels.Page> pages, String sender) {
        BrowserCommand resolved = resolvePlayerSuffix(pages);
        return resolved.kind.equals("clog") && resolved.player == null && validPlayer(sender) ? resolved.withPlayer(sender) : resolved;
    }
    public BrowserCommand withPlayer(String value) { return new BrowserCommand(kind, category, metric, boss, tier, value); }
    public static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
    public static boolean validPlayer(String value) { return value != null && value.matches("[a-zA-Z0-9 _-]{1,12}"); }
    private static String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }

    public Map<String, String> parameters(long accountHash) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("command", kind);
        if (!kind.equals("leaderboard") && category != null) values.put("category", category);
        if (kind.equals("leaderboard") && metric != null) values.put("metric", metric);
        if (kind.equals("leaderboard") && boss != null) values.put("boss", boss);
        if (kind.equals("leaderboard") && tier != null) values.put("tier", tier);
        if (kind.equals("clog") && player != null) values.put("player", player);
        if (kind.equals("clog") && player == null && accountHash != 0) values.put("accountHash", String.valueOf(accountHash));
        return values;
    }
}

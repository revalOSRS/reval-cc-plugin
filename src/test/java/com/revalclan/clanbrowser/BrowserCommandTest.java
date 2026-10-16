package com.revalclan.clanbrowser;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserCommandTest {
    @Test public void switchingModesPreservesCompatibleCategoriesAndReplacesUnsupportedPages() {
        BrowserModels.Page forestry = new BrowserModels.Page(); forestry.category = "Other"; forestry.value = "forestry"; forestry.label = "Forestry";
        BrowserModels.Page vorkath = new BrowserModels.Page(); vorkath.category = "Bosses"; vorkath.value = "vorkath"; vorkath.label = "Vorkath";
        BrowserModels.Page cox = new BrowserModels.Page(); cox.category = "Raids"; cox.value = "chambers-of-xeric"; cox.label = "Chambers of Xeric"; cox.aliases.add("cox");
        java.util.List<BrowserModels.Page> pages = Arrays.asList(forestry, vorkath, cox);
        assertEquals("vorkath", ClanBrowserWindow.categoryForMode("dryboard", "forestry", pages));
        assertEquals("chambers-of-xeric", ClanBrowserWindow.categoryForMode("dryboard", "cox", pages));
        assertEquals("forestry", ClanBrowserWindow.categoryForMode("deserved", "forestry", pages));
        assertEquals("hard-clues", ClanBrowserWindow.categoryForMode("luck", "clues", pages));
        assertEquals("clues", ClanBrowserWindow.categoryForMode("clog", "clues", pages));
    }
    @Test public void acceptsEveryDiscordCommandAndOptionalClueTierOrBoss() {
        for (String metric : Arrays.asList("clog", "pets", "points", "greenlogs", "monthly-clog", "monthly-points", "clues", "combat", "bosses", "xp")) {
            BrowserCommand command = BrowserCommand.parse("::reval leaderboard " + metric);
            assertEquals("leaderboard", command.kind); assertEquals(metric, command.metric);
        }
        assertEquals("hard", BrowserCommand.parse("::reval leaderboard clues HARD").tier);
        assertEquals("Phosani's Nightmare", BrowserCommand.parse("::reval leaderboard bosses Phosani's Nightmare").boss);
        for (String mode : Arrays.asList("clog", "luck", "deserved", "dryboard")) assertEquals("general graardor", BrowserCommand.parse("::reval " + mode + " general graardor").category);
        assertEquals("help", BrowserCommand.parse("::reval help").kind);
        assertEquals("help", BrowserCommand.parse("::reval").kind);
    }

    @Test public void resolvesLongCategoryNamesBeforeAnOptionalPlayerSuffix() {
        BrowserModels.Page page = new BrowserModels.Page(); page.value = "general-graardor"; page.label = "General Graardor";
        page.aliases.addAll(Arrays.asList("General Graardor", "bandos"));
        BrowserCommand command = BrowserCommand.parse("::reval clog general graardor \"Love Yourz\"").resolvePlayerSuffix(Arrays.asList(page));
        assertEquals("general graardor", command.category); assertEquals("Love Yourz", command.player);
        assertNull(BrowserCommand.parse("::reval clog general graardor").resolvePlayerSuffix(Arrays.asList(page)).player);
        assertEquals("Valgusruum", BrowserCommand.parse("::reval clog forestry player:Valgusruum").player);
        assertEquals("Love Yourz", BrowserCommand.parse("::reval clog forestry --player \"Love Yourz\"").player);
        BrowserCommand clues = BrowserCommand.parse("::reval clog clues Phosani55555").resolvePlayerSuffix(Arrays.asList(page));
        assertEquals("clues", clues.category); assertEquals("Phosani55555", clues.player);
    }

    @Test public void preservesSignedAccountHashesAndNeverSendsOneWhenAPlayerWasSelected() {
        assertEquals("-123456789", BrowserCommand.parse("::reval clog forestry").parameters(-123456789L).get("accountHash"));
        assertFalse(BrowserCommand.parse("::reval clog forestry player:Valgusruum").parameters(-123L).containsKey("accountHash"));
        assertFalse(BrowserCommand.parse("::reval dryboard vorkath").parameters(123L).containsKey("accountHash"));
    }

    @Test public void rejectsUnsupportedStatsAndMalformedOptionalArguments() {
        for (String input : Arrays.asList("reval remove", "reval leaderboard bank", "reval leaderboard xp extra", "reval leaderboard clues impossible", "reval luck vorkath --player Bob")) {
            try { BrowserCommand.parse(input); fail(input); } catch (IllegalArgumentException expected) { assertFalse(expected.getMessage().isEmpty()); }
        }
    }

    @Test public void autocompleteUsesAliasesAndRanksExactMatchesFirst() {
        BrowserModels.Page cox = new BrowserModels.Page(); cox.value = "chambers-of-xeric"; cox.label = "Chambers of Xeric"; cox.aliases.add("cox");
        BrowserModels.Page other = new BrowserModels.Page(); other.value = "other"; other.label = "Other";
        assertEquals(cox, SearchPicker.matches(Arrays.asList(other, cox), "cox").get(0));
        assertTrue(SearchPicker.matches(Arrays.asList(other, cox), "unknown").isEmpty());
    }
}

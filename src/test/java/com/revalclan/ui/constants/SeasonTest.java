package com.revalclan.ui.constants;

import java.time.LocalDate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SeasonTest {
    @Test
    public void halloweenRunsFromAugustUntilNovemberEachYear() {
        for (int year : new int[] {2026, 2027, 2028}) {
            assertEquals(Season.NONE, Season.on(LocalDate.of(year, 7, 31)));
            assertEquals(Season.HALLOWEEN, Season.on(LocalDate.of(year, 8, 1)));
            assertEquals(Season.HALLOWEEN, Season.on(LocalDate.of(year, 10, 31)));
            assertEquals(Season.NONE, Season.on(LocalDate.of(year, 11, 1)));
        }
    }

    @Test
    public void christmasRunsForDecemberAndEndsOnNewYearsDay() {
        for (int year : new int[] {2026, 2027, 2028}) {
            assertEquals(Season.NONE, Season.on(LocalDate.of(year, 11, 30)));
            assertEquals(Season.CHRISTMAS, Season.on(LocalDate.of(year, 12, 1)));
            assertEquals(Season.CHRISTMAS, Season.on(LocalDate.of(year, 12, 31)));
            assertEquals(Season.NONE, Season.on(LocalDate.of(year + 1, 1, 1)));
        }
    }

    @Test
    public void previewOverridesTheDateAndInvalidValuesUseTodaysSeason() {
        String previous = System.getProperty("reval.season");
        try {
            System.setProperty("reval.season", " Christmas ");
            assertEquals(Season.CHRISTMAS, Season.current());
            System.setProperty("reval.season", "halloween");
            assertEquals(Season.HALLOWEEN, Season.current());
            System.setProperty("reval.season", "none");
            assertEquals(Season.NONE, Season.current());
            System.setProperty("reval.season", "invalid");
            assertEquals(Season.on(LocalDate.now()), Season.current());
            System.clearProperty("reval.season");
            assertEquals(Season.on(LocalDate.now()), Season.current());
        } finally {
            if (previous == null) System.clearProperty("reval.season");
            else System.setProperty("reval.season", previous);
        }
    }
}

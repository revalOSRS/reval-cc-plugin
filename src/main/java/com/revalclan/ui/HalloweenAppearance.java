package com.revalclan.ui;

import java.time.LocalDate;
import java.time.Month;

/** Automatic expires with October; explicit choices remain under the player's control. */
public enum HalloweenAppearance {
    AUTOMATIC("Automatic"),
    ON("On"),
    OFF("Off");

    private final String label;

    HalloweenAppearance(String label) {
        this.label = label;
    }

    public boolean isActive(LocalDate date) {
        return this == ON || (this == AUTOMATIC && date.getMonth() == Month.OCTOBER);
    }

    @Override
    public String toString() {
        return label;
    }
}

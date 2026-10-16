package com.revalclan.clanbrowser;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/** Read-only browser DTOs. Numeric scores remain longs, including multi-billion XP totals. */
public final class BrowserModels {
    private BrowserModels() {}

    public static class Choice {
        public String value;
        public String label;
        public List<String> aliases = new ArrayList<>();
        @Override public String toString() { return label != null ? label : value; }
    }

    public static class Page extends Choice {
        public String category;
        public String subcategory;
    }

    public static class Catalogue {
        public List<Page> pages = new ArrayList<>();
        public List<Choice> metrics = new ArrayList<>();
        public List<Choice> bosses = new ArrayList<>();
        public List<String> clueTiers = new ArrayList<>();
    }

    public static class View {
        public String kind;
        public String title;
        public String subtitle;
        public String valueLabel;
        public String emptyLabel;
        public String generatedAt;
        public Integer completedTotal;
        public boolean partial;
        public Page page;
        public Account account;
        public JsonElement metric;
        public Metric overviewMetric;
        public List<Group> groups = new ArrayList<>();
        public List<Page> completedPages;
        public List<Panel> panels = new ArrayList<>();
        public List<Section> sections = new ArrayList<>();
        public List<Row> rows = new ArrayList<>();
    }

    public static class Account {
        public int id;
        @SerializedName(value = "osrs_nickname", alternate = {"osrsNickname"}) public String name;
        @SerializedName(value = "last_synced_at", alternate = {"lastSyncedAt"}) public String lastSyncedAt;
        public Long obtained;
        public Integer total;
    }

    public static class Item {
        public Integer id;
        public Integer canonicalId;
        public String name;
        public Long quantity;
    }

    public static class Metric {
        public String key;
        public String kind;
        public Long value;
        public Long current;
        public boolean estimated;
        public Metric completion;
    }

    public static class LuckRank {
        public int rank;
        public int total;
        public boolean estimated;
        public boolean valueEstimated;
    }

    public static class Group {
        public String category;
        public String subcategory;
        public boolean complete;
        public List<Item> items = new ArrayList<>();
        public Metric metric;
        public LuckRank rank;
    }

    public static class Row {
        public int accountId;
        public String player;
        public Integer rank;
        public Long value;
        public boolean estimated;
        public String status;
        public Integer obtained;
        public Integer totalItems;
        public Long current;
        public Long last;
        public String item;
        @Override public String toString() {
            return (rank == null ? "" : "#" + rank + " ") + player + " " +
                ("progress".equals(status) ? obtained + " / " + totalItems : value == null ? "Greenlogged" : (estimated ? "~" : "") + value);
        }
    }

    public static class Panel {
        public String key;
        public String label;
        public String color;
        public int total;
        public List<Row> rows = new ArrayList<>();
    }

    public static class Section {
        public String label;
        public String tier;
        public List<Panel> panels = new ArrayList<>();
    }
}

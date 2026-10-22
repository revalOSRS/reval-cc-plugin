package com.revalclan.api.competitions;

import com.revalclan.api.common.PublicApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/** Public race view shared with the homepage; an upcoming race may still be a draft. */
@Data
@EqualsAndHashCode(callSuper = true)
public class FeaturedRaceResponse extends PublicApiResponse {
    private RaceData data;

    @Data
    public static class RaceData {
        private Race race;
    }

    @Data
    public static class Race {
        private String id;
        private String name;
        private String imageUrl;
        private String state;
        private String plannedStartDate;
        private String startDate;
        private String endDate;
        private String timeZone;
        private List<Objective> objectives;
        private Kc kc;
    }
    @Data
    public static class Objective {
        private String kind;
        private String title;
        private Integer iconItemId;
        private String imageUrl;
        private int points;
        private boolean clanPartyOnly;
        private Winner winner;
    }
    @Data
    public static class Winner {
        private String name;
        private List<String> members;
    }

    @Data
    public static class Kc {
        private List<Integer> rewards;
        private List<Standing> leaderboard;
    }
    @Data
    public static class Standing {
        private String name;
        private int rank;
        private int value;
        private int points;
    }
}

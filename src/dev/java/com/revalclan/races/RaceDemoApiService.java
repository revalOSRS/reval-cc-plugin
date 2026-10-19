package com.revalclan.races;

import com.google.gson.Gson;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.competitions.FeaturedRaceResponse;
import okhttp3.OkHttpClient;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Read-only, dev-only sample results layered over the real event configuration. */
final class RaceDemoApiService extends RevalApiService {
    private final RevalApiService liveApi;
    private final Gson gson;

    RaceDemoApiService(RevalApiService liveApi, OkHttpClient httpClient, Gson gson) {
        super(httpClient, gson);
        this.liveApi = liveApi;
        this.gson = gson;
    }

    @Override
    public void fetchFeaturedRace(Consumer<FeaturedRaceResponse> onSuccess, Consumer<Exception> onError) {
        liveApi.fetchFeaturedRace(response -> {
            // Clone the response so sample data cannot affect another view or a shared cache.
            FeaturedRaceResponse sample = gson.fromJson(gson.toJson(response), FeaturedRaceResponse.class);
            FeaturedRaceResponse.Race race = sample.getData() == null ? null : sample.getData().getRace();
            if (race != null) populate(race);
            onSuccess.accept(sample);
        }, onError);
    }

    @Override
    public void fetchRaceArtwork(String imageUrl, Consumer<BufferedImage> onSuccess, Consumer<Exception> onError) {
        if (!"sample:archive".equals(imageUrl)) {
            super.fetchRaceArtwork(imageUrl, onSuccess, onError);
            return;
        }
        try {
            onSuccess.accept(ImageIO.read(RaceDemoApiService.class.getResource("/com/revalclan/races/archive.png")));
        } catch (IOException error) { onError.accept(error); }
    }

    private static void populate(FeaturedRaceResponse.Race race) {
        race.setState("active");
        race.setImageUrl(Boolean.getBoolean("reval.raceNoImage") ? null : "sample:archive");
        if (race.getObjectives() != null && race.getObjectives().stream().noneMatch(objective -> "completion".equals(objective.getKind()))) {
            FeaturedRaceResponse.Objective clear = new FeaturedRaceResponse.Objective();
            clear.setId("sample-first-clear"); clear.setKind("completion"); clear.setTitle("First raid KC");
            clear.setPoints(100); clear.setClanPartyOnly(true);
            race.getObjectives().add(0, clear);
        }
        if (race.getObjectives() != null) {
            for (FeaturedRaceResponse.Objective objective : race.getObjectives()) {
                FeaturedRaceResponse.Winner winner = new FeaturedRaceResponse.Winner();
                winner.setName("pet".equals(objective.getKind()) ? "Arctic Vaggy"
                    : "purple".equals(objective.getKind()) ? "Kahur111" : "Love Yourz");
                if (objective.isClanPartyOnly()) {
                    winner.setMembers(List.of("Arctic Vaggy", "Love Yourz", "Kahur111", "PhosaniSpoon"));
                    winner.setName(String.join(", ", winner.getMembers()));
                }
                objective.setWinner(winner);
            }
        }
        if (race.getKc() == null) return;
        race.getKc().setState("active");
        String[] names = {"PhosaniSpoon", "Arctic Vaggy", "Love Yourz", "Kahur111", "Kits", "Sharick",
            "Road Vulture", "Jurlus", "Murt15", "CH33R YP", "Loot Goblin", "Kask"};
        int[] kills = {128, 116, 109, 97, 88, 76, 64, 52, 41, 35, 28, 19};
        List<Integer> rewards = race.getKc().getRewards();
        List<FeaturedRaceResponse.Standing> standings = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            FeaturedRaceResponse.Standing player = new FeaturedRaceResponse.Standing();
            player.setRank(i + 1);
            player.setName(names[i]);
            player.setValue(kills[i]);
            player.setPoints(rewards != null && i < rewards.size() ? rewards.get(i) : 0);
            standings.add(player);
        }
        race.getKc().setLeaderboard(standings);
    }
}

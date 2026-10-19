package com.revalclan.races;

import com.google.gson.Gson;
import com.revalclan.api.RevalApiService;
import com.revalclan.api.competitions.FeaturedRaceResponse;
import okhttp3.OkHttpClient;

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

    private static void populate(FeaturedRaceResponse.Race race) {
        race.setState("active");
        if (race.getObjectives() != null) {
            for (FeaturedRaceResponse.Objective objective : race.getObjectives()) {
                FeaturedRaceResponse.Winner winner = new FeaturedRaceResponse.Winner();
                winner.setName("pet".equals(objective.getKind()) ? "Arctic Vaggy"
                    : "purple".equals(objective.getKind()) ? "Kahur111" : "Love Yourz");
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

package com.revalclan.ui.leaguesbingo;

import com.revalclan.util.PluginVersion;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Loads tile icons from the OSRS wiki ({@code /images/<name>_detail.png},
 * falling back to {@code /images/<name>.png}) and keeps them in memory for
 * the rest of the client session. Callbacks always run on the Swing thread.
 */
@Slf4j
public class WikiIconCache {
	private static final String WIKI_IMAGES = "https://oldschool.runescape.wiki/images/";

	private final OkHttpClient http;
	private final Map<String, BufferedImage> loaded = new HashMap<>();
	private final Set<String> failed = new HashSet<>();
	private final Map<String, List<Consumer<BufferedImage>>> pending = new HashMap<>();

	public WikiIconCache(OkHttpClient http) {
		this.http = http;
	}

	/** Already-loaded image, or null when it has not arrived (or failed). */
	public synchronized BufferedImage peek(String iconName) {
		return iconName == null ? null : loaded.get(iconName);
	}

	public void load(String iconName, Consumer<BufferedImage> onLoaded) {
		if (iconName == null || iconName.trim().isEmpty()) return;
		String key = iconName.trim().replace(' ', '_');

		synchronized (this) {
			BufferedImage cached = loaded.get(key);
			if (cached != null) {
				SwingUtilities.invokeLater(() -> onLoaded.accept(cached));
				return;
			}
			if (failed.contains(key)) return;
			List<Consumer<BufferedImage>> waiters = pending.get(key);
			if (waiters != null) {
				waiters.add(onLoaded);
				return;
			}
			waiters = new ArrayList<>();
			waiters.add(onLoaded);
			pending.put(key, waiters);
		}

		fetch(key, key + "_detail.png", true);
	}

	private void fetch(String key, String fileName, boolean allowFallback) {
		HttpUrl base = HttpUrl.parse(WIKI_IMAGES);
		if (base == null) {
			finish(key, null);
			return;
		}
		HttpUrl url = base.newBuilder().addPathSegment(fileName).build();
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", PluginVersion.userAgent())
			.get()
			.build();

		http.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.debug("Wiki icon {} failed: {}", fileName, e.getMessage());
				finish(key, null);
			}

			@Override
			public void onResponse(Call call, Response response) {
				try (Response r = response) {
					if (!r.isSuccessful() || r.body() == null) {
						if (allowFallback) {
							fetch(key, key + ".png", false);
						} else {
							finish(key, null);
						}
						return;
					}
					try (InputStream in = r.body().byteStream()) {
						finish(key, ImageIO.read(in));
					}
				} catch (IOException e) {
					log.debug("Wiki icon {} unreadable: {}", fileName, e.getMessage());
					finish(key, null);
				}
			}
		});
	}

	private void finish(String key, BufferedImage image) {
		List<Consumer<BufferedImage>> waiters;
		synchronized (this) {
			if (image != null) {
				loaded.put(key, image);
			} else {
				failed.add(key);
			}
			waiters = pending.remove(key);
		}
		if (image == null || waiters == null) return;
		SwingUtilities.invokeLater(() -> {
			for (Consumer<BufferedImage> w : waiters) w.accept(image);
		});
	}
}

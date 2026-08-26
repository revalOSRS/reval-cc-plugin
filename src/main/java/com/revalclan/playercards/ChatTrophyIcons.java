package com.revalclan.playercards;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedSprite;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Chat icons for the profile commands: the game's speedrun trophy sprites
 * (gold/silver/bronze) scaled to chat size, plus a crisp pixel star.
 * Registered as mod icons once after login.
 */
@Singleton
public class ChatTrophyIcons {
	private static final int ICON_HEIGHT = 14;
	private static final Color STAR_GOLD = new Color(0xFFC83C);
	private static final Color STAR_DARK = new Color(0x8A6D25);

	/** Gold fill, dark outline; drawn without antialiasing so it stays crisp. */
	private static final String[] STAR = {
		".....d.....",
		"....dgd....",
		"....dgd....",
		"dddddgddddd",
		".dgggggggd.",
		"..dgggggd..",
		"..dgggggd..",
		".dggd.dggd.",
		".dd.....dd.",
	};

	private final Client client;
	private final ClientThread clientThread;
	private final SpriteManager spriteManager;

	private volatile int starIdx = -1;
	private volatile int goldIdx = -1;
	private volatile int silverIdx = -1;
	private volatile int bronzeIdx = -1;
	private volatile boolean loading;

	@Inject
	public ChatTrophyIcons(Client client, ClientThread clientThread, SpriteManager spriteManager) {
		this.client = client;
		this.clientThread = clientThread;
		this.spriteManager = spriteManager;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			loadIcons();
		}
	}

	public void startUp() {
		if (client.getGameState() == GameState.LOGGED_IN) {
			loadIcons();
		}
	}

	/** Empty string until icons are registered. */
	public String star() {
		return tag(starIdx);
	}

	public String goldTrophy() {
		return tag(goldIdx);
	}

	public String silverTrophy() {
		return tag(silverIdx);
	}

	public String bronzeTrophy() {
		return tag(bronzeIdx);
	}

	private static String tag(int index) {
		return index >= 0 ? "<img=" + index + ">" : "";
	}

	private void loadIcons() {
		if (starIdx != -1 || loading) {
			return;
		}
		loading = true;
		BufferedImage[] trophies = new BufferedImage[3];
		int[] spriteIds = {SpriteID.SpeedrunTrophies._2, SpriteID.SpeedrunTrophies._1, SpriteID.SpeedrunTrophies._0};
		int[] loaded = {0};
		for (int i = 0; i < spriteIds.length; i++) {
			final int slot = i;
			spriteManager.getSpriteAsync(spriteIds[i], 0, sprite -> {
				trophies[slot] = sprite;
				if (++loaded[0] == spriteIds.length) {
					clientThread.invoke(() -> register(trophies));
				}
			});
		}
	}

	private void register(BufferedImage[] trophies) {
		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons == null || starIdx != -1) {
			return;
		}
		BufferedImage[] images = {
			starImage(),
			fitToChat(trophies[0]),
			fitToChat(trophies[1]),
			fitToChat(trophies[2]),
		};
		IndexedSprite[] newIcons = Arrays.copyOf(modIcons, modIcons.length + images.length);
		for (int i = 0; i < images.length; i++) {
			newIcons[modIcons.length + i] = ImageUtil.getImageIndexedSprite(images[i], client);
		}
		client.setModIcons(newIcons);
		starIdx = modIcons.length;
		goldIdx = modIcons.length + 1;
		silverIdx = modIcons.length + 2;
		bronzeIdx = modIcons.length + 3;
	}

	private static BufferedImage fitToChat(BufferedImage sprite) {
		int width = Math.max(1, Math.round(sprite.getWidth() * (float) ICON_HEIGHT / sprite.getHeight()));
		return ImageUtil.resizeImage(sprite, width, ICON_HEIGHT);
	}

	private static BufferedImage starImage() {
		int h = STAR.length;
		int w = STAR[0].length();
		BufferedImage image = new BufferedImage(w, h + 2, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				char c = STAR[y].charAt(x);
				if (c == 'g') {
					image.setRGB(x, y + 1, STAR_GOLD.getRGB());
				} else if (c == 'd') {
					image.setRGB(x, y + 1, STAR_DARK.getRGB());
				}
			}
		}
		return image;
	}
}

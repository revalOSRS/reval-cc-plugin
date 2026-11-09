package com.revalclan;

import com.google.inject.Injector;
import com.revalclan.api.RevalApiService;
import com.revalclan.clanbrowser.ClanBrowserManager;
import com.revalclan.ui.RevalPanel;
import com.revalclan.ui.constants.PanelTheme;
import com.revalclan.ui.constants.Season;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.ui.leaguesbingo.LeaguesBingoPanel;
import com.revalclan.util.RequestScope;
import com.revalclan.util.UIAssetLoader;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PanelThemeTest {
	@Mock private Client client;
	@Mock private RevalClanConfig config;
	@Mock private RevalApiService api;
	@Mock private UIAssetLoader assets;
	@Mock private ItemManager items;
	@Mock private SpriteManager sprites;
	@Mock private ClientToolbar toolbar;
	@Mock private ClanBrowserManager browser;
	@Mock private Injector injector;
	@InjectMocks private RevalClanPlugin plugin;
	private String previousPreview;
	private Season previousSeason;

	@Before public void previewChristmas() {
		previousPreview = System.getProperty("reval.season");
		previousSeason = UIConstants.season();
		System.setProperty("reval.season", "christmas");
	}

	@After public void cleanUp() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				RevalPanel panel = (RevalPanel) field("revalPanel");
				if (panel != null) panel.shutDown();
			} catch (Exception e) { throw new RuntimeException(e); }
			UIConstants.setSeason(previousSeason);
		});
		if (previousPreview == null) System.clearProperty("reval.season");
		else System.setProperty("reval.season", previousPreview);
	}

	@Test public void themeSwitchRebuildsPanelAndToolbarAndKeepsTab() throws Exception {
		assertEquals(PanelTheme.SEASONAL, new RevalClanConfig() {}.theme());
		when(config.theme()).thenReturn(PanelTheme.SEASONAL);
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(assets.getImage("reval.png")).thenReturn(new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB));
		when(injector.getInstance(LeaguesBingoPanel.class)).thenAnswer(ignored ->
			new LeaguesBingoPanel(api, client, items, sprites));
		SwingUtilities.invokeAndWait(() -> {
			try {
				Method build = RevalClanPlugin.class.getDeclaredMethod("buildSidepanel");
				build.setAccessible(true);
				build.invoke(plugin);
			} catch (Exception e) { throw new RuntimeException(e); }
		});
		RevalPanel initial = (RevalPanel) field("revalPanel");
		NavigationButton initialButton = (NavigationButton) field("navButton");
		SwingUtilities.invokeAndWait(() -> initial.showTab("ACHIEVEMENTS"));
		assertEquals(Season.CHRISTMAS, UIConstants.season());

		when(config.theme()).thenReturn(PanelTheme.REGULAR);
		changeTheme();
		RevalPanel regular = (RevalPanel) field("revalPanel");
		assertNotSame(initial, regular);
		assertEquals(Season.NONE, UIConstants.season());
		assertEquals(UIConstants.BACKGROUND, regular.getBackground());
		assertEquals("ACHIEVEMENTS", regular.getSelectedTab());
		NavigationButton regularButton = (NavigationButton) field("navButton");
		assertEquals("Reval Clan", regularButton.getTooltip());
		verify(toolbar).removeNavigation(initialButton);
		verify(toolbar).addNavigation(regularButton);
		verify(toolbar, never()).openPanel(any());

		changeTheme();
		assertSame("Unchanged appearance must not rebuild or reload", regular, field("revalPanel"));
		when(config.theme()).thenReturn(PanelTheme.SEASONAL);
		changeTheme();
		RevalPanel seasonal = (RevalPanel) field("revalPanel");
		assertNotSame(regular, seasonal);
		assertEquals(Season.CHRISTMAS, UIConstants.season());
		assertEquals(UIConstants.BACKGROUND, seasonal.getBackground());
		assertEquals("ACHIEVEMENTS", seasonal.getSelectedTab());
		assertEquals(Season.CHRISTMAS.tooltip, ((NavigationButton) field("navButton")).getTooltip());
		verifyNoInteractions(api);
	}

	@Test public void themeChangesBeforeInitializationOrAfterShutdownDoNothing() throws Exception {
		changeTheme();
		((RequestScope) field("lifecycle")).cancel();
		changeTheme();
		assertNull(field("revalPanel"));
		verifyNoInteractions(toolbar, config);
	}

	private void changeTheme() throws Exception {
		ConfigChanged event = new ConfigChanged();
		event.setGroup("revalclan");
		event.setKey("theme");
		plugin.onConfigChanged(event);
		SwingUtilities.invokeAndWait(() -> {});
	}

	private Object field(String name) throws Exception {
		Field field = RevalClanPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(plugin);
	}
}

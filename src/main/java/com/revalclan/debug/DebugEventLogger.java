package com.revalclan.debug;

import com.google.gson.Gson;
import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Test-mode firehose: streams raw RuneLite events to a JSONL file in
 * ~/.runelite/reval-debug/ so event ordering, timing and payloads can be
 * studied offline. One line per event, buffered and flushed once per tick.
 *
 * Enabled via the debug config section (or ::rlog on/off at runtime).
 */
@Slf4j
@Singleton
public class DebugEventLogger {
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	/** Script ids worth streaming — everything else is far too noisy. */
	private static final Set<Integer> LOGGED_SCRIPTS = Set.of(
		4100,  // collection log item drawn
		2240,  // collection log search
		7812   // collection log burger menu draw
	);

	@Inject private Client client;
	@Inject private Gson gson;
	@Inject private RevalClanConfig config;
	@Inject private DebugDataDumper dumper;
	@Inject private ScheduledExecutorService executor;

	private final List<String> buffer = new ArrayList<>();
	private File currentFile;

	/** Runtime override from ::rlog — null means "follow the config toggle". */
	private volatile Boolean runtimeOverride;

	public boolean isEnabled() {
		if (!config.debugMode()) return false;
		return runtimeOverride != null ? runtimeOverride : config.debugEventStream();
	}

	public void setRuntimeOverride(boolean enabled) {
		runtimeOverride = enabled;
		if (enabled) {
			currentFile = null; // start a fresh file
		} else {
			flush();
		}
	}

	public File getCurrentFile() {
		return currentFile;
	}

	// ─── Event subscriptions ───────────────────────────────────────────

	@Subscribe
	public void onGameTick(GameTick event) {
		if (!isEnabled()) return;
		flush();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("GameStateChanged");
		e.put("state", String.valueOf(event.getGameState()));
		add(e);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("VarbitChanged");
		e.put("varpId", event.getVarpId());
		e.put("varbitId", event.getVarbitId());
		e.put("value", event.getValue());
		add(e);
	}

	@Subscribe
	public void onStatChanged(StatChanged event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("StatChanged");
		e.put("skill", event.getSkill().getName());
		e.put("level", event.getLevel());
		e.put("boostedLevel", event.getBoostedLevel());
		e.put("xp", event.getXp());
		add(e);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("ChatMessage");
		e.put("chatType", String.valueOf(event.getType()));
		e.put("name", event.getName());
		e.put("message", event.getMessage()); // raw, including <col> tags
		e.put("sender", event.getSender());
		add(e);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("WidgetLoaded");
		e.put("groupId", event.getGroupId());
		add(e);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("WidgetClosed");
		e.put("groupId", event.getGroupId());
		add(e);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event) {
		if (!isEnabled() || !LOGGED_SCRIPTS.contains(event.getScriptId())) return;
		Map<String, Object> e = base("ScriptPreFired");
		e.put("scriptId", event.getScriptId());
		try {
			Object[] args = event.getScriptEvent() != null ? event.getScriptEvent().getArguments() : null;
			if (args != null) {
				List<String> rendered = new ArrayList<>(args.length);
				for (Object arg : args) {
					rendered.add(String.valueOf(arg));
				}
				e.put("args", rendered);
			}
		} catch (Exception ignored) {}
		add(e);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("ItemContainerChanged");
		e.put("containerId", event.getContainerId());
		try {
			e.put("size", event.getItemContainer().size());
			e.put("nonEmptySlots", event.getItemContainer().count());
		} catch (Exception ignored) {}
		add(e);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("ActorDeath");
		e.put("actorName", event.getActor() != null ? event.getActor().getName() : null);
		e.put("isNpc", event.getActor() instanceof NPC);
		if (event.getActor() instanceof NPC) {
			e.put("npcId", ((NPC) event.getActor()).getId());
		}
		add(e);
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("CommandExecuted");
		e.put("command", event.getCommand());
		e.put("args", event.getArguments());
		add(e);
	}

	// ─── Loot events (all four variants, raw) ──────────────────────────

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("NpcLootReceived");
		e.put("npcId", event.getNpc().getId());
		e.put("npcName", event.getNpc().getName());
		e.put("items", renderItems(event.getItems()));
		add(e);
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("PlayerLootReceived");
		e.put("playerName", event.getPlayer().getName());
		e.put("items", renderItems(event.getItems()));
		add(e);
	}

	@Subscribe
	public void onLootReceived(LootReceived event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("LootReceived");
		e.put("lootType", String.valueOf(event.getType()));
		e.put("name", event.getName());
		e.put("combatLevel", event.getCombatLevel());
		e.put("items", renderItems(event.getItems()));
		add(e);
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event) {
		if (!isEnabled()) return;
		Map<String, Object> e = base("ServerNpcLoot");
		e.put("npcId", event.getComposition().getId());
		e.put("npcName", event.getComposition().getName());
		e.put("items", renderItems(event.getItems()));
		add(e);
	}

	// ─── Internals ─────────────────────────────────────────────────────

	private List<Map<String, Object>> renderItems(Collection<ItemStack> items) {
		List<Map<String, Object>> out = new ArrayList<>();
		if (items == null) return out;
		for (ItemStack item : items) {
			Map<String, Object> i = new LinkedHashMap<>();
			i.put("id", item.getId());
			i.put("quantity", item.getQuantity());
			out.add(i);
		}
		return out;
	}

	private Map<String, Object> base(String type) {
		Map<String, Object> e = new LinkedHashMap<>();
		e.put("event", type);
		e.put("tick", client.getTickCount());
		e.put("ms", System.currentTimeMillis());
		return e;
	}

	private void add(Map<String, Object> event) {
		String line;
		try {
			line = gson.toJson(event);
		} catch (Exception e) {
			return;
		}
		synchronized (buffer) {
			buffer.add(line);
		}
	}

	/** Move buffered lines to disk off the client thread. */
	public void flush() {
		List<String> lines;
		synchronized (buffer) {
			if (buffer.isEmpty()) return;
			lines = new ArrayList<>(buffer);
			buffer.clear();
		}

		if (currentFile == null) {
			currentFile = new File(dumper.getDebugDir(),
				"events-" + LocalDateTime.now().format(FILE_STAMP) + ".jsonl");
		}
		File target = currentFile;

		executor.execute(() -> {
			try {
				File dir = target.getParentFile();
				if (!dir.exists() && !dir.mkdirs()) return;
				StringBuilder sb = new StringBuilder();
				for (String line : lines) {
					sb.append(line).append('\n');
				}
				Files.write(target.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (Exception e) {
				log.warn("Failed to flush debug event log: {}", e.getMessage());
			}
		});
	}
}

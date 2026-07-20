package com.revalclan.debug;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Chat commands for the test-mode data dumps. All commands require the debug
 * config toggle and run on the client thread (CommandExecuted is dispatched
 * there already).
 *
 *   ::rdump            — list available dumps
 *   ::rdump all        — dump every domain to its own file
 *   ::rdump <domain>   — sync|player|quests|diaries|ca|clog|clograw|kc|varps|varcs|inv|equip|bank
 *   ::rdump enum 2102  — probe an arbitrary cache enum
 *   ::rdump struct 471 — probe an arbitrary cache struct (all params)
 *   ::rdump widgets 621— dump a widget group tree
 *   ::rlog on|off      — toggle the raw event stream at runtime
 */
@Slf4j
@Singleton
public class DebugCommandHandler {
	@Inject private RevalClanConfig config;
	@Inject private DebugDataDumper dumper;
	@Inject private DebugEventLogger eventLogger;

	@Subscribe
	public void onCommandExecuted(CommandExecuted event) {
		if (!config.debugMode()) return;

		String command = event.getCommand().toLowerCase();
		String[] args = event.getArguments();

		if ("rlog".equals(command)) {
			handleLogToggle(args);
			return;
		}

		if (!"rdump".equals(command)) return;

		try {
			handleDump(args);
		} catch (Exception e) {
			dumper.chat("dump failed: " + e.getMessage());
			log.warn("Debug dump failed", e);
		}
	}

	private void handleLogToggle(String[] args) {
		if (args.length == 0) {
			dumper.chat("event stream is " + (eventLogger.isEnabled() ? "ON" : "OFF") + ". Use ::rlog on|off");
			return;
		}
		boolean on = "on".equalsIgnoreCase(args[0]);
		eventLogger.setRuntimeOverride(on);
		dumper.chat("event stream " + (on ? "ON — writing to " + dumper.getDebugDir().getAbsolutePath() : "OFF"));
	}

	private void handleDump(String[] args) {
		if (args.length == 0) {
			dumper.chat("usage: ::rdump all|sync|player|quests|diaries|ca|clog|clograw|kc|varps|varcs|inv|equip|bank");
			dumper.chat("probes: ::rdump enum <id> | struct <id> | widgets <groupId>");
			return;
		}

		String what = args[0].toLowerCase();
		switch (what) {
			case "all":
				dumper.dumpAll();
				return;
			case "sync":
				dumper.dumpSyncPayload();
				break;
			case "player":
			case "skills":
				dumper.dumpPlayerRaw();
				break;
			case "quests":
				dumper.dumpQuestsRaw();
				break;
			case "diaries":
				dumper.dumpDiariesRaw();
				break;
			case "ca":
				dumper.dumpCombatAchievementsRaw();
				break;
			case "clog":
				dumper.dumpClogMapped();
				break;
			case "clograw":
				dumper.dumpClogRawStructure();
				break;
			case "kc":
				dumper.dumpKcs();
				break;
			case "varps":
				dumper.dumpVarps();
				break;
			case "varcs":
				dumper.dumpVarcs();
				break;
			case "inv":
				dumper.dumpItemContainer(93, "inventory");
				break;
			case "equip":
				dumper.dumpItemContainer(94, "equipment");
				break;
			case "bank":
				dumper.dumpItemContainer(95, "bank");
				break;
			case "enum":
				dumper.dumpEnumById(requireIntArg(args, "enum"));
				break;
			case "struct":
				dumper.dumpStructById(requireIntArg(args, "struct"));
				break;
			case "widgets":
				dumper.dumpWidgets(requireIntArg(args, "widgets"));
				break;
			default:
				dumper.chat("unknown dump '" + what + "'. Use ::rdump for the list.");
				return;
		}
		dumper.chat("wrote " + what + " dump to " + dumper.getDebugDir().getAbsolutePath());
	}

	private int requireIntArg(String[] args, String name) {
		if (args.length < 2) {
			throw new IllegalArgumentException("::rdump " + name + " needs an id");
		}
		return Integer.parseInt(args[1]);
	}
}

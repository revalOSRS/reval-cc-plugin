package com.revalclan;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RevalClanDevClient
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RevalClanPlugin.class);
		RuneLite.main(args);
	}
}



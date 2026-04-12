package com.adeptum.questai;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Plugin extends JavaPlugin implements Listener {
	private final List<SubPlugin> plugins = new ArrayList<>();

	@Override
	public void onEnable() {
		plugins.add(new AutoVillagerPlugin(this));
		plugins.add(new RandomQuestPlugin(this));

		PluginManager pm = getServer().getPluginManager();

		plugins.forEach(p -> {
			pm.registerEvents(p, this);
			p.onEnable();
		});
	}

	@Override
	public void onDisable() {
		HandlerList.unregisterAll((Listener) this);

		plugins.forEach(p -> {
			p.onDisable();
		});
	}
}

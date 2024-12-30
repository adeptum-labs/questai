package com.adeptum.questai;

import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public interface SubPlugin extends Listener {
	void onEnable();
	void onDisable();
}

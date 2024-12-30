package com.adeptum.questai.event;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerEvent {
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Bukkit.broadcastMessage(event.getPlayer().getDisplayName());
	}
}

package com.adeptum.questai.Events;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.Bukkit;

public class helloWorldEvent implements Listener  {
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.broadcastMessage(event.getPlayer().getDisplayName());
    }
}

/*
 * Copyright (C) 2026 Adeptum AB, org nr. 559494-1824
 *
 * This file is part of QuestAI.
 *
 * QuestAI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * QuestAI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with QuestAI. If not, see
 * <https://www.gnu.org/licenses/>.
 */

package com.adeptum.questai;

import com.adeptum.questai.dialogue.ConversationManager;
import com.adeptum.questai.service.QuestGenerationService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spawns wandering peasant NPCs that roam the world and offer quests.
 * Uses Wandering Trader entities for natural roaming AI and llamas.
 */
public class WanderingPeasantPlugin implements SubPlugin {

	private static final long SPAWN_INTERVAL_TICKS = 12000L;
	private static final double SPAWN_CHANCE = 0.2;
	private static final double QUEST_CHANCE = 0.5;
	private static final long DESPAWN_TICKS = 36000L; // 30 minutes
	private static final int SPAWN_MIN_DISTANCE = 30;
	private static final int SPAWN_MAX_DISTANCE = 60;

	private final JavaPlugin plugin;
	private final ConversationManager conversationManager;
	private final QuestGenerationService questService;
	private final OpenAiChatModel chatModel;

	private final Set<UUID> peasantIds = ConcurrentHashMap.newKeySet();
	private final Map<UUID, String> peasantNames = new ConcurrentHashMap<>();

	public WanderingPeasantPlugin(JavaPlugin plugin,
		ConversationManager conversationManager,
		QuestGenerationService questService,
		OpenAiChatModel chatModel) {

		super();
		this.plugin = plugin;
		this.conversationManager = conversationManager;
		this.questService = questService;
		this.chatModel = chatModel;
	}

	@Override
	public void onEnable() {
		plugin.getLogger().info("[WanderingPeasantPlugin] Enabled.");

		Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			for (Player player : Bukkit.getOnlinePlayers()) {
				trySpawnPeasant(player);
			}
		}, SPAWN_INTERVAL_TICKS, SPAWN_INTERVAL_TICKS);
	}

	@Override
	public void onDisable() {
		for (UUID id : peasantIds) {
			final Entity entity = Bukkit.getEntity(id);
			if (entity != null) {
				entity.remove();
			}
		}
		peasantIds.clear();
		peasantNames.clear();
		plugin.getLogger().info("[WanderingPeasantPlugin] Disabled.");
	}

	// ------------------- Spawning -------------------

	private void trySpawnPeasant(Player player) {
		if (!player.isOnline()) {
			return;
		}

		// Check if there's already a peasant nearby
		final boolean nearbyPeasant = player.getWorld()
			.getNearbyEntities(player.getLocation(), 100, 100, 100)
			.stream()
			.anyMatch(e -> peasantIds.contains(e.getUniqueId()));
		if (nearbyPeasant) {
			return;
		}

		if (Math.random() > SPAWN_CHANCE) {
			return;
		}

		final World world = player.getWorld();
		if (world.getEnvironment() != World.Environment.NORMAL) {
			return;
		}

		final Location spawnLoc = getRandomLocationNear(player.getLocation());
		if (spawnLoc == null) {
			return;
		}

		final WanderingTrader trader = (WanderingTrader)
			world.spawnEntity(spawnLoc, EntityType.WANDERING_TRADER);
		trader.setPersistent(false);
		trader.setCustomNameVisible(true);

		final UUID traderId = trader.getUniqueId();
		peasantIds.add(traderId);

		// Schedule despawn
		Bukkit.getScheduler().runTaskLater(plugin, () -> despawn(traderId),
			DESPAWN_TICKS);

		// Generate name async
		generatePeasantName(trader);
	}

	private Location getRandomLocationNear(Location center) {
		final double angle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
		final double distance = ThreadLocalRandom.current()
			.nextDouble(SPAWN_MIN_DISTANCE, SPAWN_MAX_DISTANCE);

		final int blockX = (int) Math.floor(center.getX() + distance * Math.cos(angle));
		final int blockZ = (int) Math.floor(center.getZ() + distance * Math.sin(angle));
		final World world = center.getWorld();
		final int y = world.getHighestBlockYAt(blockX, blockZ);

		if (y <= world.getMinHeight()) {
			return null;
		}

		return new Location(world, blockX + 0.5, y + 1, blockZ + 0.5);
	}

	private void generatePeasantName(WanderingTrader trader) {
		final UUID traderId = trader.getUniqueId();

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				final String prompt = "Provide a unique name for a wandering peasant"
					+ " traveler in Minecraft."
					+ " *Output only first name and surname*";
				final ChatRequest req = ChatRequest.builder()
					.messages(UserMessage.from(prompt))
					.build();
				String response = chatModel.chat(req).aiMessage().text().trim();
				if (response.isEmpty()) {
					response = "Wandering Peasant";
				}

				final String name = response;
				peasantNames.put(traderId, name);

				Bukkit.getScheduler().runTask(plugin, () -> {
					final Entity entity = Bukkit.getEntity(traderId);
					if (entity instanceof WanderingTrader wt) {
						wt.setCustomName("§d" + name);
						wt.setCustomNameVisible(true);
					}
				});
			} catch (Exception e) {
				plugin.getLogger().log(Level.SEVERE,
					"[WanderingPeasantPlugin] Failed to generate peasant name.", e);
				Bukkit.getScheduler().runTask(plugin, () -> {
					final Entity entity = Bukkit.getEntity(traderId);
					if (entity instanceof WanderingTrader wt) {
						wt.setCustomName("§dWandering Peasant");
						wt.setCustomNameVisible(true);
					}
					peasantNames.put(traderId, "Wandering Peasant");
				});
			}
		});
	}

	private void despawn(UUID traderId) {
		final Entity entity = Bukkit.getEntity(traderId);
		if (entity != null) {
			entity.remove();
		}
		peasantIds.remove(traderId);
		peasantNames.remove(traderId);
	}

	// ------------------- Event Handlers -------------------

	@EventHandler(priority = EventPriority.HIGH)
	public void onTraderInteract(PlayerInteractEntityEvent event) {
		final Entity clicked = event.getRightClicked();
		if (!peasantIds.contains(clicked.getUniqueId())) {
			return;
		}

		event.setCancelled(true);

		final Player player = event.getPlayer();
		final UUID traderId = clicked.getUniqueId();
		final String name = peasantNames.getOrDefault(traderId, "Wandering Peasant");
		final boolean questAvailable = Math.random() <= QUEST_CHANCE;

		conversationManager.startConversation(player, traderId,
			name, "wandering traveler", questAvailable);
	}

	@EventHandler
	public void onTraderAcquireTrade(VillagerAcquireTradeEvent event) {
		if (peasantIds.contains(event.getEntity().getUniqueId())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		final UUID entityId = event.getEntity().getUniqueId();
		if (peasantIds.remove(entityId)) {
			peasantNames.remove(entityId);
		}
	}
}

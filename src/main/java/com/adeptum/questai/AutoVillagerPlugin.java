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

import com.adeptum.questai.model.VillageInfo;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;

/**
 * Enhanced AutoVillagerPlugin that spawns villagers only within actual villages.
 */
public class AutoVillagerPlugin implements SubPlugin {
	private static final int MIN_DOORS = 4;
	private static final int MIN_WORKSTATIONS = 3;
	private static final int MIN_SPAWN_VILLAGERS = 3;
	private static final int MAX_VILLAGERS = 8;
	private static final int SEARCH_RADIUS = 32;
	private static final int VERTICAL_SEARCH_RADIUS = 8;
	private static final int SPAWN_RADIUS = 5;
	private static final int MAX_DEPTH_BELOW_SURFACE = 10;
	private static final long RECHECK_INTERVAL = 6000;
	private static final String[] MOODS = {
		"Kindly", "Surly", "Mysterious", "Eccentric",
		"Royal", "Shy", "Brash", "Chatty", "Stoic", "Bookish"
	};
	private static final Villager.Profession[] PROFESSIONS = {
		Villager.Profession.NITWIT, Villager.Profession.ARMORER, Villager.Profession.BUTCHER,
		Villager.Profession.CARTOGRAPHER, Villager.Profession.CLERIC, Villager.Profession.FARMER,
		Villager.Profession.FISHERMAN, Villager.Profession.FLETCHER, Villager.Profession.LIBRARIAN,
		Villager.Profession.TOOLSMITH
	};
	private static final Set<Material> VILLAGE_WORKSTATION_MATERIALS = Set.of(
		Material.LECTERN,
		Material.CRAFTING_TABLE,
		Material.FLETCHING_TABLE,
		Material.BLAST_FURNACE,
		Material.SMITHING_TABLE,
		Material.COMPOSTER,
		Material.BARREL,
		Material.JUKEBOX,
		Material.BELL
	);

	private final JavaPlugin plugin;

	public AutoVillagerPlugin(JavaPlugin plugin) {
		super();
		this.plugin = plugin;
	}

	@Override
	public void onEnable() {
		Logger logger = plugin.getLogger();
		logger.info("AutoVillagerPlugin enabled. Will repopulate villages around players!");

		// Schedule a repeating task every RECHECK_INTERVAL ticks
		new BukkitRunnable() {
			@Override
			public void run() {
				// For each online player, check & repopulate
				for (Player player : Bukkit.getOnlinePlayers()) {
					checkAndRepopulateNearbyVillages(player, logger);
				}
			}
		}.runTaskTimer(plugin, RECHECK_INTERVAL, RECHECK_INTERVAL);
	}

	@Override
	public void onDisable() {
		plugin.getLogger().info("AutoVillagerPlugin disabled.");
	}

	/**
	 * Checks if the player's location is within a village and repopulates villagers if necessary.
	 *
	 * @param player The player around whom to check and repopulate villagers.
	 * @param logger The logger for spawn messages.
	 */
	private void checkAndRepopulateNearbyVillages(Player player, Logger logger) {
		if (!player.isOnline()) {
			return;
		}

		Location playerLoc = player.getLocation();
		World world = playerLoc.getWorld();
		if (world == null || !isNearSurface(world, playerLoc)) {
			return;
		}

		// Retrieve village information
		VillageInfo villageInfo = getVillageInfo(world, playerLoc);

		if (!villageInfo.village()) {
			// Not within a village; no action needed
			return;
		}

		int doorCount = villageInfo.doorCount();
		int currentVillagerCount = villageInfo.villagerCount();

		// Calculate the number of villagers to spawn based on door count
		int desiredVillagerCount = Math.max(MIN_SPAWN_VILLAGERS, doorCount / 2);
		int toSpawn = desiredVillagerCount - currentVillagerCount;

		// Ensure we don't spawn more than the maximum allowed villagers
		if (toSpawn > 0) {
			toSpawn = Math.min(toSpawn, MAX_VILLAGERS - currentVillagerCount);

			spawnVillagers(world, playerLoc, toSpawn);
			logger.info("Spawned " + toSpawn + " villagers near " + player.getName()
				+ " to match door count (" + doorCount + ").");
		}
	}

	/**
	 * Spawns the given number of villagers around location, randomizing mood and profession.
	 */
	private void spawnVillagers(World world, Location center, int amount) {
		for (int i = 0; i < amount; i++) {
			// Choose a random offset up to ~5 blocks from center
			double offsetX = ThreadLocalRandom.current().nextDouble(-SPAWN_RADIUS, SPAWN_RADIUS);
			double offsetZ = ThreadLocalRandom.current().nextDouble(-SPAWN_RADIUS, SPAWN_RADIUS);

			Location spawnLoc = center.clone().add(offsetX, 0, offsetZ);
			// Adjust Y to highest block at that X/Z so they don't spawn in the air or underground
			int y = world.getHighestBlockYAt(spawnLoc) + 1;
			spawnLoc.setY(y);

			Villager villager = (Villager) world.spawnEntity(spawnLoc, EntityType.VILLAGER);

			// Random mood
			String mood = MOODS[ThreadLocalRandom.current().nextInt(MOODS.length)];
			// Random profession
			Villager.Profession prof = PROFESSIONS[ThreadLocalRandom.current().nextInt(PROFESSIONS.length)];

			villager.setPersistent(true);
			villager.setProfession(prof);
			villager.customName(Component.text("§a[" + mood + " " + prof.name() + "]"));
			villager.setCustomNameVisible(true);
		}
	}

	/**
	 * Determines whether a specific location is within a genuine village by checking for the presence of multiple doors
	 * and workstations within a defined search radius.
	 *
	 * @param world The world to search in.
	 * @param location The central location to check for village presence.
	 * @return A VillageInfo object containing details about the village status.
	 */
	private VillageInfo getVillageInfo(World world, Location location) {
		final VillageBlocks villageBlocks = countVillageBlocks(world, location);
		final boolean village = hasRequiredVillageBlocks(villageBlocks);
		final int villagerCount = village ? countNearbyVillagers(world, location) : 0;

		return new VillageInfo(village, villageBlocks.doorCount(), villageBlocks.workstationCount(), villagerCount);
	}

	@SuppressWarnings({"PMD.CognitiveComplexity", "checkstyle:CyclomaticComplexity"})
	private VillageBlocks countVillageBlocks(World world, Location location) {
		final int centerX = location.getBlockX();
		final int centerY = location.getBlockY();
		final int centerZ = location.getBlockZ();
		final int minX = centerX - SEARCH_RADIUS;
		final int maxX = centerX + SEARCH_RADIUS;
		final int minZ = centerZ - SEARCH_RADIUS;
		final int maxZ = centerZ + SEARCH_RADIUS;
		final int minY = Math.max(centerY - VERTICAL_SEARCH_RADIUS, world.getMinHeight());
		final int maxY = Math.min(centerY + VERTICAL_SEARCH_RADIUS, world.getMaxHeight());
		int doorCount = 0;
		int workstationCount = 0;

		// Iterate chunk-by-chunk for cache locality, skipping unloaded chunks
		final int minChunkX = minX >> 4;
		final int maxChunkX = maxX >> 4;
		final int minChunkZ = minZ >> 4;
		final int maxChunkZ = maxZ >> 4;

		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				if (!world.isChunkLoaded(cx, cz)) {
					continue;
				}

				final int startX = Math.max(minX, cx << 4);
				final int endX = Math.min(maxX, (cx << 4) + 15);
				final int startZ = Math.max(minZ, cz << 4);
				final int endZ = Math.min(maxZ, (cz << 4) + 15);

				for (int x = startX; x <= endX; x++) {
					for (int z = startZ; z <= endZ; z++) {
						for (int y = minY; y <= maxY; y++) {
							final Block block = world.getBlockAt(x, y, z);
							final Material type = block.getType();

							if (isDoor(type) && block.getBlockData() instanceof Door door
								&& door.getHalf() == Bisected.Half.BOTTOM) {
								doorCount++;
							}

							if (isWorkstation(type)) {
								workstationCount++;
							}
						}
					}
				}
			}
		}

		return new VillageBlocks(doorCount, workstationCount);
	}

	private boolean hasRequiredVillageBlocks(VillageBlocks villageBlocks) {
		return villageBlocks.doorCount() >= MIN_DOORS
			&& villageBlocks.workstationCount() >= MIN_WORKSTATIONS;
	}

	private boolean isNearSurface(World world, Location location) {
		final int surfaceY = world.getHighestBlockYAt(location);
		return location.getBlockY() >= surfaceY - MAX_DEPTH_BELOW_SURFACE;
	}

	private int countNearbyVillagers(World world, Location location) {
		return (int) world
			.getNearbyEntities(location, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
			.stream()
			.filter(e -> e.getType() == EntityType.VILLAGER)
			.count();
	}

	/**
	 * Determines if the given material is a wooden door.
	 *
	 * @param type The material to check.
	 * @return True if the material is a wooden door, false otherwise.
	 */
	private boolean isDoor(Material type) {
		return Tag.WOODEN_DOORS.isTagged(type);
	}

	/**
	 * Determines if the given material is a workstation.
	 *
	 * @param type The material to check.
	 * @return True if the material is a workstation, false otherwise.
	 */
	private boolean isWorkstation(Material type) {
		return VILLAGE_WORKSTATION_MATERIALS.contains(type);
	}

	private record VillageBlocks(int doorCount, int workstationCount) {
	}
}

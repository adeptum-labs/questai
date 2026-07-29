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

import com.adeptum.questai.event.VillageCheckListener;
import com.adeptum.questai.model.VillageInfo;
import com.adeptum.questai.village.VillageScanner;
import com.adeptum.questai.village.VillageSurvey;
import com.adeptum.questai.utility.EnumUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
public class AutoVillagerPlugin implements SubPlugin, VillageScanner {
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

	private final JavaPlugin plugin;
	private final List<VillageCheckListener> villageCheckListeners;

	public AutoVillagerPlugin(JavaPlugin plugin,
		List<VillageCheckListener> villageCheckListeners) {

		super();
		this.plugin = plugin;
		this.villageCheckListeners = List.copyOf(villageCheckListeners);
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
		final Location playerLoc = player.getLocation();
		final World world = playerLoc.getWorld();
		if (world == null || !isNearSurface(world, playerLoc)) {
			return;
		}
		survey(world, playerLoc,
			info -> repopulate(player, playerLoc, world, info, logger));
	}

	/**
	 * Tops a village up once its survey is back. The player may have walked
	 * on by then, so the spawn works off the surveyed spot rather than
	 * wherever they now stand.
	 */
	private void repopulate(Player player, Location playerLoc, World world,
		VillageInfo villageInfo, Logger logger) {

		if (!villageInfo.village()) {
			// Not within a village; no action needed
			return;
		}
		for (final VillageCheckListener listener : villageCheckListeners) {
			listener.onVillageCheck(player, playerLoc, villageInfo);
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

			String mood = EnumUtil.random(MOODS);
			Villager.Profession prof = EnumUtil.random(PROFESSIONS);

			villager.setPersistent(true);
			villager.setProfession(prof);
			villager.customName(Component.text("§a[" + mood + " " + prof.name() + "]"));
			villager.setCustomNameVisible(true);
		}
	}

	/**
	 * Surveys the ground around a location for the doors and workstations
	 * that make a place a village, and hands the verdict back on the server
	 * thread.
	 *
	 * <p>Only the copy of the chunks happens here; walking the tens of
	 * thousands of blocks inside them happens off the server thread, where it
	 * costs the game nothing. Snapshots are what makes that safe — they are
	 * a detached copy, so no world state is touched from the other thread.
	 */
	private void survey(final World world, final Location location,
		final Consumer<VillageInfo> whenDone) {

		final VillageSurvey.Bounds bounds = VillageSurvey.around(
			location.getBlockX(), location.getBlockY(), location.getBlockZ(),
			SEARCH_RADIUS, VERTICAL_SEARCH_RADIUS,
			world.getMinHeight(), world.getMaxHeight());
		final Map<Long, ChunkSnapshot> ground = captureGround(world, bounds);

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			final VillageSurvey.Tally tally =
				VillageSurvey.count(bounds, new SnapshotBlocks(ground));
			// A server stopping mid-survey takes its scheduler with it
			if (plugin.isEnabled()) {
				Bukkit.getScheduler().runTask(plugin,
					() -> whenDone.accept(verdict(world, location, tally)));
			}
		});
	}

	/** Copies every loaded chunk the box reaches into, ready to read away. */
	private static Map<Long, ChunkSnapshot> captureGround(final World world,
		final VillageSurvey.Bounds bounds) {

		final Map<Long, ChunkSnapshot> ground = new HashMap<>();
		for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
			for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
				if (world.isChunkLoaded(cx, cz)) {
					ground.put(chunkKey(cx, cz), world.getChunkAt(cx, cz)
						.getChunkSnapshot(false, false, false));
				}
			}
		}
		return ground;
	}

	private static long chunkKey(final int chunkX, final int chunkZ) {
		return (long) chunkX << 32 | chunkZ & 0xffff_ffffL;
	}

	/** Counts the villagers only where the blocks already say village. */
	private VillageInfo verdict(final World world, final Location location,
		final VillageSurvey.Tally tally) {

		final boolean village = tally.doors() >= MIN_DOORS
			&& tally.workstations() >= MIN_WORKSTATIONS;
		final int villagerCount = village ? countNearbyVillagers(world, location) : 0;

		return new VillageInfo(village, tally.doors(), tally.workstations(), villagerCount);
	}

	/**
	 * Reads a survey off chunk snapshots rather than the world itself. A
	 * snapshot answers straight out of the chunk's packed block data, where
	 * {@code World#getBlockAt} builds a throwaway block object for every one
	 * of the tens of thousands of reads a survey makes.
	 */
	private static final class SnapshotBlocks implements VillageSurvey.Blocks {
		private final Map<Long, ChunkSnapshot> ground;
		private ChunkSnapshot snapshot;

		SnapshotBlocks(final Map<Long, ChunkSnapshot> ground) {
			this.ground = ground;
		}

		@Override
		public boolean openChunk(final int chunkX, final int chunkZ) {
			snapshot = ground.get(chunkKey(chunkX, chunkZ));
			return snapshot != null;
		}

		@Override
		public Material typeAt(final int x, final int y, final int z) {
			return snapshot.getBlockType(x & 15, y, z & 15);
		}

		@Override
		public boolean isDoorBottom(final int x, final int y, final int z) {
			return snapshot.getBlockData(x & 15, y, z & 15) instanceof Door door
				&& door.getHalf() == Bisected.Half.BOTTOM;
		}
	}

	private boolean isNearSurface(World world, Location location) {
		final int surfaceY = world.getHighestBlockYAt(location);
		return location.getBlockY() >= surfaceY - MAX_DEPTH_BELOW_SURFACE;
	}

	@Override
	public boolean isNearSurface(final Location location) {
		final World world = location.getWorld();
		return world != null && isNearSurface(world, location);
	}

	@Override
	public boolean hasVillagersNearby(final Location location) {
		final World world = location.getWorld();
		return world != null && countNearbyVillagers(world, location) > 0;
	}

	@Override
	public void scan(final Location location, final Consumer<VillageInfo> whenDone) {
		final World world = location.getWorld();
		if (world == null) {
			whenDone.accept(new VillageInfo(false, 0, 0, 0));
			return;
		}
		survey(world, location, whenDone);
	}

	private int countNearbyVillagers(World world, Location location) {
		return (int) world
			.getNearbyEntities(location, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
			.stream()
			.filter(e -> e.getType() == EntityType.VILLAGER)
			.count();
	}

}

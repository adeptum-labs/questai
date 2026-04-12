
package com.adeptum.questai;

import java.util.Collection;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;

/**
 * Enhanced AutoVillagerPlugin that spawns villagers only within actual villages.
 */
public class AutoVillagerPlugin implements SubPlugin {
	private static final int MIN_BEDS = 3;
	private static final int MIN_WORKSTATIONS = 3;
	private static final int MIN_EXISTING_VILLAGERS = 3;
	private static final int MAX_VILLAGERS = 20;
	private static final int SEARCH_RADIUS = 16;
	private static final int VERTICAL_SEARCH_RADIUS = 8;
	private static final int SPAWN_RADIUS = 5;
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
		if (world == null) {
			return;
		}

		// Retrieve village information
		VillageInfo villageInfo = getVillageInfo(world, playerLoc);

		if (!villageInfo.isVillage()) {
			// Not within a village; no action needed
			return;
		}

		int bedCount = villageInfo.getBedCount();
		int currentVillagerCount = villageInfo.getVillagerCount();

		// Calculate the number of villagers to spawn to match the number of beds
		int desiredVillagerCount = bedCount;
		int toSpawn = desiredVillagerCount - currentVillagerCount;

		// Ensure we don't spawn more than the maximum allowed villagers
		if (toSpawn > 0) {
			// Limit the spawn count to not exceed the maximum spawn limit
			toSpawn = Math.min(toSpawn, MAX_VILLAGERS - currentVillagerCount);

			// Spawn the required number of villagers
			spawnVillagers(world, playerLoc, toSpawn);
			logger.info("Spawned " + toSpawn + " villagers near " + player.getName()
				+ " to match bed count (" + bedCount + ").");
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
	 * Determines whether a specific location is within a genuine village by checking for the presence of multiple beds,
	 * workstations, and existing villagers within a defined search radius.
	 *
	 * @param world The world to search in.
	 * @param location The central location to check for village presence.
	 * @return A VillageInfo object containing details about the village status.
	 */
	private VillageInfo getVillageInfo(World world, Location location) {
		final VillageBlocks villageBlocks = countVillageBlocks(world, location);
		int villagerCount = 0;
		boolean village = false;

		if (hasRequiredVillageBlocks(villageBlocks)) {
			villagerCount = countNearbyVillagers(world, location);
			village = villagerCount >= MIN_EXISTING_VILLAGERS;
		}

		return new VillageInfo(village, villageBlocks.bedCount(), villageBlocks.workstationCount(), villagerCount);
	}

	@SuppressWarnings({"PMD.CognitiveComplexity", "checkstyle:CyclomaticComplexity"})
	private VillageBlocks countVillageBlocks(World world, Location location) {
		int centerX = location.getBlockX();
		int centerY = location.getBlockY();
		int centerZ = location.getBlockZ();
		int minX = centerX - SEARCH_RADIUS;
		int maxX = centerX + SEARCH_RADIUS;
		int minZ = centerZ - SEARCH_RADIUS;
		int maxZ = centerZ + SEARCH_RADIUS;
		int minY = Math.max(centerY - VERTICAL_SEARCH_RADIUS, world.getMinHeight());
		int maxY = Math.min(centerY + VERTICAL_SEARCH_RADIUS, world.getMaxHeight());
		int bedCount = 0;
		int workstationCount = 0;

		outerLoop:
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					Block block = world.getBlockAt(x, y, z);
					Material type = block.getType();

					// Check for beds
					if (isBed(type)) {
						bedCount++;
					}

					// Check for workstations
					if (isWorkstation(type)) {
						workstationCount++;
					}

					// If both criteria are met, no need to continue scanning
					if (hasRequiredVillageBlocks(bedCount, workstationCount)) {
						break outerLoop;
					}
				}
			}
		}

		return new VillageBlocks(bedCount, workstationCount);
	}

	private boolean hasRequiredVillageBlocks(VillageBlocks villageBlocks) {
		return hasRequiredVillageBlocks(villageBlocks.bedCount(), villageBlocks.workstationCount());
	}

	private boolean hasRequiredVillageBlocks(int bedCount, int workstationCount) {
		return bedCount >= MIN_BEDS && workstationCount >= MIN_WORKSTATIONS;
	}

	private int countNearbyVillagers(World world, Location location) {
		Collection<Villager> nearbyVillagers = world
			.getNearbyEntities(location, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
			.stream()
			.filter(e -> e.getType() == EntityType.VILLAGER)
			.map(e -> (Villager) e)
			.toList();
		return nearbyVillagers.size();
	}

	/**
	 * Determines if the given material is any type of bed.
	 *
	 * @param type The material to check.
	 * @return True if the material is a bed, false otherwise.
	 */
	private boolean isBed(Material type) {
		return Tag.BEDS.isTagged(type);
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

	private record VillageBlocks(int bedCount, int workstationCount) {
	}
}

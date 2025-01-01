
package com.adeptum.questai;

import java.util.Collection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import static org.bukkit.Material.BLACK_BED;
import static org.bukkit.Material.BLUE_BED;
import static org.bukkit.Material.BROWN_BED;
import static org.bukkit.Material.CYAN_BED;
import static org.bukkit.Material.GRAY_BED;
import static org.bukkit.Material.GREEN_BED;
import static org.bukkit.Material.LIGHT_BLUE_BED;
import static org.bukkit.Material.LIGHT_GRAY_BED;
import static org.bukkit.Material.LIME_BED;
import static org.bukkit.Material.MAGENTA_BED;
import static org.bukkit.Material.ORANGE_BED;
import static org.bukkit.Material.PINK_BED;
import static org.bukkit.Material.PURPLE_BED;
import static org.bukkit.Material.RED_BED;
import static org.bukkit.Material.WHITE_BED;
import static org.bukkit.Material.YELLOW_BED;

/**
 * Enhanced AutoVillagerPlugin that spawns villagers only within actual villages.
 */
public class AutoVillagerPlugin implements SubPlugin {

	// How far from the player to search for villagers
	private static final double VILLAGE_RADIUS = 32.0;
	// If we find fewer than 5 villagers, spawn enough to reach at least 5, up to 20
	private static final int MIN_VILLAGERS = 5;
	private static final int MAX_VILLAGERS = 20;
	// How frequently (in ticks) we re-check all players
	// e.g., every 5 minutes = 5*60*20 = 6000 ticks
	private static final long RECHECK_INTERVAL = 6000;
	

	// Array of random "moods"
	private static final String[] MOODS = {
		"Kindly", "Surly", "Mysterious", "Eccentric",
		"Royal", "Shy", "Brash", "Chatty", "Stoic", "Bookish"
	};

	private final JavaPlugin plugin;

	public AutoVillagerPlugin(JavaPlugin plugin) {
		super();
		this.plugin = plugin;
	}

	// Array of professions
	private static final Villager.Profession[] PROFESSIONS = {
		Villager.Profession.NITWIT, Villager.Profession.ARMORER, Villager.Profession.BUTCHER,
		Villager.Profession.CARTOGRAPHER, Villager.Profession.CLERIC, Villager.Profession.FARMER,
		Villager.Profession.FISHERMAN, Villager.Profession.FLETCHER, Villager.Profession.LIBRARIAN,
		Villager.Profession.TOOLSMITH
	};

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
	 * Checks if the player's location is within a village and repopulates villagers if necessary. Ensures that the number of
	 * villagers equals the number of beds.
	 *
	 * @param player The player around whom to check and repopulate villagers.
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
		VillageInfo villageInfo = isLocationInVillage(world, playerLoc);

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
			logger.info("Spawned " + toSpawn + " villagers near " + player.getName() + " to match bed count (" + bedCount + ").");
		}
	}

	/**
	 * Spawns the given number of villagers around location, randomizing mood (name) and profession. We'll place them in ~5
	 * block radius around loc.
	 */
	private void spawnVillagers(World world, Location center, int amount) {
		for (int i = 0; i < amount; i++) {
			// Choose a random offset up to ~5 blocks from center
			double offsetX = ThreadLocalRandom.current().nextDouble(-5, 5);
			double offsetZ = ThreadLocalRandom.current().nextDouble(-5, 5);

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
	private VillageInfo isLocationInVillage(World world, Location location) {
		// Define the search radius for village blocks (in blocks)
		final int SEARCH_RADIUS = 16; // Adjust as needed for your server's needs

		// Define key village block types indicative of village structures
		final Material[] VILLAGE_WORKSTATION_MATERIALS = {
			Material.LECTERN,
			Material.CRAFTING_TABLE,
			Material.FLETCHING_TABLE,
			Material.BLAST_FURNACE,
			Material.SMITHING_TABLE,
			Material.COMPOSTER,
			Material.BARREL,
			Material.JUKEBOX,
			Material.BELL
		// Add other workstation materials as needed
		};

		// Define minimum required counts for each criterion to qualify as a village
		final int MIN_BEDS = 3;                  // Minimum number of beds
		final int MIN_WORKSTATIONS = 3;          // Minimum number of workstations
		final int MIN_VILLAGERS = 3;             // Minimum number of existing villagers

		// Initialize counters for beds and workstations
		int bedCount = 0;
		int workstationCount = 0;

		// Calculate the bounding box for the search area
		int centerX = location.getBlockX();
		int centerY = location.getBlockY();
		int centerZ = location.getBlockZ();

		int minX = centerX - SEARCH_RADIUS;
		int maxX = centerX + SEARCH_RADIUS;
		int minZ = centerZ - SEARCH_RADIUS;
		int maxZ = centerZ + SEARCH_RADIUS;
		int minY = Math.max(centerY - 8, world.getMinHeight()); // Limit vertical search range
		int maxY = Math.min(centerY + 8, world.getMaxHeight());

		// Iterate through blocks within the search radius to count beds and workstations
		outerLoop:
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					Block block = world.getBlockAt(x, y, z);
					Material type = block.getType();

					// Check for beds
					if (isBed(type)) {
						bedCount++;
						if (bedCount >= MIN_BEDS) {
							// Beds requirement met; continue to check workstations
							// Optionally, you could break here if you only needed to count beds
						}
					}

					// Check for workstations
					if (isWorkstation(type, VILLAGE_WORKSTATION_MATERIALS)) {
						workstationCount++;
						if (workstationCount >= MIN_WORKSTATIONS) {
							// Workstations requirement met; continue to check beds
							// Optionally, you could break here if you only needed to count workstations
						}
					}

					// If both criteria are met, no need to continue scanning
					if (bedCount >= MIN_BEDS && workstationCount >= MIN_WORKSTATIONS) {
						break outerLoop;
					}
				}
			}
		}

		// Determine if the location meets the village criteria
		boolean isVillage = false;
		int villagerCount = 0;

		if (bedCount >= MIN_BEDS && workstationCount >= MIN_WORKSTATIONS) {
			// Count existing villagers within the search radius
			Collection<Villager> nearbyVillagers = world.getNearbyEntities(location, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
				.stream()
				.filter(e -> e.getType() == EntityType.VILLAGER)
				.map(e -> (Villager) e)
				.toList();

			villagerCount = nearbyVillagers.size();

			if (villagerCount >= MIN_VILLAGERS) {
				isVillage = true;
			}
		}

		return new VillageInfo(isVillage, bedCount, workstationCount, villagerCount);
	}

	/**
	 * Determines if the given material is any type of bed.
	 *
	 * @param type The material to check.
	 * @return True if the material is a bed, false otherwise.
	 */
	private boolean isBed(Material type) {
		// In Minecraft 1.14+, beds are color-specific
		// List all bed materials
		switch (type) {
			case BLACK_BED, BLUE_BED, BROWN_BED, CYAN_BED, GRAY_BED, GREEN_BED, LIGHT_BLUE_BED, LIGHT_GRAY_BED, LIME_BED, MAGENTA_BED, ORANGE_BED, PINK_BED, PURPLE_BED, RED_BED, WHITE_BED, YELLOW_BED -> {
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/**
	 * Determines if the given material is a workstation.
	 *
	 * @param type The material to check.
	 * @param villageMaterials Array of workstation materials.
	 * @return True if the material is a workstation, false otherwise.
	 */
	private boolean isWorkstation(Material type, Material[] villageMaterials) {
		for (Material mat : villageMaterials) {
			if (type == mat) {
				return true;
			}
		}
		return false;
	}
}

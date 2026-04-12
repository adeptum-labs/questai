package com.adeptum.questai;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.Random;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.plugin.java.JavaPlugin;

@Slf4j
public class FlyingPigPlugin implements SubPlugin { // Implement Listener interface
	private static final Vector UPWARD_VELOCITY = new Vector(0, 0.5, 0);

	private final Random random = new Random();
	private final JavaPlugin plugin;

	// Define a set of grass biomes where flying pigs can spawn
	private static final Set<Biome> PIG_BIOMES = Set.of(
		Biome.PLAINS,
		Biome.FOREST,
		Biome.BIRCH_FOREST,
		Biome.FLOWER_FOREST,
		Biome.SUNFLOWER_PLAINS,
		Biome.TAIGA
	);

	public FlyingPigPlugin(JavaPlugin plugin) {
		super();
		this.plugin = plugin;
	}

	@Override
	public void onEnable() {
		log.atInfo().log("Flying Pigs Plugin Enabled!");
		Bukkit.getPluginManager().registerEvents(this, plugin);

		// Schedule a task to keep flying pigs floating
		new KeepFlyingTask().runTaskTimer(plugin, 20L, 20L); // Runs every second
	}

	@Override
	public void onDisable() {
		log.atInfo().log("Flying Pigs Plugin Disabled!");
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		if (!event.isNewChunk()) {
			return;
		}

		final World world = event.getWorld();

		if (world.getEnvironment() != World.Environment.NORMAL) {
			return;
		}

		final Location spawnLocation = event.getChunk().getBlock(8, 100, 8)
			.getLocation().add(0, random.nextInt(20), 0);

		final Biome biome = world.getBiome(spawnLocation.getBlockX(),
			spawnLocation.getBlockY(),
			spawnLocation.getBlockZ()
		);

		if (!PIG_BIOMES.contains(biome)) {
			return; // Do not spawn if the biome is not a "pig biome"
		}

		if (random.nextDouble() < 0.03) { // 3% chance per chunk load
			spawnFlyingPig(world, spawnLocation);
		}
	}

	private void spawnFlyingPig(World world, Location location) {
		Pig pig = (Pig) world.spawnEntity(location, EntityType.PIG);
		pig.customName(Component.text("Flying Pig"));
		pig.setCustomNameVisible(true);
		pig.setGravity(false);
		pig.setVelocity(UPWARD_VELOCITY);
	}

	private Vector createRandomHorizontalVelocity() {
		double randomX = (random.nextDouble() - 0.5) * 0.2;
		double randomZ = (random.nextDouble() - 0.5) * 0.2;
		return new Vector(randomX, 0, randomZ);
	}

	// Keep flying pigs floating and wandering
	private final class KeepFlyingTask extends BukkitRunnable {
		@Override
		public void run() {
			for (World world : Bukkit.getWorlds()) {
				world.getEntitiesByClass(Pig.class).stream().filter(pig -> {
					return pig.customName() != null && "Flying Pig".equals(pig.customName().toString());
				}).forEach(pig -> {
					if (pig.getLocation().getY() < 80) {
						pig.setVelocity(UPWARD_VELOCITY);
					} else {
						pig.setVelocity(createRandomHorizontalVelocity());
					}
				});
			}
		}
	}
}

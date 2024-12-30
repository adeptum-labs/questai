package com.adeptum.questai.model.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import java.util.ArrayList;
import java.util.List;

public class SpawnLocator {
	public static List<Location> getSuitableLevels(Location baseLocation) {
		final List<Location> suitableLevels = new ArrayList<>();
		final World world = baseLocation.getWorld();
		final int x = baseLocation.getBlockX();
		final int z = baseLocation.getBlockZ();

		for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
			final Location location = new Location(world, x, y, z);

			if (isSuitableForItemSpawn(location)) {
				suitableLevels.add(location);
			}
		}

		return suitableLevels;
	}

	public static boolean isSuitableForItemSpawn(Location location) {
		final Block at = location.getBlock();
		final Block above = location.clone().add(0, 1, 0).getBlock();
		final Block below = location.clone().subtract(0, 1, 0).getBlock();

		return at.getType() == Material.AIR
			&& above.getType() == Material.AIR
			&& below.getType() != Material.AIR;
	}
}

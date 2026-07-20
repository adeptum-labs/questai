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

package com.adeptum.questai.utility;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Finds somewhere a mob can actually stand. The motion-blocking heightmap
 * counts leaves, water surfaces and player-built roofs as the top block,
 * so placing a mob one above it drops raiders into tree canopies and onto
 * rooftops; this checks that the ground is natural and that the mob has
 * room to occupy before handing back a location.
 */
public final class SpawnGround {

	/** Blocks of clear air a default-sized mob needs above the ground. */
	private static final int HEADROOM = 2;

	/** How far up and down to look when hugging an already-placed mob. */
	private static final int SEARCH = 4;

	private SpawnGround() {
	}

	/**
	 * A standing spot near this height, or null when the column offers no
	 * floor within the search band. Used to place companions beside a mob
	 * that is already somewhere specific: unlike {@link #findSurface} it
	 * stays at the given depth, so mates of a mob in a cave are not lifted
	 * to the roof of the world.
	 */
	public static Location findNear(final World world, final int x,
		final int y, final int z) {

		for (int dy = 0; dy <= SEARCH; dy++) {
			if (standable(world, x, y - dy, z)) {
				return new Location(world, x + 0.5, y - dy, z + 0.5);
			}
			if (dy > 0 && standable(world, x, y + dy, z)) {
				return new Location(world, x + 0.5, y + dy, z + 0.5);
			}
		}
		return null;
	}

	/** True when a mob standing with its feet here has floor and room. */
	private static boolean standable(final World world, final int x,
		final int y, final int z) {

		if (y <= world.getMinHeight() || y + HEADROOM > world.getMaxHeight()
			|| world.getBlockAt(x, y - 1, z).isPassable()) {
			return false;
		}
		for (int step = 0; step < HEADROOM; step++) {
			if (!world.getBlockAt(x, y + step, z).isPassable()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The standing spot above the surface at these coordinates, or null
	 * when the column offers no natural ground with room above it. Loads
	 * the chunk first, so the heightmap never triggers a hidden
	 * generation on whichever thread asked.
	 */
	public static Location findSurface(final World world, final int x,
		final int z) {

		world.getChunkAt(x >> 4, z >> 4).load(true);

		final int y = world.getHighestBlockYAt(x, z);
		if (y <= world.getMinHeight()
			|| !NaturalTerrain.isSurface(world.getBlockAt(x, y, z).getType())) {
			return null;
		}
		for (int step = 1; step <= HEADROOM; step++) {
			final int above = y + step;
			if (above >= world.getMaxHeight()
				|| !world.getBlockAt(x, above, z).isPassable()) {
				return null;
			}
		}
		return new Location(world, x + 0.5, y + 1.0, z + 0.5);
	}
}

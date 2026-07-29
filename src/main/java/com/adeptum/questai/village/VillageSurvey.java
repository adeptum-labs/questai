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

package com.adeptum.questai.village;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Walks a box of blocks and tallies what makes a place a village: doorways
 * and the workstations its people stand at.
 *
 * <p>The walk is the hot path of village detection — tens of thousands of
 * blocks per survey — so it reads through {@link Blocks} a chunk at a time
 * rather than asking the world where each block lives.
 */
public final class VillageSurvey {

	private static final Set<Material> WORKSTATIONS = Set.of(
		Material.LECTERN,
		Material.CRAFTING_TABLE,
		Material.FLETCHING_TABLE,
		Material.BLAST_FURNACE,
		Material.SMITHING_TABLE,
		Material.COMPOSTER,
		Material.BARREL,
		Material.JUKEBOX,
		Material.BELL);

	private VillageSurvey() {
	}

	/** What a survey found in the blocks it walked. */
	public record Tally(int doors, int workstations) {
	}

	/** The box a survey walks, in world block coordinates, ends included. */
	public record Bounds(int minX, int minY, int minZ,
		int maxX, int maxY, int maxZ) {
	}

	/**
	 * Reads blocks for a survey. Opened a chunk at a time so an
	 * implementation can hold one chunk's worth of data and answer from it,
	 * instead of paying a lookup per block to find out where it lives.
	 */
	public interface Blocks {

		/**
		 * Makes a chunk ready to read.
		 *
		 * @return false when the chunk is not loaded and must be skipped
		 */
		boolean openChunk(int chunkX, int chunkZ);

		/** The material at these world coordinates in the open chunk. */
		Material typeAt(int x, int y, int z);

		/**
		 * Whether the door at these world coordinates is its lower half.
		 * Only asked about blocks already known to be doors, so the cost of
		 * reading block state stays off the bulk of the walk.
		 */
		boolean isDoorBottom(int x, int y, int z);
	}

	/**
	 * The box of the given reach around a point, with the vertical ends
	 * pulled in to the world's floor and roof.
	 */
	public static Bounds around(final int centreX, final int centreY,
		final int centreZ, final int radius, final int verticalRadius,
		final int worldMinY, final int worldMaxY) {

		return new Bounds(
			centreX - radius,
			Math.max(centreY - verticalRadius, worldMinY),
			centreZ - radius,
			centreX + radius,
			Math.min(centreY + verticalRadius, worldMaxY),
			centreZ + radius);
	}

	/**
	 * Tallies the doorways and workstations standing in the box.
	 *
	 * <p>Chunk by chunk, so a reader keeps one chunk open at a time and
	 * unloaded ground costs nothing. Air short-circuits ahead of both
	 * material tests: it is the bulk of any box cut around a player, and
	 * neither test can ever match it.
	 */
	public static Tally count(final Bounds bounds, final Blocks blocks) {
		int doors = 0;
		int workstations = 0;

		for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4;
			chunkX++) {

			for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4;
				chunkZ++) {

				if (!blocks.openChunk(chunkX, chunkZ)) {
					continue;
				}
				final int startX = Math.max(bounds.minX(), chunkX << 4);
				final int endX = Math.min(bounds.maxX(), (chunkX << 4) + 15);
				final int startZ = Math.max(bounds.minZ(), chunkZ << 4);
				final int endZ = Math.min(bounds.maxZ(), (chunkZ << 4) + 15);

				for (int x = startX; x <= endX; x++) {
					for (int z = startZ; z <= endZ; z++) {
						for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
							final Material type = blocks.typeAt(x, y, z);
							if (type == null || type.isAir()) {
								continue;
							}
							if (WORKSTATIONS.contains(type)) {
								workstations++;
							} else if (Tag.WOODEN_DOORS.isTagged(type)
								&& blocks.isDoorBottom(x, y, z)) {
								doors++;
							}
						}
					}
				}
			}
		}
		return new Tally(doors, workstations);
	}
}

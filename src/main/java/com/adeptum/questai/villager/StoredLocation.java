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

package com.adeptum.questai.villager;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * World-independent snapshot of an entity position, safe to persist and to
 * hold for villagers whose chunks are unloaded.
 */
public record StoredLocation(UUID worldId, double x, double y, double z) {

	public static StoredLocation from(final Location location) {
		return new StoredLocation(location.getWorld().getUID(),
			location.getX(), location.getY(), location.getZ());
	}

	/** Squared horizontal distance to the given point in the same world. */
	public double distanceSquaredXz(final double originX, final double originZ) {
		final double dx = x - originX;
		final double dz = z - originZ;
		return dx * dx + dz * dz;
	}

	/** Resolves to a Bukkit location, or null when the world is not loaded. */
	public Location toLocation() {
		final World world = Bukkit.getWorld(worldId);
		return world == null ? null : new Location(world, x, y, z);
	}
}

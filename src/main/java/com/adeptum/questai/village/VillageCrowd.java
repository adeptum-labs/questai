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

import com.adeptum.questai.fortify.VillageExtent;
import com.adeptum.questai.villager.StoredLocation;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

/**
 * Where a village's people say its middle is.
 *
 * <p>A village's first centre is wherever the player who found it happened
 * to be standing, which on a settlement wider than the claim radius is
 * enough to have it discovered twice under two names. The villagers know
 * better, and asking them is what lets a centre correct itself.
 */
public final class VillageCrowd {

	/** How far out to look for the people who count as living here. */
	public static final double CROWD_RADIUS = 96.0;
	private static final double CROWD_HEIGHT = 32.0;

	private VillageCrowd() {
	}

	/** The villagers around this point, flattened to the ground plan. */
	public static List<VillageExtent.Point> points(final Location centre,
		final double radius) {

		if (centre == null || centre.getWorld() == null) {
			return List.of();
		}
		return centre.getWorld()
			.getNearbyEntities(centre, radius, CROWD_HEIGHT, radius)
			.stream()
			.filter(entity -> entity.getType() == EntityType.VILLAGER)
			.map(entity -> new VillageExtent.Point(
				entity.getLocation().getX(), entity.getLocation().getZ()))
			.toList();
	}

	/**
	 * The centre this village's crowd describes, or null when fewer than
	 * {@link VillageExtent#MIN_POINTS} villagers are about for the answer
	 * to be worth having. A sufficient crowd whose median happens to land
	 * back on the stored centre still returns that centre rather than
	 * null — whether an unchanged answer is worth acting on is for the
	 * caller to decide, not this method. Keeps the stored height: the
	 * crowd speaks to where a village is, not how high.
	 */
	public static StoredLocation measure(final StoredLocation stored) {
		final Location centre = stored.toLocation();
		final List<VillageExtent.Point> crowd = points(centre, CROWD_RADIUS);
		if (crowd.size() < VillageExtent.MIN_POINTS) {
			return null;
		}
		final VillageExtent.Extent extent =
			VillageExtent.measure(crowd, stored.x(), stored.z());
		return new StoredLocation(stored.worldId(), extent.centreX(),
			stored.y(), extent.centreZ());
	}
}

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

package com.adeptum.questai.relic;

import com.adeptum.questai.service.DeliveryRecipientPicker;
import java.util.List;

/**
 * Pure direction-finding for the Wayfarer's Compass.
 */
public final class RelicCompass {

	private static final String[] DIRECTIONS =
		{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private RelicCompass() {
	}

	/**
	 * Describes the nearest named villager relative to the given position,
	 * e.g. "Mira Bloom \u00a77— NE, 134 blocks", or null when none is known.
	 */
	public static String pointTo(
		final List<DeliveryRecipientPicker.Candidate> candidates,
		final double x, final double z) {

		DeliveryRecipientPicker.Candidate nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (final DeliveryRecipientPicker.Candidate candidate : candidates) {
			final double sq = candidate.location().distanceSquaredXz(x, z);
			if (sq < nearestSq) {
				nearestSq = sq;
				nearest = candidate;
			}
		}
		if (nearest == null) {
			return null;
		}

		final int blocks = (int) Math.round(Math.sqrt(nearestSq));
		return nearest.name() + " \u00a77— "
			+ cardinal(nearest.location().x() - x, nearest.location().z() - z)
			+ ", " + blocks + " blocks";
	}

	/** Eight-way compass direction; north is negative z, east positive x. */
	public static String cardinal(final double dx, final double dz) {
		final double angle = Math.toDegrees(Math.atan2(dx, -dz));
		final int index = (int) Math.floor((angle + 360 + 22.5) % 360 / 45);
		return DIRECTIONS[index];
	}
}

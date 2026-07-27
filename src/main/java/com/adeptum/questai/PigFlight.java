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

/**
 * The arc a flying pig travels: an ordinary pig's hop, taken somewhere with
 * barely any gravity to pull it back.
 *
 * <p>The whole trick is the ratio. The shove is a pig's own jump, unchanged;
 * only the fall is slowed, to about a seventh of what the world does to
 * everything else. That turns a hop into a long, slow, floating arc while
 * still reading as a hop rather than as flight, which a pig has no business
 * doing.
 *
 * <p>Pure and off-server, so the shape of the arc can be argued with in a
 * test rather than by standing under one.
 */
public final class PigFlight {

	/** Pulled down this much per tick; vanilla uses 0.08. */
	public static final double GRAVITY = 0.012;

	/** The shove of a hop — a vanilla jump, left exactly as it is. */
	public static final double HOP = 0.42;

	/** How near the ground a pig must fall before it hops again. */
	public static final double HOP_HEIGHT = 2.0;

	/** No pig climbs past this above the ground it took off from. */
	public static final double CEILING = 48.0;

	/** Above this the wings are working; at or below it they are spread. */
	private static final double BEATING_ABOVE = 0.0;

	private PigFlight() {
	}

	/**
	 * The vertical speed one step on from this one.
	 *
	 * @param rise      how fast the pig is climbing, negative when falling
	 * @param clearance blocks of open air below the pig
	 * @param rested    whether it has waited long enough to hop again
	 */
	public static double nextRise(final double rise, final double clearance,
		final boolean rested) {

		if (clearance > CEILING) {
			// Out of air to climb into: shed any lift and start back down
			return Math.min(rise, 0.0) - GRAVITY;
		}
		if (rested && rise <= 0 && clearance <= HOP_HEIGHT) {
			return HOP;
		}
		return rise - GRAVITY;
	}

	/** Whether the wings are working, rather than held out gliding. */
	public static boolean beating(final double rise) {
		return rise > BEATING_ABOVE;
	}
}

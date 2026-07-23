package com.adeptum.questai.fortify;

import java.util.Arrays;

/**
 * The decidable half of choosing a building site: whether a patch of ground is
 * flat enough, what height to level it to, and which way to turn the plan.
 *
 * <p>The world scan itself lives in the sub-plugin; keeping the arithmetic here
 * means the rules that matter can be tested without a server.
 */
public final class WorkSite {

	private WorkSite() {
	}

	/**
	 * Whether these surface heights are close enough together to level.
	 * Refusing a wide spread is what stops the plugin carving a mesa.
	 */
	public static boolean levelFits(final int[] heights, final int maxSpread) {
		return heights.length != 0 && spread(heights) <= maxSpread;
	}

	/** How far the highest column stands above the lowest; zero when empty. */
	public static int spread(final int[] heights) {
		if (heights.length == 0) {
			return 0;
		}
		int lowest = heights[0];
		int highest = heights[0];
		for (final int height : heights) {
			lowest = Math.min(lowest, height);
			highest = Math.max(highest, height);
		}
		return highest - lowest;
	}

	/** The height to level to; the median resists a single spike or dip. */
	public static int medianHeight(final int[] heights) {
		final int[] sorted = heights.clone();
		Arrays.sort(sorted);
		return sorted[(sorted.length - 1) / 2];
	}

	/**
	 * Quarter turns needed so the plan's local south points along the given
	 * offset, which is how a door ends up facing the village.
	 */
	public static int facingRotation(final double dx, final double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? 3 : 1;
		}
		return dz >= 0 ? 0 : 2;
	}

	/** A validated place to build, in world coordinates. */
	public record Candidate(int x, int baseY, int z, int rotation) {
	}

	/**
	 * How good a footprint is: flat ground first, then the one that costs
	 * the fewest trees. Ranking rather than taking the first fit is what
	 * stops the crew settling on the roughest ground the ring offers.
	 */
	public record Rating(int spread, int coverColumns) {

		public boolean betterThan(final Rating other) {
			if (other == null) {
				return true;
			}
			return spread != other.spread
				? spread < other.spread : coverColumns < other.coverColumns;
		}
	}
}

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
		if (heights.length == 0) {
			return false;
		}
		int lowest = heights[0];
		int highest = heights[0];
		for (final int height : heights) {
			lowest = Math.min(lowest, height);
			highest = Math.max(highest, height);
		}
		return highest - lowest <= maxSpread;
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
}

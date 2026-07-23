package com.adeptum.questai.fortify;

import lombok.Getter;
import org.bukkit.World;

/**
 * The hunt for somewhere to put a structure: every footprint in the band is
 * measured, and the flattest one that costs the fewest trees wins.
 *
 * <p>Taking the first fit, as this once did, meant a single sample landing on
 * a rough shoulder settled the matter while open pasture lay two steps
 * further round. Scoring instead costs one full sweep and gives the village
 * the site it would have picked itself.
 */
public final class SiteSearch {

	/** Roughly how far apart to sample along a ring, in blocks. */
	private static final int SAMPLE_SPACING = 4;
	private static final int MIN_SAMPLES = 24;
	private static final int RADIUS_STEP = 2;
	/** How broken a footprint may be before levelling it becomes a quarry. */
	private static final int MAX_LEVEL_SPREAD = 5;

	@Getter
	private final SiteGround ground;
	/** Why the ground was refused, for the donor to be told about. */
	@Getter
	private final SiteReport report = new SiteReport();

	public SiteSearch(final World world) {
		this.ground = new SiteGround(world);
	}

	/** The best footprint in the band, or null when the ground refuses. */
	public WorkSite.Candidate find(final VillageExtent.Extent extent,
		final int[] band, final int footprint) {

		final Best best = new Best();
		for (int radius = band[0]; radius <= band[1]; radius += RADIUS_STEP) {
			if (scanRing(extent, radius, footprint, best)) {
				break;
			}
		}
		return best.candidate;
	}

	/**
	 * Walks one ring around the centre. Answers true once what it holds
	 * cannot be bettered, so open pasture ends the sweep early.
	 */
	private boolean scanRing(final VillageExtent.Extent extent,
		final int radius, final int footprint, final Best best) {

		final int samples = Math.max(MIN_SAMPLES,
			(int) (2 * Math.PI * radius / SAMPLE_SPACING));
		for (int step = 0; step < samples; step++) {
			final double angle = 2 * Math.PI * step / samples;
			consider(extent, footprint, best,
				(int) Math.round(extent.centreX() + radius * Math.cos(angle)),
				(int) Math.round(extent.centreZ() + radius * Math.sin(angle)));
			if (perfect(best.rating)) {
				return true;
			}
		}
		return false;
	}

	/** Weighs one footprint against the best held so far. */
	private void consider(final VillageExtent.Extent extent,
		final int footprint, final Best best, final int x, final int z) {

		final Patch patch = measure(x, z, footprint);
		final WorkSite.Rating rating =
			patch == null ? null : rate(patch, extent, x, z);
		if (rating != null && rating.betterThan(best.rating)) {
			best.rating = rating;
			best.candidate = candidate(patch, extent, x, z);
		}
	}

	/** A footprint's ground heights and how many columns need clearing. */
	private record Patch(int[] heights, int coverColumns) {
	}

	/** The best site seen so far, carried through the sweep. */
	private static final class Best {
		private WorkSite.Candidate candidate;
		private WorkSite.Rating rating;
	}

	/**
	 * The heights across a footprint, or null when any column of it refuses
	 * — the reason being counted on the way out, so a refusal can say what
	 * the crew kept running into.
	 */
	private Patch measure(final int x, final int z, final int footprint) {
		final int[] heights = new int[footprint * footprint];
		int index = 0;
		int coverColumns = 0;

		for (int dx = 0; dx < footprint; dx++) {
			for (int dz = 0; dz < footprint; dz++) {
				final SiteGround.Column column =
					ground.columnAt(x + dx, z + dz);
				if (!accepted(column)) {
					return null;
				}
				if (column.cover() > 0) {
					coverColumns++;
				}
				// The floor goes one above the ground the footing rests on
				heights[index] = column.groundY() + 1;
				index++;
			}
		}
		return new Patch(heights, coverColumns);
	}

	/** Whether this column can be built on, counting the reason when not. */
	private boolean accepted(final SiteGround.Column column) {
		if (column == null) {
			report.unscanned();
			return false;
		}
		if (!column.usable()) {
			report.rejected(column.verdict());
			return false;
		}
		return true;
	}

	/** This patch's rating, or null when it is too broken to level. */
	private WorkSite.Rating rate(final Patch patch,
		final VillageExtent.Extent extent, final int x, final int z) {

		final int spread = WorkSite.spread(patch.heights());
		if (spread > MAX_LEVEL_SPREAD) {
			report.tooSteep(spread, x - extent.centreX(),
				z - extent.centreZ());
			return null;
		}
		return new WorkSite.Rating(spread, patch.coverColumns());
	}

	/** Flat and clear; nothing further round the ring can beat it. */
	private static boolean perfect(final WorkSite.Rating rating) {
		return rating != null && rating.spread() == 0
			&& rating.coverColumns() == 0;
	}

	private static WorkSite.Candidate candidate(final Patch patch,
		final VillageExtent.Extent extent, final int x, final int z) {

		return new WorkSite.Candidate(x, WorkSite.medianHeight(patch.heights()),
			z, WorkSite.facingRotation(extent.centreX() - x,
				extent.centreZ() - z));
	}
}

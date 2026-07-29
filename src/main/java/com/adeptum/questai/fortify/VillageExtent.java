package com.adeptum.questai.fortify;

import java.util.ArrayList;
import java.util.List;

/**
 * How large a village actually is, measured from the people living in it.
 *
 * <p>A village's stored centre is wherever the player stood when it was
 * named, which can be a good sixty blocks off its middle. Siting a
 * structure by that point alone puts the search ring in a lake while the
 * fields lie open on the other side, so the works measure the villagers
 * instead and work outward from what they find.
 */
public final class VillageExtent {

	/** Below this many villagers the crowd says nothing worth trusting. */
	public static final int MIN_POINTS = 3;
	private static final int MIN_RADIUS = 16;
	private static final int MAX_RADIUS = 64;
	/** How wide a village is assumed to be when nobody is about. */
	private static final int FALLBACK_RADIUS = 24;
	/** The share of villagers the measured radius must reach. */
	private static final double REACH_SHARE = 0.8;

	private VillageExtent() {
	}

	/** Where somebody stands, flattened to the ground plan. */
	public record Point(double x, double z) {
	}

	/** A village's middle and how far its people spread from it. */
	public record Extent(int centreX, int centreZ, int radius) {
	}

	/**
	 * The extent these villagers describe, or the stored centre with an
	 * assumed radius when too few of them are about to say anything.
	 *
	 * <p>Medians throughout: one villager who has wandered off to a lake
	 * must not drag the middle of the village after him.
	 */
	public static Extent measure(final List<Point> points,
		final double fallbackX, final double fallbackZ) {

		if (points.size() < MIN_POINTS) {
			return new Extent((int) Math.round(fallbackX),
				(int) Math.round(fallbackZ), FALLBACK_RADIUS);
		}
		final int centreX = (int) Math.round(median(points.stream()
			.map(Point::x).toList()));
		final int centreZ = (int) Math.round(median(points.stream()
			.map(Point::z).toList()));
		return new Extent(centreX, centreZ, reach(points, centreX, centreZ));
	}

	/** The band a point structure searches, or null when it sites itself. */
	public static int[] band(final Extent extent, final VillageWork work) {
		final int radius = extent.radius();
		return switch (work) {
			case WATCHTOWER -> new int[] {radius + 8, radius + 24};
			case BELL_TOWER -> new int[] {Math.max(4, radius / 3),
				Math.max(12, radius * 2 / 3)};
			default -> null;
		};
	}

	/** How far out the village reaches, ignoring the furthest strays. */
	private static int reach(final List<Point> points, final int centreX,
		final int centreZ) {

		final List<Double> distances = new ArrayList<>();
		for (final Point point : points) {
			distances.add(Math.hypot(point.x() - centreX, point.z() - centreZ));
		}
		distances.sort(null);
		final int index =
			(int) Math.round(REACH_SHARE * (distances.size() - 1));
		return Math.clamp((int) Math.round(distances.get(index)),
			MIN_RADIUS, MAX_RADIUS);
	}

	private static double median(final List<Double> values) {
		final List<Double> sorted = new ArrayList<>(values);
		sorted.sort(null);
		return sorted.get((sorted.size() - 1) / 2);
	}
}

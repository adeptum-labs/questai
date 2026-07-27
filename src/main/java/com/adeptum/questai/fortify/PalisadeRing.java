package com.adeptum.questai.fortify;

import java.util.ArrayList;
import java.util.List;

/**
 * The palisade's geometry: a square ring of tiling wall pieces at a fixed
 * Chebyshev distance from the village centre.
 *
 * <p>Pure and deterministic, so the ring is recomputed on demand instead of
 * persisted, and a restart cannot disagree with itself about where the wall
 * goes. The store only remembers how far each building front has got.
 */
public final class PalisadeRing {

	public static final int RADIUS = 48;
	public static final int MODULE_LENGTH = 4;

	private static final int MODULES_PER_SIDE = 23;
	private static final int FILLERS_PER_SIDE = 3;
	private static final int GATE_RUN_MODULES = 12;
	private static final int GATE_RUN_FILLERS = 2;
	private static final int CLOSING_RUN_MODULES = 11;
	private static final int CLOSING_RUN_FILLERS = 1;

	private PalisadeRing() {
	}

	/**
	 * The full ring in build order: starting at the gate module in the
	 * middle of the south side, running east to the south-east corner,
	 * clockwise around the other three sides, then closing the loop back
	 * to just short of the gate.
	 */
	public static List<RingModule> ring(final int centreX, final int centreZ) {
		final List<RingModule> ring = new ArrayList<>();
		run(ring, centreX - MODULE_LENGTH / 2, centreZ + RADIUS, 0,
			0, GATE_RUN_MODULES, GATE_RUN_FILLERS);
		corner(ring, centreX + RADIUS, centreZ + RADIUS);
		run(ring, centreX + RADIUS, centreZ + RADIUS - 1, 3,
			0, MODULES_PER_SIDE, FILLERS_PER_SIDE);
		corner(ring, centreX + RADIUS, centreZ - RADIUS);
		run(ring, centreX + RADIUS - 1, centreZ - RADIUS, 2,
			0, MODULES_PER_SIDE, FILLERS_PER_SIDE);
		corner(ring, centreX - RADIUS, centreZ - RADIUS);
		run(ring, centreX - RADIUS, centreZ - RADIUS + 1, 1,
			0, MODULES_PER_SIDE, FILLERS_PER_SIDE);
		corner(ring, centreX - RADIUS, centreZ + RADIUS);
		run(ring, centreX - RADIUS + 1, centreZ + RADIUS, 0,
			CLOSING_RUN_FILLERS, CLOSING_RUN_MODULES, 0);
		return List.copyOf(ring);
	}

	/** The index of the gate module: by construction, the ring's start. */
	public static int gateIndex(final List<RingModule> ring) {
		return 0;
	}

	/** The ring index the forward front builds next. */
	public static int nextForward(final int gateIndex, final int placed,
		final int size) {

		return Math.floorMod(gateIndex + placed, size);
	}

	/** The ring index the backward front builds next. */
	public static int nextBackward(final int gateIndex, final int placed,
		final int size) {

		return Math.floorMod(gateIndex - 1 - placed, size);
	}

	/** How many wall cells a piece of this kind covers walking the ring. */
	public static int length(final Kind kind) {
		return kind == Kind.MODULE_A || kind == Kind.MODULE_B
			? MODULE_LENGTH : 1;
	}

	/**
	 * The wall cell {@code offset} steps along this slot's run. A negative
	 * offset steps back down the ring, which is how the cell bordering the
	 * piece behind gets named without knowing which piece that is.
	 */
	public static RingCell cell(final RingModule slot, final int offset) {
		return new RingCell(stepX(slot.x(), slot.rotation(), offset),
			stepZ(slot.z(), slot.rotation(), offset));
	}

	/** True once every slot of the ring has been built or skipped. */
	public static boolean complete(final int placedForward,
		final int placedBackward, final int size) {

		return placedForward + placedBackward >= size;
	}

	/**
	 * Lays one straight run along the direction of {@code rotation}: any
	 * leading fillers first, then alternating modules, then any trailing
	 * fillers — each piece's origin is the first wall cell it covers
	 * walking the ring, matching the schematic's rotation convention.
	 */
	private static void run(final List<RingModule> ring, final int x, final int z,
		final int rotation, final int leadingFillers, final int modules,
		final int trailingFillers) {

		int offset = 0;
		for (int i = 0; i < leadingFillers; i++) {
			ring.add(fillerAt(x, z, rotation, offset));
			offset++;
		}
		boolean moduleA = true;
		for (int i = 0; i < modules; i++) {
			ring.add(moduleAt(x, z, rotation, offset, moduleA));
			moduleA = !moduleA;
			offset += MODULE_LENGTH;
		}
		for (int i = 0; i < trailingFillers; i++) {
			ring.add(fillerAt(x, z, rotation, offset));
			offset++;
		}
	}

	private static void corner(final List<RingModule> ring, final int x, final int z) {
		ring.add(new RingModule(x, z, 0, Kind.CORNER));
	}

	private static RingModule moduleAt(final int x, final int z, final int rotation,
		final int offset, final boolean moduleA) {

		return new RingModule(stepX(x, rotation, offset), stepZ(z, rotation, offset),
			rotation, moduleA ? Kind.MODULE_A : Kind.MODULE_B);
	}

	private static RingModule fillerAt(final int x, final int z, final int rotation,
		final int offset) {

		return new RingModule(stepX(x, rotation, offset), stepZ(z, rotation, offset),
			rotation, Kind.FILLER);
	}

	/** Advances the x coordinate along the run direction for this rotation. */
	private static int stepX(final int x, final int rotation, final int offset) {
		return switch (rotation) {
			case 0 -> x + offset;
			case 2 -> x - offset;
			default -> x;
		};
	}

	/** Advances the z coordinate along the run direction for this rotation. */
	private static int stepZ(final int z, final int rotation, final int offset) {
		return switch (rotation) {
			case 1 -> z + offset;
			case 3 -> z - offset;
			default -> z;
		};
	}

	/** What stands at one slot of the ring. */
	public enum Kind { MODULE_A, MODULE_B, CORNER, FILLER }

	/** One placed piece: world origin, quarter turns, and which piece. */
	public record RingModule(int x, int z, int rotation, Kind kind) {
	}

	/** One cell of the wall line, without the piece standing on it. */
	public record RingCell(int x, int z) {
	}
}

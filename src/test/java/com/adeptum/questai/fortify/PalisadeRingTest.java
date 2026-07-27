package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PalisadeRingTest {

	private static final List<PalisadeRing.RingModule> RING =
		PalisadeRing.ring(0, 0);

	@Test
	void theRingHasFourCornersAndFourTiledSides() {
		final long corners = RING.stream()
			.filter(m -> m.kind() == PalisadeRing.Kind.CORNER).count();
		final long fillers = RING.stream()
			.filter(m -> m.kind() == PalisadeRing.Kind.FILLER).count();
		assertEquals(4, corners);
		assertEquals(12, fillers);
		assertEquals(4 + 4 * 23 + 12, RING.size());
	}

	@Test
	void pieceLengthsFollowTheirKind() {
		assertEquals(PalisadeRing.MODULE_LENGTH,
			PalisadeRing.length(PalisadeRing.Kind.MODULE_A));
		assertEquals(PalisadeRing.MODULE_LENGTH,
			PalisadeRing.length(PalisadeRing.Kind.MODULE_B));
		assertEquals(1, PalisadeRing.length(PalisadeRing.Kind.CORNER));
		assertEquals(1, PalisadeRing.length(PalisadeRing.Kind.FILLER));
	}

	@Test
	void cellsStepForwardAndBackAlongEachRun() {
		// rotation 0 runs east along x, 1 south along z, 2 west, 3 north
		final int[][] steps = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		for (int rotation = 0; rotation < 4; rotation++) {
			final PalisadeRing.RingModule slot = new PalisadeRing.RingModule(
				10, 20, rotation, PalisadeRing.Kind.MODULE_A);
			final int[] step = steps[rotation];
			assertEquals(new PalisadeRing.RingCell(10, 20),
				PalisadeRing.cell(slot, 0));
			assertEquals(new PalisadeRing.RingCell(10 + 3 * step[0],
				20 + 3 * step[1]), PalisadeRing.cell(slot, 3));
			assertEquals(new PalisadeRing.RingCell(10 - step[0], 20 - step[1]),
				PalisadeRing.cell(slot, -1),
				"a negative offset must step back down the ring");
		}
	}

	@Test
	void everyPerimeterCellIsCoveredExactlyOnce() {
		final Set<String> cells = new HashSet<>();
		for (final PalisadeRing.RingModule module : RING) {
			for (int i = 0; i < PalisadeRing.length(module.kind()); i++) {
				final PalisadeRing.RingCell cell = PalisadeRing.cell(module, i);
				assertTrue(cells.add(cell.x() + "/" + cell.z()),
					"cell " + cell + " covered twice");
			}
		}
		assertEquals(8 * PalisadeRing.RADIUS, cells.size());
		for (final String cell : cells) {
			final String[] parts = cell.split("/");
			final int x = Integer.parseInt(parts[0]);
			final int z = Integer.parseInt(parts[1]);
			assertEquals(PalisadeRing.RADIUS,
				Math.max(Math.abs(x), Math.abs(z)),
				cell + " is off the ring line");
		}
	}

	@Test
	void straightRunsAlternateTheirModules() {
		PalisadeRing.Kind previous = null;
		for (final PalisadeRing.RingModule module : RING) {
			if (module.kind() == PalisadeRing.Kind.MODULE_A
				|| module.kind() == PalisadeRing.Kind.MODULE_B) {
				if (previous != null) {
					assertFalse(module.kind() == previous,
						"two equal modules in a row at " + module);
				}
				previous = module.kind();
			} else {
				previous = null;
			}
		}
	}

	@Test
	void theGateSitsMidSouth() {
		final PalisadeRing.RingModule gate =
			RING.get(PalisadeRing.gateIndex(RING));
		assertEquals(PalisadeRing.RADIUS, gate.z());
		assertTrue(Math.abs(gate.x()) <= PalisadeRing.MODULE_LENGTH,
			"gate module is not centred on the south side: " + gate);
	}

	@Test
	void everyPieceFacesItsOutsideAwayFromTheVillage() {
		for (final PalisadeRing.RingModule module : RING) {
			if (module.kind() == PalisadeRing.Kind.CORNER) {
				continue;
			}
			final int expected;
			if (module.z() == PalisadeRing.RADIUS) {
				expected = 0;
			} else if (module.x() == PalisadeRing.RADIUS) {
				expected = 3;
			} else if (module.z() == -PalisadeRing.RADIUS) {
				expected = 2;
			} else {
				expected = 1;
			}
			assertEquals(expected, module.rotation(),
				module + " faces the wrong way");
		}
	}

	@Test
	void theFrontsCoverEveryIndexExactlyOnceAndMeetOnce(
	) {
		final int size = RING.size();
		final int gate = PalisadeRing.gateIndex(RING);
		final Set<Integer> built = new HashSet<>();
		int forward = 0;
		int backward = 0;
		while (!PalisadeRing.complete(forward, backward, size)) {
			assertTrue(built.add(
				PalisadeRing.nextForward(gate, forward, size)));
			forward++;
			if (PalisadeRing.complete(forward, backward, size)) {
				break;
			}
			assertTrue(built.add(
				PalisadeRing.nextBackward(gate, backward, size)));
			backward++;
		}
		assertEquals(size, built.size(), "the fronts missed or repeated");
	}
}

package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WatchtowerSchematicTest {

	private static final WorkSchematic TOWER =
		WorkSchematic.load("structures/watchtower.txt");

	@Test
	void theWatchtowerFillsItsNineByNineSite() {
		assertEquals(9, TOWER.getWidth());
		assertEquals(9, TOWER.getDepth());
	}

	@Test
	void everyStageHasBlocks() {
		for (final BuildStage stage : BuildStage.values()) {
			assertFalse(TOWER.stage(stage).isEmpty(), stage + " places nothing");
		}
	}

	@Test
	void stagesDoNotOverlap() {
		final Set<String> seen = new HashSet<>();
		for (final BuildStage stage : BuildStage.values()) {
			for (final SchematicEntry entry : TOWER.stage(stage)) {
				final String cell = entry.x() + "/" + entry.y() + "/" + entry.z();
				assertTrue(seen.add(cell),
					"cell " + cell + " is claimed by two stages");
			}
		}
	}

	@Test
	void theLadderRisesInOneUnbrokenColumn() {
		final List<SchematicEntry> rungs = ladderRungs();
		assertFalse(rungs.isEmpty(), "no ladder");
		final SchematicEntry first = rungs.get(0);
		for (final SchematicEntry rung : rungs) {
			assertEquals(first.x(), rung.x(), "ladder wanders in x");
			assertEquals(first.z(), rung.z(), "ladder wanders in z");
		}
		final Set<Integer> heights = new HashSet<>();
		rungs.forEach(rung -> heights.add(rung.y()));
		for (int y = minY(rungs); y <= maxY(rungs); y++) {
			assertTrue(heights.contains(y), "ladder skips layer " + y);
		}
	}

	@Test
	void aHatchCrownsTheLadderSoItCanBeClimbedOut() {
		final List<SchematicEntry> rungs = ladderRungs();
		final SchematicEntry top = rungs.stream()
			.max((a, b) -> Integer.compare(a.y(), b.y())).orElseThrow();
		final SchematicEntry above = cellAt(top.x(), top.y() + 1, top.z());
		assertNotNull(above,
			"nothing sits above the ladder top; the shaft has no exit");
		assertEquals(PaletteRole.TRAPDOOR, above.role(),
			"the ladder tops out under a " + above.role()
				+ "; a climber cannot get past it");
	}

	private static List<SchematicEntry> ladderRungs() {
		return allEntries().stream()
			.filter(entry -> entry.role() == PaletteRole.LADDER)
			.toList();
	}

	private static SchematicEntry cellAt(final int x, final int y, final int z) {
		return allEntries().stream()
			.filter(entry -> entry.x() == x && entry.y() == y && entry.z() == z)
			.findFirst()
			.orElse(null);
	}

	private static List<SchematicEntry> allEntries() {
		final java.util.List<SchematicEntry> all = new java.util.ArrayList<>();
		for (final BuildStage stage : BuildStage.values()) {
			all.addAll(TOWER.stage(stage));
		}
		return all;
	}

	private static int minY(final List<SchematicEntry> rungs) {
		return rungs.stream().mapToInt(SchematicEntry::y).min().orElseThrow();
	}

	private static int maxY(final List<SchematicEntry> rungs) {
		return rungs.stream().mapToInt(SchematicEntry::y).max().orElseThrow();
	}
}

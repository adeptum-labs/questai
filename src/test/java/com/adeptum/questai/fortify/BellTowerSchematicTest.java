package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BellTowerSchematicTest {

	private static final WorkSchematic TOWER =
		WorkSchematic.load("structures/bell-tower.txt");

	@Test
	void theBellTowerFillsItsSevenBySevenSite() {
		assertEquals(7, TOWER.getWidth());
		assertEquals(7, TOWER.getDepth());
	}

	@Test
	void everyStageHasBlocks() {
		for (final BuildStage stage : BuildStage.values()) {
			assertFalse(TOWER.stage(stage).isEmpty(),
				stage + " places nothing");
		}
	}

	@Test
	void stagesDoNotOverlap() {
		final Set<String> seen = new HashSet<>();
		for (final BuildStage stage : BuildStage.values()) {
			for (final SchematicEntry entry : TOWER.stage(stage)) {
				final String cell =
					entry.x() + "/" + entry.y() + "/" + entry.z();
				assertTrue(seen.add(cell),
					"cell " + cell + " is claimed by two stages");
			}
		}
	}

	@Test
	void theBellHangsUnderItsBeamAndTheRodTopsOut() {
		boolean bell = false;
		boolean rod = false;
		int rodY = 0;
		int bellY = 0;
		for (final BuildStage stage : BuildStage.values()) {
			for (final SchematicEntry entry : TOWER.stage(stage)) {
				if (entry.role() == PaletteRole.BELL) {
					bell = true;
					bellY = entry.y();
				}
				if (entry.role() == PaletteRole.ROD) {
					rod = true;
					rodY = entry.y();
				}
			}
		}
		assertTrue(bell, "no bell");
		assertTrue(rod, "no lightning rod");
		assertEquals(10, bellY);
		assertEquals(14, rodY);
	}

	@Test
	void theRoofOverhangsTheShaft() {
		final long roofCells = TOWER.stage(BuildStage.ROOF).stream()
			.filter(e -> e.y() == 11)
			.filter(e -> e.role() == PaletteRole.ROOF_STAIRS)
			.count();
		assertEquals(24, roofCells,
			"the Y11 eaves should ring the full 7x7 footprint");
	}
}

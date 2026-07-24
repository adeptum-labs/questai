package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WatchtowerHatchTest {

	private static final WorkSchematic TOWER =
		WorkSchematic.load("structures/watchtower.txt");

	@Test
	void theHatchSitsDirectlyAboveTheLaddersTopRung() {
		assertEquals(new WatchtowerHatch.Cell(4, 9, 3),
			WatchtowerHatch.target(TOWER));
	}

	@Test
	void theStrandedCellIsOneStepTowardTheBackingWall() {
		assertEquals(new WatchtowerHatch.Cell(4, 9, 2),
			WatchtowerHatch.stranded(TOWER));
	}

	@Test
	void theCorrectedPlansAlreadyPlaceATrapdoorOnTheTarget() {
		assertEquals(PaletteRole.TRAPDOOR,
			roleAt(WatchtowerHatch.target(TOWER)));
	}

	private static PaletteRole roleAt(final WatchtowerHatch.Cell cell) {
		for (final BuildStage stage : BuildStage.values()) {
			for (final SchematicEntry entry : TOWER.stage(stage)) {
				if (entry.x() == cell.x() && entry.y() == cell.y()
					&& entry.z() == cell.z()) {
					return entry.role();
				}
			}
		}
		return null;
	}
}

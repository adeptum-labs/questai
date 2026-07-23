package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WorkSchematicTest {

	private static final WorkSchematic TOWER =
		WorkSchematic.load("structures/watchtower.txt");

	@Test
	void theWatchtowerFillsItsNineByNineSite() {
		assertEquals(9, TOWER.getWidth());
		assertEquals(9, TOWER.getDepth());
	}

	@Test
	void theHeightCoversEveryLayerAboveTheBase() {
		assertEquals(15, TOWER.getHeight());
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
		final Set<String> seen = new java.util.HashSet<>();
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
	void everyEntryLiesInsideTheFootprint() {
		for (final BuildStage stage : BuildStage.values()) {
			for (final SchematicEntry entry : TOWER.stage(stage)) {
				assertTrue(entry.x() >= 0 && entry.x() < TOWER.getWidth());
				assertTrue(entry.z() >= 0 && entry.z() < TOWER.getDepth());
			}
		}
	}

	@Test
	void scaffoldingIsOnlyEverStructural() {
		final Set<BuildStage> stages = EnumSet.noneOf(BuildStage.class);
		for (final BuildStage stage : BuildStage.values()) {
			if (TOWER.stage(stage).stream()
				.anyMatch(e -> e.role() == PaletteRole.SCAFFOLDING)) {
				stages.add(stage);
			}
		}
		assertFalse(stages.contains(BuildStage.DETAIL),
			"scaffolding must be gone by the finish");
	}

	@Test
	void aQuarterTurnMovesNorthToEast() {
		final SchematicEntry entry = new SchematicEntry(0, 0, 0,
			PaletteRole.WOOD_STAIRS, "facing=north", BuildStage.SHELL);
		final SchematicEntry turned = WorkSchematic.rotate(entry, 1, 7, 7);

		assertEquals("facing=east", turned.state());
	}

	@Test
	void aQuarterTurnMovesTheCorner() {
		final SchematicEntry entry = new SchematicEntry(0, 3, 0,
			PaletteRole.ROUGH_STONE, null, BuildStage.SHELL);
		final SchematicEntry turned = WorkSchematic.rotate(entry, 1, 7, 7);

		assertEquals(6, turned.x());
		assertEquals(0, turned.z());
		assertEquals(3, turned.y());
	}

	@Test
	void fourQuarterTurnsRestoreTheOriginal() {
		final SchematicEntry entry = new SchematicEntry(1, 2, 5,
			PaletteRole.WOOD_STAIRS, "facing=west,half=top", BuildStage.ROOF);
		SchematicEntry turned = entry;
		for (int i = 0; i < 4; i++) {
			turned = WorkSchematic.rotate(turned, 1, 7, 7);
		}
		assertEquals(entry, turned);
	}

	@Test
	void rotationSwapsLogAxes() {
		final SchematicEntry entry = new SchematicEntry(0, 0, 0,
			PaletteRole.LOG_X, "axis=x", BuildStage.DETAIL);
		assertEquals("axis=z", WorkSchematic.rotate(entry, 1, 7, 7).state());
	}

	@Test
	void rotationAdvancesBannerRotation() {
		final SchematicEntry entry = new SchematicEntry(0, 0, 0,
			PaletteRole.BANNER, "rotation=2", BuildStage.DETAIL);
		assertEquals("rotation=6", WorkSchematic.rotate(entry, 1, 7, 7).state());
		assertEquals("rotation=2", WorkSchematic.rotate(entry, 4, 7, 7).state());
	}

	@Test
	void theTowerHasADoorAndALadder() {
		final List<PaletteRole> roles = java.util.Arrays.stream(BuildStage.values())
			.flatMap(stage -> TOWER.stage(stage).stream())
			.map(SchematicEntry::role)
			.collect(Collectors.toList());

		assertTrue(roles.contains(PaletteRole.DOOR));
		assertTrue(roles.contains(PaletteRole.LADDER));
		assertTrue(roles.contains(PaletteRole.CAMPFIRE));
	}
}

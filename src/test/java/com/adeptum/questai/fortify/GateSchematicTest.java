package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GateSchematicTest {

	private static final WorkSchematic GATE =
		WorkSchematic.load("structures/gate.txt");

	@Test
	void theGateFillsItsElevenByEightSite() {
		assertEquals(11, GATE.getWidth());
		assertEquals(8, GATE.getDepth());
	}

	@Test
	void everyStageHasBlocks() {
		for (final BuildStage stage : BuildStage.values()) {
			assertFalse(GATE.stage(stage).isEmpty(), stage + " places nothing");
		}
	}

	@Test
	void stagesDoNotOverlap() {
		final Set<String> seen = new HashSet<>();
		for (final BuildStage stage : BuildStage.values()) {
			for (final SchematicEntry entry : GATE.stage(stage)) {
				final String cell =
					entry.x() + "/" + entry.y() + "/" + entry.z();
				assertTrue(seen.add(cell),
					"cell " + cell + " is claimed by two stages");
			}
		}
	}

	@Test
	void theGateKeepsItsCharacterPieces() {
		final Set<PaletteRole> roles = new HashSet<>();
		for (final BuildStage stage : BuildStage.values()) {
			GATE.stage(stage).forEach(e -> roles.add(e.role()));
		}
		assertTrue(roles.contains(PaletteRole.PATH), "wheel ruts missing");
		assertTrue(roles.contains(PaletteRole.WALL_BANNER), "banner missing");
		assertTrue(roles.contains(PaletteRole.LADDER), "turret ladder missing");
		assertTrue(roles.contains(PaletteRole.TRAPDOOR), "bridge hatch missing");
	}

	@Test
	void scaffoldingIsGoneByTheFinish() {
		assertFalse(GATE.stage(BuildStage.DETAIL).stream()
			.anyMatch(e -> e.role() == PaletteRole.SCAFFOLDING));
	}
}

package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PalisadePiecesTest {

	private static List<SchematicEntry> all(final String name) {
		final WorkSchematic piece = WorkSchematic.load("structures/" + name);
		return piece.stage(BuildStage.SHELL);
	}

	@Test
	void bothModulesRaiseAFullWallLine() {
		for (final String name : new String[] {
			"palisade-module-a.txt", "palisade-module-b.txt"}) {

			final List<SchematicEntry> entries = all(name);
			for (int y = 0; y <= 2; y++) {
				final int levelY = y;
				final long logs = entries.stream()
					.filter(e -> e.y() == levelY && e.z() == 1)
					.filter(e -> e.role() == PaletteRole.LOG_Y)
					.count();
				assertEquals(4, logs,
					name + " wall line is not solid at Y" + levelY);
			}
		}
	}

	@Test
	void moduleBCarriesTheTorchInside() {
		assertTrue(all("palisade-module-b.txt").stream()
			.anyMatch(e -> e.role() == PaletteRole.WALL_TORCH && e.z() == 0),
			"module B lost its inside torch");
	}

	@Test
	void theSeamTopsOutInStrippedLogs() {
		final long stripped = all("palisade-seam.txt").stream()
			.filter(e -> e.y() == 3)
			.filter(e -> e.role() == PaletteRole.STRIPPED_LOG)
			.count();
		assertEquals(2, stripped, "the topping-out joint is missing");
	}

	@Test
	void cornerAndEndPostsHangALantern() {
		for (final String name : new String[] {
			"palisade-corner.txt", "palisade-end.txt"}) {

			assertTrue(all(name).stream()
				.anyMatch(e -> e.role() == PaletteRole.LANTERN),
				name + " lost its lantern");
		}
	}

	@Test
	void everyPieceHasFootingUnderItsWallLine() {
		for (final String name : new String[] {
			"palisade-module-a.txt", "palisade-module-b.txt",
			"palisade-corner.txt", "palisade-end.txt",
			"palisade-seam.txt"}) {

			assertTrue(all(name).stream()
				.anyMatch(e -> e.y() == -1
					&& e.role() == PaletteRole.ROUGH_STONE),
				name + " floats");
		}
	}
}

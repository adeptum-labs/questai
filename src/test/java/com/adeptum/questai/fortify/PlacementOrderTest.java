package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlacementOrderTest {

	private static SchematicEntry entry(final int y, final PaletteRole role,
		final String state) {

		return new SchematicEntry(0, y, 0, role, state, BuildStage.SHELL);
	}

	@Test
	void lowerBlocksComeFirst() {
		final List<SchematicEntry> entries = new ArrayList<>(List.of(
			entry(5, PaletteRole.PLANKS, null),
			entry(1, PaletteRole.PLANKS, null)));
		entries.sort(PlacementOrder.comparator());

		assertEquals(1, entries.get(0).y());
	}

	@Test
	void supportsComeBeforeAttachments() {
		final List<SchematicEntry> entries = new ArrayList<>(List.of(
			entry(2, PaletteRole.LADDER, "facing=south"),
			entry(2, PaletteRole.ROUGH_STONE, null)));
		entries.sort(PlacementOrder.comparator());

		assertEquals(PaletteRole.ROUGH_STONE, entries.get(0).role());
	}

	@Test
	void lowerDoorHalfComesFirst() {
		final List<SchematicEntry> entries = new ArrayList<>(List.of(
			entry(1, PaletteRole.DOOR, "half=upper,facing=north"),
			entry(1, PaletteRole.DOOR, "half=lower,facing=north")));
		entries.sort(PlacementOrder.comparator());

		assertTrue(entries.get(0).state().contains("half=lower"));
	}

	@Test
	void campfireIsPlacedLast() {
		assertTrue(PlacementOrder.rank(PaletteRole.CAMPFIRE)
			> PlacementOrder.rank(PaletteRole.HAY));
	}

	@Test
	void onlyConnectingBlocksNeedPhysics() {
		assertTrue(PlacementOrder.needsPhysics(PaletteRole.FENCE));
		assertTrue(PlacementOrder.needsPhysics(PaletteRole.STONE_WALL));
		assertFalse(PlacementOrder.needsPhysics(PaletteRole.PLANKS));
		assertFalse(PlacementOrder.needsPhysics(PaletteRole.LADDER));
	}
}

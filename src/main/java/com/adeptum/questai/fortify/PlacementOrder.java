package com.adeptum.questai.fortify;

import java.util.Comparator;

/**
 * The order blocks must be written in so every attachment finds its support
 * already standing. Without this, placing with physics off drops ladders,
 * torches and door halves on the floor as items.
 */
public final class PlacementOrder {

	private PlacementOrder() {
	}

	/** Lowest first, then supports before the things that hang off them. */
	public static Comparator<SchematicEntry> comparator() {
		return Comparator.comparingInt(SchematicEntry::y)
			.thenComparingInt(entry -> rank(entry.role()))
			.thenComparingInt(PlacementOrder::doorHalf);
	}

	/** Attachment class; lower is placed first. */
	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	public static int rank(final PaletteRole role) {
		// Grows one exhaustive case per palette role by design.
		return switch (role) {
			case AIR -> 0;
			case ROUGH_STONE, MOSSY_STONE, ANDESITE, DRESSED_STONE,
				CRACKED_STONE, DAUB, LOG_Y, LOG_X, STRIPPED_LOG, PLANKS, HAY,
				SPOIL, PATH, SCAFFOLDING, ROOF_TILE -> 1;
			case STONE_STAIRS, STONE_WALL, WOOD_STAIRS, WOOD_SLAB, FENCE,
				DRESSED_STAIRS, ROOF_STAIRS, GLASS_PANE -> 2;
			case DOOR -> 3;
			case LADDER, TRAPDOOR, WALL_TORCH, BANNER, WALL_BANNER, LANTERN,
				BARREL, CAULDRON, BELL -> 4;
			case CAMPFIRE, ROD -> 5;
		};
	}

	/** Lower door half before upper, whatever order the plan listed them. */
	private static int doorHalf(final SchematicEntry entry) {
		if (entry.role() != PaletteRole.DOOR || entry.state() == null) {
			return 0;
		}
		return entry.state().contains("half=upper") ? 1 : 0;
	}

	/** Fences, walls and panes need physics so their connections compute. */
	public static boolean needsPhysics(final PaletteRole role) {
		return role == PaletteRole.FENCE || role == PaletteRole.STONE_WALL
			|| role == PaletteRole.GLASS_PANE;
	}
}

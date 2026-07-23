package com.adeptum.questai.fortify;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

/**
 * The order blocks must be written in so every attachment finds its support
 * already standing. Without this, placing with physics off drops ladders,
 * torches and door halves on the floor as items.
 */
public final class PlacementOrder {

	/** Attachment class per role; lower is placed first. */
	private static final Map<PaletteRole, Integer> ROLE_RANKS = buildRanks();

	private PlacementOrder() {
	}

	/** Lowest first, then supports before the things that hang off them. */
	public static Comparator<SchematicEntry> comparator() {
		return Comparator.comparingInt(SchematicEntry::y)
			.thenComparingInt(entry -> rank(entry.role()))
			.thenComparingInt(PlacementOrder::doorHalf);
	}

	/** Attachment class; lower is placed first. */
	public static int rank(final PaletteRole role) {
		return ROLE_RANKS.get(role);
	}

	private static Map<PaletteRole, Integer> buildRanks() {
		final Map<PaletteRole, Integer> ranks = new EnumMap<>(PaletteRole.class);
		ranks.put(PaletteRole.AIR, 0);
		putAll(ranks, 1, PaletteRole.ROUGH_STONE, PaletteRole.MOSSY_STONE,
			PaletteRole.ANDESITE, PaletteRole.DRESSED_STONE,
			PaletteRole.CRACKED_STONE, PaletteRole.LOG_Y, PaletteRole.LOG_X,
			PaletteRole.STRIPPED_LOG, PaletteRole.PLANKS, PaletteRole.HAY,
			PaletteRole.SCAFFOLDING);
		putAll(ranks, 2, PaletteRole.STONE_STAIRS, PaletteRole.STONE_WALL,
			PaletteRole.WOOD_STAIRS, PaletteRole.WOOD_SLAB, PaletteRole.FENCE);
		putAll(ranks, 3, PaletteRole.DOOR);
		putAll(ranks, 4, PaletteRole.LADDER, PaletteRole.TRAPDOOR,
			PaletteRole.WALL_TORCH, PaletteRole.BANNER, PaletteRole.LANTERN,
			PaletteRole.BARREL, PaletteRole.CAULDRON);
		putAll(ranks, 5, PaletteRole.CAMPFIRE);
		return ranks;
	}

	private static void putAll(final Map<PaletteRole, Integer> ranks,
		final int value, final PaletteRole... roles) {

		for (final PaletteRole role : roles) {
			ranks.put(role, value);
		}
	}

	/** Lower door half before upper, whatever order the plan listed them. */
	private static int doorHalf(final SchematicEntry entry) {
		if (entry.role() != PaletteRole.DOOR || entry.state() == null) {
			return 0;
		}
		return entry.state().contains("half=upper") ? 1 : 0;
	}

	/** Fences and walls need physics so their connections compute. */
	public static boolean needsPhysics(final PaletteRole role) {
		return role == PaletteRole.FENCE || role == PaletteRole.STONE_WALL;
	}
}

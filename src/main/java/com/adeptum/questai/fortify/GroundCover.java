package com.adeptum.questai.fortify;

import com.adeptum.questai.utility.NaturalTerrain;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;

/**
 * What a building crew may cut away to reach the ground, and what stops
 * them for good.
 *
 * <p>Reading a column from the top down answers the only question site
 * selection really asks: is there honest ground under this, or is somebody
 * already standing on it? Kept pure so the rules can be argued with in a
 * test rather than in a world.
 */
public final class GroundCover {

	private static final Set<Material> BLOCKERS = EnumSet.of(Material.WATER,
		Material.LAVA, Material.POWDER_SNOW, Material.ICE,
		Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE);
	private static final Set<Material> LEAVES = named("_LEAVES");
	private static final Set<Material> TIMBER = allTimber();
	private static final Set<Material> GROWTH = allGrowth();

	private GroundCover() {
	}

	/** What one block of a column means to a crew looking for ground. */
	public enum Verdict {
		/** Nothing there; keep looking down. */
		EMPTY,
		/** Growth or drift that comes down with an axe. */
		COVER,
		/** Honest ground: this is where the footing goes. */
		GROUND,
		/** Water, lava or ice: no amount of clearing helps. */
		BLOCKED,
		/** Somebody's work, which is never cleared to make room. */
		BUILT
	}

	/**
	 * What the block at {@code index} means, given everything above it in
	 * the same column. The column runs downward, so index zero is the
	 * highest block.
	 *
	 * <p>Logs are the delicate case: a trunk under a canopy is a tree and
	 * comes down, while a bare log column is a cabin wall or a palisade and
	 * turns the site away. Judging them by what stands above is what keeps
	 * the crew from felling somebody's house.
	 */
	public static Verdict verdict(final List<Material> column, final int index) {
		final Material material = column.get(index);
		if (material.isAir()) {
			return Verdict.EMPTY;
		}
		if (BLOCKERS.contains(material)) {
			return Verdict.BLOCKED;
		}
		if (TIMBER.contains(material)) {
			return canopyAbove(column, index) ? Verdict.COVER : Verdict.BUILT;
		}
		return isCover(material) ? Verdict.COVER : settled(material);
	}

	/** Untouched terrain to build on, or somebody's work to leave be. */
	private static Verdict settled(final Material material) {
		return NaturalTerrain.isSurface(material)
			? Verdict.GROUND : Verdict.BUILT;
	}

	/** Whether leaves stand anywhere above this block in the column. */
	private static boolean canopyAbove(final List<Material> column,
		final int index) {

		for (int above = 0; above < index; above++) {
			if (LEAVES.contains(column.get(above))) {
				return true;
			}
		}
		return false;
	}

	/** True for anything the crew is willing to clear away. */
	public static boolean isCover(final Material material) {
		return LEAVES.contains(material) || TIMBER.contains(material)
			|| GROWTH.contains(material);
	}

	private static Set<Material> allTimber() {
		final Set<Material> materials = named("_LOG");
		materials.addAll(named("_WOOD"));
		return materials;
	}

	/** Growth and drift that can sit on top of the ground unbidden. */
	private static Set<Material> allGrowth() {
		final Set<Material> materials = EnumSet.of(Material.SNOW,
			Material.COBWEB, Material.VINE, Material.GLOW_LICHEN,
			Material.MOSS_CARPET, Material.LILY_PAD, Material.CACTUS,
			Material.BAMBOO, Material.SUGAR_CANE, Material.SWEET_BERRY_BUSH,
			Material.PUMPKIN, Material.MELON, Material.BROWN_MUSHROOM_BLOCK,
			Material.RED_MUSHROOM_BLOCK, Material.MUSHROOM_STEM);
		materials.addAll(named("_SAPLING"));
		return materials;
	}

	/** Every material whose name ends with this suffix. */
	private static Set<Material> named(final String suffix) {
		final Set<Material> materials = EnumSet.noneOf(Material.class);
		for (final Material material : Material.values()) {
			if (material.name().endsWith(suffix)) {
				materials.add(material);
			}
		}
		return materials;
	}
}

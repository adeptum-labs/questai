package com.adeptum.questai.fortify;

import com.adeptum.questai.utility.NaturalTerrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * The ground as a building crew reads it: what stands in each column, and
 * what it takes to turn a patch of it into a level pad.
 *
 * <p>Columns are probed once and remembered, because neighbouring footprints
 * overlap heavily and a site search would otherwise read the same block
 * hundreds of times. Chunks are never loaded to answer a question: ground
 * nobody has walked to is simply unknown, the same rule the palisade
 * follows when a front reaches an unloaded slot.
 */
public final class SiteGround {

	/** How far under the canopy to keep looking before giving up. */
	private static final int MAX_DEPTH = 32;

	private final World world;
	private final Map<Long, Column> columns = new HashMap<>();

	public SiteGround(final World world) {
		this.world = world;
	}

	/** One column's answer: where its ground lies and what sits on it. */
	public record Column(int groundY, int cover, GroundCover.Verdict verdict) {

		/** Whether a footing can go here at all. */
		public boolean usable() {
			return verdict == GroundCover.Verdict.GROUND;
		}
	}

	/** The column here, probed once and remembered; null when unloaded. */
	public Column columnAt(final int x, final int z) {
		final long key = (long) x << 32 | (z & 0xffffffffL);
		final Column known = columns.get(key);
		if (known != null) {
			return known;
		}
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return null;
		}
		final Column probed = probe(x, z);
		columns.put(key, probed);
		return probed;
	}

	/**
	 * Reads down from the surface until the column says something final:
	 * ground to build on, water to keep clear of, or work to leave alone.
	 */
	private Column probe(final int x, final int z) {
		final List<Material> column = new ArrayList<>();
		final int top = world.getHighestBlockYAt(x, z);
		final int floor = Math.max(world.getMinHeight(), top - MAX_DEPTH);

		int cover = 0;
		for (int y = top; y >= floor; y--) {
			column.add(world.getBlockAt(x, y, z).getType());
			final GroundCover.Verdict verdict =
				GroundCover.verdict(column, column.size() - 1);
			if (verdict == GroundCover.Verdict.COVER) {
				cover++;
			} else if (verdict != GroundCover.Verdict.EMPTY) {
				return new Column(y, cover, verdict);
			}
		}
		return new Column(floor, cover, GroundCover.Verdict.BLOCKED);
	}

	/**
	 * Cuts the footprint down to the pad and fills what falls short of it,
	 * so the structure stands on ground instead of hovering over a dip or
	 * burying itself in a rise. Only growth and honest terrain move; a
	 * column holding somebody's work is left exactly as it is.
	 */
	public void prepare(final Location base, final int width, final int depth,
		final int height) {

		final BlockData spoil = BiomePalette.forBiome(base.getBlock().getBiome())
			.resolve(PaletteRole.SPOIL, null);
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < depth; dz++) {
				prepareColumn(base.getBlockX() + dx, base.getBlockZ() + dz,
					base.getBlockY(), height, spoil);
			}
		}
	}

	private void prepareColumn(final int x, final int z, final int baseY,
		final int height, final BlockData spoil) {

		final Column column = columnAt(x, z);
		if (column == null || !column.usable()) {
			return;
		}
		cut(x, z, baseY, baseY + height);
		if (spoil != null) {
			fill(x, z, column.groundY() + 1, baseY, spoil);
		}
	}

	/** Takes the growth and the rise off the pad, up to the eaves. */
	private void cut(final int x, final int z, final int from, final int to) {
		for (int y = from; y < to; y++) {
			final Block block = world.getBlockAt(x, y, z);
			if (clearable(block)) {
				block.setType(Material.AIR, false);
			}
		}
	}

	/** Brings a column that falls short of the pad up to it. */
	private void fill(final int x, final int z, final int from, final int to,
		final BlockData spoil) {

		for (int y = from; y < to; y++) {
			final Block block = world.getBlockAt(x, y, z);
			if (clearable(block)) {
				block.setBlockData(spoil, false);
			}
		}
	}

	/** What a crew may cut away: growth, drift and terrain, never work. */
	public static boolean clearable(final Block block) {
		final Material material = block.getType();
		return material.isAir() || !material.isSolid()
			|| GroundCover.isCover(material)
			|| NaturalTerrain.isSurface(material);
	}
}

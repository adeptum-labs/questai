package com.adeptum.questai.fortify;

import com.adeptum.questai.villager.StoredLocation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a block position belongs to a village's built works.
 *
 * <p>Point structures answer exactly: a cheap bounding-box reject, then a
 * rotated-cell lookup against the schematic. The palisade keeps no
 * per-block record, so it answers as a band around the ring line — callers
 * treat {@link Hit#RING} as a claim to be confirmed by material, not fact.
 */
public final class BuiltBlocks {

	private static final Map<VillageWork, Cached> SCHEMATICS =
		new EnumMap<>(VillageWork.class);
	private static final int RING_BAND = 1;
	private static final int RING_HEIGHT_WINDOW = 24;

	private BuiltBlocks() {
	}

	/** What, if anything, of the village's works stands at this block. */
	public static Hit hit(final WorkState state, final int x, final int y,
		final int z) {

		if (state == null) {
			return Hit.NONE;
		}
		for (final Map.Entry<Integer, WorkState.BuiltSite> built
			: state.getBuiltSites().entrySet()) {

			if (inStructure(built.getKey(), built.getValue(), x, y, z)) {
				return Hit.STRUCTURE;
			}
		}
		if (onRing(state, x, y, z)) {
			return Hit.RING;
		}
		return Hit.NONE;
	}

	private static boolean inStructure(final int tier,
		final WorkState.BuiltSite site, final int x, final int y, final int z) {

		final VillageWork work = VillageWork.byTier(tier);
		if (work == null || work.getSchematicResource() == null) {
			return false;
		}
		final Cached cached = SCHEMATICS.computeIfAbsent(work,
			BuiltBlocks::cache);
		final int dx = x - (int) site.origin().x();
		final int dy = y - (int) site.origin().y();
		final int dz = z - (int) site.origin().z();
		return inBox(cached, site.rotation(), dx, dy, dz)
			&& isCell(cached, site.rotation(), dx, dy, dz);
	}

	private static boolean inBox(final Cached cached, final int rotation,
		final int dx, final int dy, final int dz) {

		final boolean swapped = rotation % 2 != 0;
		final int spanX = swapped ? cached.depth() : cached.width();
		final int spanZ = swapped ? cached.width() : cached.depth();
		return inFootprint(dx, dz, spanX, spanZ) && inHeight(dy, cached.maxY());
	}

	private static boolean inFootprint(final int dx, final int dz,
		final int spanX, final int spanZ) {

		return dx >= 0 && dx < spanX && dz >= 0 && dz < spanZ;
	}

	private static boolean inHeight(final int dy, final int maxY) {
		return dy >= -1 && dy <= maxY;
	}

	private static boolean isCell(final Cached cached, final int rotation,
		final int dx, final int dy, final int dz) {

		for (final SchematicEntry raw : cached.cells()) {
			final SchematicEntry cell = WorkSchematic.rotate(raw, rotation,
				cached.width(), cached.depth());
			if (cell.x() == dx && cell.y() == dy && cell.z() == dz) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether this block stands on the ring line. Measured from the origin the
	 * palisade was raised on, which is the only point the wall was ever drawn
	 * around: a village's stored centre is merely where its finder stood, and
	 * a band taken from there lands wherever the two have drifted apart —
	 * claiming blocks in the middle of the village and none of the wall.
	 */
	private static boolean onRing(final WorkState state, final int x,
		final int y, final int z) {

		final WorkState.BuiltSite palisade = state.getBuiltSites()
			.get(VillageWork.PALISADE.ordinal());
		if (palisade == null) {
			return false;
		}
		final StoredLocation origin = palisade.origin();
		if (Math.abs(y - (int) origin.y()) > RING_HEIGHT_WINDOW) {
			return false;
		}
		final int cheb = Math.max(Math.abs(x - (int) origin.x()),
			Math.abs(z - (int) origin.z()));
		return Math.abs(cheb - PalisadeRing.RADIUS) <= RING_BAND;
	}

	private static Cached cache(final VillageWork work) {
		final WorkSchematic schematic =
			WorkSchematic.load(work.getSchematicResource());
		int maxY = 0;
		final List<SchematicEntry> cells = new ArrayList<>();
		for (final BuildStage stage : BuildStage.values()) {
			for (final SchematicEntry entry : schematic.stage(stage)) {
				// Stakes and scaffolding come down when the build
				// finishes; where they stood is not the standing works
				if (entry.stage() == BuildStage.SURVEY
					|| entry.role() == PaletteRole.SCAFFOLDING) {
					continue;
				}
				maxY = Math.max(maxY, entry.y());
				cells.add(entry);
			}
		}
		return new Cached(schematic.getWidth(), schematic.getDepth(), maxY,
			List.copyOf(cells));
	}

	/** What stood at a queried block, if anything. */
	public enum Hit {
		NONE, STRUCTURE, RING
	}

	/** One schematic flattened for membership lookups. */
	private record Cached(int width, int depth, int maxY,
		List<SchematicEntry> cells) {
	}
}

package com.adeptum.questai.fortify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the two roof cells a standing watchtower needs put right: the one
 * above the ladder that must carry the climbable hatch, and the floor cell
 * one step toward the backing wall where an earlier layout stranded it.
 *
 * <p>Pure, with local grid offsets only, so the world-facing repair that
 * rotates and places them can stay thin.
 */
public final class WatchtowerHatch {

	private WatchtowerHatch() {
	}

	/** A local grid offset from a structure's placement origin. */
	public record Cell(int x, int y, int z) {
	}

	/** The floor cell directly above the ladder's top rung. */
	public static Cell target(final WorkSchematic schematic) {
		final SchematicEntry top = topRung(schematic);
		return new Cell(top.x(), top.y() + 1, top.z());
	}

	/** The floor cell an earlier layout put the hatch in: one step north,
	 *  over the wall the ladder backs against rather than the shaft. */
	public static Cell stranded(final WorkSchematic schematic) {
		final Cell target = target(schematic);
		return new Cell(target.x(), target.y(), target.z() - 1);
	}

	private static SchematicEntry topRung(final WorkSchematic schematic) {
		final List<SchematicEntry> rungs = new ArrayList<>();
		for (final BuildStage stage : BuildStage.values()) {
			for (final SchematicEntry entry : schematic.stage(stage)) {
				if (entry.role() == PaletteRole.LADDER) {
					rungs.add(entry);
				}
			}
		}
		return rungs.stream()
			.max(Comparator.comparingInt(SchematicEntry::y))
			.orElseThrow(() ->
				new IllegalArgumentException("structure has no ladder"));
	}
}

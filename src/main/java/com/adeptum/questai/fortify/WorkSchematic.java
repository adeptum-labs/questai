package com.adeptum.questai.fortify;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * A structure loaded from its authored grid resource.
 *
 * <p>The resources are the plans as drawn, not a translation of them, so the
 * design and what gets built cannot drift apart.
 */
public final class WorkSchematic {

	private static final String[] FACINGS = {"north", "east", "south", "west"};

	@Getter
	private final int width;
	@Getter
	private final int depth;
	private final Map<BuildStage, List<SchematicEntry>> byStage;

	private WorkSchematic(final int width, final int depth,
		final Map<BuildStage, List<SchematicEntry>> byStage) {

		this.width = width;
		this.depth = depth;
		this.byStage = byStage;
	}

	/** Everything this stage places, in no particular order. */
	public List<SchematicEntry> stage(final BuildStage stage) {
		return byStage.getOrDefault(stage, List.of());
	}

	/** Reads a structure resource from the plugin jar. */
	public static WorkSchematic load(final String resourceName) {
		try (InputStream in = WorkSchematic.class.getClassLoader()
			.getResourceAsStream(resourceName)) {

			if (in == null) {
				throw new IllegalArgumentException(
					"Missing structure resource " + resourceName);
			}
			return parse(new BufferedReader(
				new InputStreamReader(in, StandardCharsets.UTF_8)));
		} catch (final IOException e) {
			throw new UncheckedIOException(
				"Could not read structure " + resourceName, e);
		}
	}

	private static WorkSchematic parse(final BufferedReader reader)
		throws IOException {

		final Map<Character, Legend> legend = new HashMap<>();
		final Map<BuildStage, List<SchematicEntry>> byStage =
			new EnumMap<>(BuildStage.class);
		int width = 0;
		int depth = 0;
		int layerY = 0;
		int row = -1;

		String line = reader.readLine();
		while (line != null) {
			final String text = line.strip();
			if (text.isEmpty() || text.startsWith("#")) {
				line = reader.readLine();
				continue;
			}

			if (text.startsWith("size ")) {
				final String[] parts = text.split("\\s+");
				width = Integer.parseInt(parts[1]);
				depth = Integer.parseInt(parts[2]);
			} else if (text.startsWith("def ")) {
				final String[] parts = text.split("\\s+");
				legend.put(parts[1].charAt(0), new Legend(
					PaletteRole.valueOf(parts[2]),
					"-".equals(parts[3]) ? null : parts[3],
					BuildStage.valueOf(parts[4])));
			} else if (text.startsWith("layer ")) {
				layerY = Integer.parseInt(text.substring("layer ".length()).strip());
				row = 0;
			} else {
				readGridRow(text, legend, byStage, layerY, row);
				row++;
			}
			line = reader.readLine();
		}
		return new WorkSchematic(width, depth, byStage);
	}

	private static void readGridRow(final String text,
		final Map<Character, Legend> legend,
		final Map<BuildStage, List<SchematicEntry>> byStage,
		final int layerY, final int row) {

		for (int x = 0; x < text.length(); x++) {
			final char symbol = text.charAt(x);
			if (symbol == '.') {
				continue;
			}
			final Legend entry = legend.get(symbol);
			if (entry == null) {
				throw new IllegalArgumentException(
					"Undeclared symbol '" + symbol + "' at layer " + layerY);
			}
			byStage.computeIfAbsent(entry.stage(), key -> new ArrayList<>())
				.add(new SchematicEntry(x, layerY, row,
					entry.role(), entry.state(), entry.stage()));
		}
	}

	/**
	 * Turns one entry clockwise about the footprint centre, carrying its block
	 * state around with it. A wrong facing here is the most visible bug the
	 * feature can have, which is why it is a pure function with its own tests.
	 */
	public static SchematicEntry rotate(final SchematicEntry entry,
		final int quarterTurns, final int width, final int depth) {

		final int turns = Math.floorMod(quarterTurns, 4);
		int x = entry.x();
		int z = entry.z();
		int sizeX = width;
		int sizeZ = depth;
		String state = entry.state();

		for (int turn = 0; turn < turns; turn++) {
			final int rotatedX = sizeZ - 1 - z;
			final int rotatedZ = x;
			x = rotatedX;
			z = rotatedZ;
			final int swap = sizeX;
			sizeX = sizeZ;
			sizeZ = swap;
			state = rotateState(state);
		}
		return new SchematicEntry(x, entry.y(), z, entry.role(), state,
			entry.stage());
	}

	private static String rotateState(final String state) {
		if (state == null) {
			return null;
		}
		String rotated = state;
		for (int i = 0; i < FACINGS.length; i++) {
			final String from = "facing=" + FACINGS[i];
			if (rotated.contains(from)) {
				rotated = rotated.replace(from,
					"facing=" + FACINGS[(i + 1) % FACINGS.length]);
				break;
			}
		}
		if (rotated.contains("axis=x")) {
			rotated = rotated.replace("axis=x", "axis=z");
		} else if (rotated.contains("axis=z")) {
			rotated = rotated.replace("axis=x", "axis=x")
				.replace("axis=z", "axis=x");
		}
		return rotateBanner(rotated);
	}

	private static String rotateBanner(final String state) {
		final int at = state.indexOf("rotation=");
		if (at < 0) {
			return state;
		}
		int end = at + "rotation=".length();
		while (end < state.length() && Character.isDigit(state.charAt(end))) {
			end++;
		}
		final int value = Integer.parseInt(
			state.substring(at + "rotation=".length(), end));
		return state.substring(0, at) + "rotation=" + ((value + 4) % 16)
			+ state.substring(end);
	}

	/** One legend row: what a grid character means. */
	private record Legend(PaletteRole role, String state, BuildStage stage) {
	}
}

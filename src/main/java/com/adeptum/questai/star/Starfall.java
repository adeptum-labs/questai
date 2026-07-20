/*
 * Copyright (C) 2026 Adeptum AB, org nr. 559494-1824
 *
 * This file is part of QuestAI.
 *
 * QuestAI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * QuestAI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with QuestAI. If not, see
 * <https://www.gnu.org/licenses/>.
 */

package com.adeptum.questai.star;

import com.adeptum.questai.relic.RelicCompass;
import com.adeptum.questai.utility.NaturalTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;

/**
 * The tunables and pure geometry behind the starfall event. Impacts only
 * ever target and carve ordinary natural terrain, and the trigger math
 * keeps starfalls very rare: roughly one per six to ten real hours across
 * the whole server.
 */
public final class Starfall {

	/** About one hit per 240 eligible 30-second polls (~4.8 h of night). */
	public static final double STARFALL_CHANCE = 1.0 / 240;
	public static final long COOLDOWN_MILLIS = 6L * 60 * 60 * 1000;
	public static final int IMPACT_MIN = 200;
	public static final int IMPACT_MAX = 500;
	public static final int MAX_IMPACT_TRIES = 8;
	public static final double ANNOUNCE_RADIUS = 600.0;
	public static final int STREAK_STEPS = 12;
	public static final int SMOKE_TICKS = 2400;
	public static final int SELL_XP = 400;
	public static final double SELL_RELIC_CHANCE = 0.25;
	public static final String[] SELL_SKILLS =
		{"MINING", "WOODCUTTING", "EXCAVATION", "FISHING"};

	private static final long NIGHT_START = 13_000L;
	private static final long NIGHT_END = 23_000L;
	private static final double CRATER_RADIUS = 3.5;
	private static final double SHELL_RADIUS = 4.5;
	private static final double RIM_RADIUS = 5.0;
	private static final double ARC_HEIGHT = 40.0;
	private static final int GUARD_COUNT = 3;

	private static final Map<String, String> DIRECTION_WORDS = Map.of(
		"N", "north", "NE", "north-east", "E", "east", "SE", "south-east",
		"S", "south", "SW", "south-west", "W", "west", "NW", "north-west");

	private Starfall() {
	}

	/** True when a star may fall right now. */
	public static boolean canFall(final long worldTime, final double roll,
		final long now, final long cooldownUntil) {

		return worldTime >= NIGHT_START && worldTime <= NIGHT_END
			&& now >= cooldownUntil && roll < STARFALL_CHANCE;
	}

	/** A random horizontal impact offset within the 200-500 block band. */
	public static Offset impactOffset(final Random rng) {
		final double angle = rng.nextDouble() * 2 * Math.PI;
		final double distance =
			IMPACT_MIN + rng.nextDouble() * (IMPACT_MAX - IMPACT_MIN);
		return new Offset((int) Math.round(distance * Math.cos(angle)), 0,
			(int) Math.round(distance * Math.sin(angle)));
	}

	/** True for the ordinary natural terrain a star may strike and carve. */
	public static boolean isNaturalSurface(final Material material) {
		return NaturalTerrain.isSurface(material);
	}

	/** The hemispheric bowl carved to air, relative to the impact block. */
	public static List<Offset> craterOffsets() {
		final List<Offset> offsets = new ArrayList<>();
		forSphere((x, y, z, distSq) -> {
			if (y <= 0 && distSq <= CRATER_RADIUS * CRATER_RADIUS) {
				offsets.add(new Offset(x, y, z));
			}
		});
		return offsets;
	}

	/** The scorched shell just beneath and around the bowl. */
	public static List<Offset> shellOffsets() {
		final List<Offset> offsets = new ArrayList<>();
		forSphere((x, y, z, distSq) -> {
			if (y < 0 && distSq > CRATER_RADIUS * CRATER_RADIUS
				&& distSq <= SHELL_RADIUS * SHELL_RADIUS) {
				offsets.add(new Offset(x, y, z));
			}
		});
		return offsets;
	}

	/** The scorched surface ring around the bowl's mouth. */
	public static List<Offset> rimOffsets() {
		final List<Offset> offsets = new ArrayList<>();
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				final int distSq = x * x + z * z;
				if (distSq > CRATER_RADIUS * CRATER_RADIUS
					&& distSq <= RIM_RADIUS * RIM_RADIUS) {
					offsets.add(new Offset(x, 0, z));
				}
			}
		}
		return offsets;
	}

	/** Three jittered guard positions ringing the crater. */
	public static List<Offset> guardOffsets(final Random rng) {
		final List<Offset> offsets = new ArrayList<>();
		for (int i = 0; i < GUARD_COUNT; i++) {
			final double angle = 2 * Math.PI * i / GUARD_COUNT
				+ rng.nextDouble() * 0.6;
			final double radius = 4 + rng.nextDouble() * 2;
			offsets.add(new Offset((int) Math.round(radius * Math.cos(angle)),
				0, (int) Math.round(radius * Math.sin(angle))));
		}
		return offsets;
	}

	/** The sky path of the streak from near the witness to the impact. */
	public static List<Vec> streakPoints(final double sx, final double sy,
		final double sz, final double ix, final double iy, final double iz) {

		final List<Vec> points = new ArrayList<>();
		for (int i = 0; i < STREAK_STEPS; i++) {
			final double t = i / (double) (STREAK_STEPS - 1);
			final double lift = ARC_HEIGHT * 4 * t * (1 - t);
			points.add(new Vec(sx + (ix - sx) * t,
				sy + (iy - sy) * t + lift, sz + (iz - sz) * t));
		}
		return points;
	}

	/** The compass direction of the impact as a spoken word. */
	public static String directionPhrase(final double dx, final double dz) {
		return DIRECTION_WORDS.get(RelicCompass.cardinal(dx, dz));
	}

	private static void forSphere(final SphereVisitor visitor) {
		for (int x = -5; x <= 5; x++) {
			for (int y = -5; y <= 0; y++) {
				for (int z = -5; z <= 5; z++) {
					visitor.visit(x, y, z, x * x + y * y + z * z);
				}
			}
		}
	}

	@FunctionalInterface
	private interface SphereVisitor {
		void visit(int x, int y, int z, int distSq);
	}

	/** An integer block offset relative to the impact block. */
	public record Offset(int x, int y, int z) {
	}

	/** A point along the streak's sky path. */
	public record Vec(double x, double y, double z) {
	}
}

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

package com.adeptum.questai.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The tunables and pure decisions behind village events. Raids start only
 * early in the night so they always resolve before dawn, and the festival
 * window lies fully in daytime, so the two events can never overlap.
 */
public final class VillageEvents {

	public static final double RAID_CHANCE = 0.20;
	public static final double FESTIVAL_CHANCE = 0.10;
	public static final long RAID_WINDOW_MILLIS = 210_000L;
	public static final long FESTIVAL_WINDOW_MILLIS = 300_000L;
	public static final long RAID_COOLDOWN_MILLIS = 3L * 60 * 60 * 1000;
	public static final long FESTIVAL_COOLDOWN_MILLIS = 8L * 60 * 60 * 1000;
	public static final double EVENT_RADIUS = 48.0;

	private static final long NIGHT_START = 13_000L;
	private static final long RAID_LATEST_START = 19_000L;
	private static final long DAY_START = 1_000L;
	private static final long DAY_LATEST_START = 11_000L;

	private static final int MIN_RAIDERS = 6;
	private static final int MAX_RAIDERS = 10;
	private static final double SPAWN_RADIUS = 30.0;
	private static final double SPAWN_JITTER = 4.0;
	private static final double FESTIVAL_XP_MULTIPLIER = 1.5;

	/** Where one raider spawns, relative to the village center. */
	public record SpawnOffset(double x, double z) {
	}

	private VillageEvents() {
	}

	public static boolean canStartRaid(final long worldTime, final double roll) {
		return worldTime >= NIGHT_START && worldTime <= RAID_LATEST_START
			&& roll < RAID_CHANCE;
	}

	public static boolean canStartFestival(final long worldTime,
		final double roll) {

		return worldTime >= DAY_START && worldTime <= DAY_LATEST_START
			&& roll < FESTIVAL_CHANCE;
	}

	public static int raiderCount(final Random rng) {
		return MIN_RAIDERS + rng.nextInt(MAX_RAIDERS - MIN_RAIDERS + 1);
	}

	/** Evenly spaced spawn points on a jittered ring around the center. */
	public static List<SpawnOffset> spawnRing(final int count, final Random rng) {
		final List<SpawnOffset> offsets = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			final double angle = 2 * Math.PI * i / count
				+ rng.nextDouble() * 0.4 - 0.2;
			final double radius = SPAWN_RADIUS
				+ rng.nextDouble() * 2 * SPAWN_JITTER - SPAWN_JITTER;
			offsets.add(new SpawnOffset(
				radius * Math.cos(angle), radius * Math.sin(angle)));
		}
		return offsets;
	}

	/** Short phrase describing the attackers, used in memory titles. */
	public static String raidTitle(final int count) {
		return "a horde of " + count + " walking dead";
	}

	/** Quest reward XP with the festival bonus applied. */
	public static int festivalXp(final int xp) {
		return (int) Math.round(xp * FESTIVAL_XP_MULTIPLIER);
	}
}

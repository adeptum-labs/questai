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

package com.adeptum.questai.mob;

import lombok.Getter;

/**
 * The custom mob variants forged from natural spawns. Attribute values are
 * plain doubles so this enum stays free of registry-backed Bukkit types
 * and safe to load in tests; the forge applies them to live entities.
 */
@Getter
public enum MobVariant {

	/** A towering zombie: slow, hard-hitting and hard to budge. */
	GRAVEHULK("gravehulk", "\u00a74Gravehulk", 0.08, 20,
		new Combat(2.0, 40.0, 0.18, 7.0, 0.6)),

	/** A knee-high zombie that hunts in swarms. */
	GRAVELING("graveling", "\u00a72Graveling", 0.01, 2,
		new Combat(0.5, 8.0, 0.32, 2.0, 0.0)),

	/** A small blaze-touched spider that sets its prey alight. */
	CINDERLING("cinderling", "\u00a76Cinderling", 0.05, 8,
		new Combat(0.7, 12.0, 0.38, 2.0, 0.0));

	private final String id;
	private final String displayName;
	private final double dropChance;
	private final int bonusXp;
	private final Combat combat;

	MobVariant(final String id, final String displayName,
		final double dropChance, final int bonusXp, final Combat combat) {

		this.id = id;
		this.displayName = displayName;
		this.dropChance = dropChance;
		this.bonusXp = bonusXp;
		this.combat = combat;
	}

	/** Resolves a stored variant id, or null when unknown. */
	public static MobVariant fromId(final String id) {
		for (final MobVariant variant : values()) {
			if (variant.id.equals(id)) {
				return variant;
			}
		}
		return null;
	}

	/** The combat profile applied to a forged mob. */
	public record Combat(double scale, double maxHealth, double movementSpeed,
		double attackDamage, double knockbackResistance) {
	}
}

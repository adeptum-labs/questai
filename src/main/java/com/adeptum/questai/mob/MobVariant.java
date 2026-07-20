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

import java.util.List;
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
		new Combat(2.0, 40.0, 0.18, 7.0, 0.6),
		new Voice(
			// Pitch bottoms out at 0.5, so the weight comes from the ravager
			// and the heartbeat underneath rather than from pitch alone
			List.of(new Layer("entity.zombie.ambient", 1.2f, 0.5f),
				new Layer("entity.ravager.ambient", 0.9f, 0.5f),
				new Layer("entity.warden.heartbeat", 0.6f, 0.6f)),
			List.of(new Layer("entity.zombie.hurt", 1.2f, 0.5f),
				new Layer("entity.ravager.hurt", 0.8f, 0.6f)),
			List.of(new Layer("entity.zombie.death", 1.3f, 0.5f),
				new Layer("entity.ravager.death", 0.9f, 0.5f)),
			new Layer("entity.ravager.step", 0.7f, 0.5f))),

	/** A knee-high zombie that hunts in swarms. */
	GRAVELING("graveling", "\u00a72Graveling", 0.01, 2,
		new Combat(0.5, 8.0, 0.32, 2.0, 0.0),
		new Voice(
			List.of(new Layer("entity.zombie.ambient", 0.6f, 1.8f),
				new Layer("entity.silverfish.ambient", 0.5f, 1.6f)),
			List.of(new Layer("entity.zombie.hurt", 0.6f, 2.0f),
				new Layer("entity.silverfish.hurt", 0.4f, 1.8f)),
			List.of(new Layer("entity.zombie.death", 0.6f, 1.9f)),
			null)),

	/** A small blaze-touched spider that sets its prey alight. */
	CINDERLING("cinderling", "\u00a76Cinderling", 0.05, 8,
		new Combat(0.7, 12.0, 0.38, 2.0, 0.0), null);

	private final String id;
	private final String displayName;
	private final double dropChance;
	private final int bonusXp;
	private final Combat combat;

	/** The replacement voice, or null to leave the mob its vanilla sounds. */
	private final Voice voice;

	MobVariant(final String id, final String displayName,
		final double dropChance, final int bonusXp, final Combat combat,
		final Voice voice) {

		this.id = id;
		this.displayName = displayName;
		this.dropChance = dropChance;
		this.bonusXp = bonusXp;
		this.combat = combat;
		this.voice = voice;
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

	/**
	 * A mob's replacement sounds. A forged mob is silenced so that its
	 * vanilla voice does not give away an ordinary zombie, which means
	 * everything it should still be heard doing has to be listed here.
	 *
	 * <p>The step is optional; a mob small enough not to shake the ground
	 * leaves it null.
	 */
	public record Voice(List<Layer> ambient, List<Layer> hurt,
		List<Layer> death, Layer step) {
	}

	/**
	 * One sound in a layer stack, named rather than typed so the table stays
	 * clear of registry-backed Bukkit types and a pack-supplied sound needs
	 * no code change. Pitch is clamped to 0.5-2.0 by the client.
	 */
	public record Layer(String sound, float volume, float pitch) {
	}
}

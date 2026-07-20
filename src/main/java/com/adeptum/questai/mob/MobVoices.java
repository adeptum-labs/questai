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

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Speaks for the forged mobs.
 *
 * <p>A mob's sounds are chosen client-side from its entity type, so the only
 * way to make a gravehulk sound unlike every other zombie is to silence it
 * and play its voice from here. That also means anything not played here is
 * simply not heard: silencing takes the hurt cry and the footsteps with it.
 */
public final class MobVoices {

	/** Range of the ambient sweep, matching the villager scan's reach. */
	private static final double RANGE = 32.0;

	/** Per-mob chance to speak on a sweep, so the growls stay irregular. */
	private static final double CHANCE = 0.35;

	/** Above this squared velocity the mob counts as walking. */
	private static final double STEP_VELOCITY_SQUARED = 0.005;

	private MobVoices() {
	}

	/** Silences a forged mob so its own voice can replace the shared one. */
	public static void silence(final LivingEntity mob, final MobVariant variant) {
		if (variant.getVoice() != null) {
			mob.setSilent(true);
		}
	}

	/**
	 * Lets every forged mob near a player growl. Sweeping outward from
	 * players rather than over whole worlds bounds the work to what anyone
	 * could hear anyway, and identity comes from the persistent tag, so a
	 * mob is still recognised after the server restarts.
	 */
	public static void sweep(final Random random) {
		final Set<UUID> spoken = new HashSet<>();
		for (final Player player : Bukkit.getOnlinePlayers()) {
			for (final Entity entity : player.getNearbyEntities(RANGE, RANGE, RANGE)) {
				// Two players in earshot must not double up the same growl
				if (spoken.add(entity.getUniqueId())) {
					ambient(entity, random);
				}
			}
		}
	}

	/** The wounded cry, replacing the one silencing took away. */
	public static void hurt(final Entity entity) {
		final MobVariant.Voice voice = voiceOf(entity);
		if (voice != null) {
			play(entity, voice.hurt());
		}
	}

	/** The death cry. The variant is already resolved by the drop handler. */
	public static void death(final Entity entity, final MobVariant variant) {
		if (variant.getVoice() != null) {
			play(entity, variant.getVoice().death());
		}
	}

	private static void ambient(final Entity entity, final Random random) {
		final MobVariant.Voice voice = voiceOf(entity);
		if (voice == null) {
			return;
		}
		if (random.nextDouble() < CHANCE) {
			play(entity, voice.ambient());
		}
		if (voice.step() != null
			&& entity.getVelocity().lengthSquared() > STEP_VELOCITY_SQUARED) {

			play(entity, List.of(voice.step()));
		}
	}

	private static MobVariant.Voice voiceOf(final Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return null;
		}
		final MobVariant variant =
			MobTags.variantOf(living.getPersistentDataContainer());
		return variant == null ? null : variant.getVoice();
	}

	/**
	 * Plays a stack of sounds at the mob. Under the hostile category so the
	 * player's own volume slider still governs them, as it would the vanilla
	 * growls these stand in for.
	 */
	private static void play(final Entity entity,
		final List<MobVariant.Layer> layers) {

		final Location location = entity.getLocation();
		for (final MobVariant.Layer layer : layers) {
			entity.getWorld().playSound(location, layer.sound(),
				SoundCategory.HOSTILE, layer.volume(), layer.pitch());
		}
	}
}

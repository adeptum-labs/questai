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

import com.adeptum.questai.SubPlugin;
import java.util.Map;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Forges rare natural spawns into custom mob variants and handles their
 * abilities and drops. This is the only class that touches the
 * registry-backed Attribute and Enchantment types, keeping the pure mob
 * logic loadable in tests.
 */
@Slf4j
public class MobForge implements SubPlugin {

	private static final int CINDERLING_FIRE_TICKS = 60;

	private static final Map<GearEnchant, Enchantment> ENCHANTMENTS =
		Map.ofEntries(
			Map.entry(GearEnchant.SHARPNESS, Enchantment.SHARPNESS),
			Map.entry(GearEnchant.FIRE_ASPECT, Enchantment.FIRE_ASPECT),
			Map.entry(GearEnchant.KNOCKBACK, Enchantment.KNOCKBACK),
			Map.entry(GearEnchant.POWER, Enchantment.POWER),
			Map.entry(GearEnchant.PUNCH, Enchantment.PUNCH),
			Map.entry(GearEnchant.UNBREAKING, Enchantment.UNBREAKING),
			Map.entry(GearEnchant.PROTECTION, Enchantment.PROTECTION),
			Map.entry(GearEnchant.PROJECTILE_PROTECTION,
				Enchantment.PROJECTILE_PROTECTION),
			Map.entry(GearEnchant.THORNS, Enchantment.THORNS),
			Map.entry(GearEnchant.QUICK_CHARGE, Enchantment.QUICK_CHARGE),
			Map.entry(GearEnchant.PIERCING, Enchantment.PIERCING));

	private final Random random = new Random();

	@Override
	public void onEnable() {
		log.atInfo().log("MobForge enabled; the night grows stranger.");
	}

	@Override
	public void onDisable() {
		log.atInfo().log("MobForge disabled.");
	}

	@EventHandler(ignoreCancelled = true)
	public void onCreatureSpawn(final CreatureSpawnEvent event) {
		// NATURAL-only also excludes raid zombies and swarm mates, which
		// spawn via world.spawn and therefore arrive as CUSTOM
		if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
			return;
		}
		final MobVariant variant = rollFor(event.getEntityType());
		if (variant != null) {
			upgrade(event.getEntity(), variant);
			if (variant == MobVariant.GRAVELING) {
				spawnSwarmMates(event.getEntity());
			}
		}
	}

	/** Forges an already-spawned entity into the given variant. */
	public void forge(final LivingEntity mob, final MobVariant variant) {
		upgrade(mob, variant);
	}

	private MobVariant rollFor(final EntityType type) {
		// Exact types keep husks, drowned and cave spiders vanilla
		return switch (type) {
			case ZOMBIE -> MobRoll.rollZombie(random);
			case SPIDER -> MobRoll.rollSpider(random);
			default -> null;
		};
	}

	private void upgrade(final LivingEntity mob, final MobVariant variant) {
		if (mob instanceof Zombie zombie) {
			// A natural baby would stack its own size and speed onto ours
			zombie.setAdult();
		}
		applyAttributes(mob, variant);

		final AttributeInstance maxHealth =
			mob.getAttribute(Attribute.MAX_HEALTH);
		if (maxHealth != null) {
			mob.setHealth(maxHealth.getValue());
		}

		mob.customName(Component.text(variant.getDisplayName()));
		mob.setCustomNameVisible(true);
		MobTags.tag(mob.getPersistentDataContainer(), variant);

		if (variant == MobVariant.CINDERLING) {
			mob.setVisualFire(true);
		}
	}

	private void applyAttributes(final LivingEntity mob, final MobVariant variant) {
		final MobVariant.Combat combat = variant.getCombat();
		setBase(mob, Attribute.SCALE, combat.scale());
		setBase(mob, Attribute.MAX_HEALTH, combat.maxHealth());
		setBase(mob, Attribute.MOVEMENT_SPEED, combat.movementSpeed());
		setBase(mob, Attribute.ATTACK_DAMAGE, combat.attackDamage());
		setBase(mob, Attribute.KNOCKBACK_RESISTANCE,
			combat.knockbackResistance());
	}

	private void setBase(final LivingEntity mob, final Attribute attribute,
		final double value) {

		final AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private void spawnSwarmMates(final LivingEntity leader) {
		final int extras = MobRoll.swarmSize(random) - 1;
		for (int i = 0; i < extras; i++) {
			final Location loc = leader.getLocation().clone().add(
				(random.nextDouble() - 0.5) * 3, 0,
				(random.nextDouble() - 0.5) * 3);
			// world.spawn arrives as CUSTOM, so mates are never re-rolled
			leader.getWorld().spawn(loc, Zombie.class,
				mate -> upgrade(mate, MobVariant.GRAVELING));
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityDamage(final EntityDamageByEntityEvent event) {
		if (isCinderling(event.getDamager())
			&& event.getEntity() instanceof LivingEntity target
			&& target.getFireTicks() < CINDERLING_FIRE_TICKS) {
			target.setFireTicks(CINDERLING_FIRE_TICKS);
		}
	}

	private boolean isCinderling(final Entity damager) {
		return damager instanceof LivingEntity living
			&& MobTags.variantOf(living.getPersistentDataContainer())
				== MobVariant.CINDERLING;
	}

	@EventHandler
	public void onEntityDeath(final EntityDeathEvent event) {
		final MobVariant variant = MobTags.variantOf(
			event.getEntity().getPersistentDataContainer());
		if (variant == null) {
			return;
		}

		event.setDroppedExp(event.getDroppedExp() + variant.getBonusXp());
		final MobDrops.GearRoll roll =
			MobDrops.roll(random, variant.getDropChance());
		if (roll != null) {
			event.getDrops().add(buildGear(roll));
		}
	}

	private ItemStack buildGear(final MobDrops.GearRoll roll) {
		final ItemStack item = new ItemStack(roll.item().getMaterial());
		for (final Map.Entry<GearEnchant, Integer> entry
			: roll.enchants().entrySet()) {
			// GearItem already constrains applicability, so the registry
			// compatibility check is unnecessary
			item.addUnsafeEnchantment(
				ENCHANTMENTS.get(entry.getKey()), entry.getValue());
		}
		return item;
	}
}

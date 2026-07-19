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

import com.adeptum.questai.SubPlugin;
import com.adeptum.questai.mob.MobForge;
import com.adeptum.questai.mob.MobVariant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Schedules the rare starfall: a visible streak across the night sky, a
 * crater carved only into natural terrain, a glowing Star Fragment and
 * three cinderling guards. All state is in-memory; the crater and the
 * fragment are intentionally permanent.
 */
public class StarfallManager implements SubPlugin {

	private static final long POLL_TICKS = 600L;
	private static final long STREAK_STEP_TICKS = 4L;

	private final JavaPlugin plugin;
	private final MobForge mobForge;

	private long cooldownUntil;
	private BukkitTask pollTask;
	private BukkitTask smokeTask;
	private Chunk ticketedChunk;

	public StarfallManager(final JavaPlugin plugin, final MobForge mobForge) {
		this.plugin = plugin;
		this.mobForge = mobForge;
	}

	@Override
	public void onEnable() {
		pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::poll,
			POLL_TICKS, POLL_TICKS);
	}

	@Override
	public void onDisable() {
		pollTask.cancel();
		if (smokeTask != null) {
			smokeTask.cancel();
		}
		releaseChunkTicket();
	}

	private void poll() {
		final Player anchor = pickAnchor();
		if (anchor == null || !Starfall.canFall(anchor.getWorld().getTime(),
			ThreadLocalRandom.current().nextDouble(),
			System.currentTimeMillis(), cooldownUntil)) {
			return;
		}

		final Location impact = findImpact(anchor);
		if (impact != null) {
			cooldownUntil = System.currentTimeMillis()
				+ Starfall.COOLDOWN_MILLIS;
			beginStreak(anchor, impact);
		}
	}

	private Player pickAnchor() {
		final List<? extends Player> players =
			List.copyOf(Bukkit.getOnlinePlayers()).stream()
				.filter(p -> p.getWorld().getEnvironment()
					== World.Environment.NORMAL)
				.toList();
		return players.isEmpty() ? null
			: players.get(ThreadLocalRandom.current().nextInt(players.size()));
	}

	/**
	 * Rolls up to eight candidate points and keeps the first whose surface
	 * is ordinary natural terrain; a starless night otherwise.
	 */
	private Location findImpact(final Player anchor) {
		final World world = anchor.getWorld();
		for (int i = 0; i < Starfall.MAX_IMPACT_TRIES; i++) {
			final Starfall.Offset offset =
				Starfall.impactOffset(ThreadLocalRandom.current());
			final int x = anchor.getLocation().getBlockX() + offset.x();
			final int z = anchor.getLocation().getBlockZ() + offset.z();
			world.getChunkAt(x >> 4, z >> 4).load(true);

			final int y = world.getHighestBlockYAt(x, z);
			if (y > world.getMinHeight() && Starfall.isNaturalSurface(
				world.getBlockAt(x, y, z).getType())) {
				return new Location(world, x + 0.5, y, z + 0.5);
			}
		}
		return null;
	}

	private void beginStreak(final Player anchor, final Location impact) {
		final Location start = anchor.getLocation();
		final List<Starfall.Vec> points = Starfall.streakPoints(
			start.getX(), start.getY() + 90, start.getZ(),
			impact.getX(), impact.getY(), impact.getZ());
		final World world = impact.getWorld();

		new BukkitRunnable() {
			private int step;

			@Override
			public void run() {
				final Starfall.Vec point = points.get(step);
				world.spawnParticle(Particle.END_ROD, point.x(), point.y(),
					point.z(), 20, 0.6, 0.6, 0.6, 0.02, null, true);
				world.spawnParticle(Particle.FIREWORK, point.x(), point.y(),
					point.z(), 10, 0.4, 0.4, 0.4, 0.05, null, true);
				step++;
				if (step >= points.size()) {
					cancel();
					impact(impact);
				}
			}
		}.runTaskTimer(plugin, 0L, STREAK_STEP_TICKS);
	}

	private void impact(final Location center) {
		final ThreadLocalRandom rng = ThreadLocalRandom.current();
		carveCrater(center, rng);
		dropFragment(center);
		spawnGuards(center, rng);
		announce(center);
		beginSmoke(center);

		ticketedChunk = center.getChunk();
		ticketedChunk.addPluginChunkTicket(plugin);
	}

	/**
	 * Carves the bowl and scorches shell and rim, replacing only blocks
	 * that pass the natural-terrain whitelist so player builds, ores,
	 * liquids and tile entities always survive.
	 */
	private void carveCrater(final Location center, final Random rng) {
		for (final Starfall.Offset offset : Starfall.craterOffsets()) {
			replaceNatural(center, offset, Material.AIR);
		}
		for (final Starfall.Offset offset : Starfall.shellOffsets()) {
			replaceNatural(center, offset, scorched(rng));
		}
		scorchRim(center, rng);

		// Guarantee the fragment a solid resting place
		final Location bottom = center.clone().add(0, -4, 0);
		if (!bottom.getBlock().getType().isSolid()) {
			bottom.getBlock().setType(Material.BLACKSTONE);
		}
	}

	private void scorchRim(final Location center, final Random rng) {
		for (final Starfall.Offset offset : Starfall.rimOffsets()) {
			final Material material = scorched(rng);
			if (replaceNatural(center, offset, material)
				&& material == Material.MAGMA_BLOCK) {
				igniteAbove(center, offset);
			}
		}
	}

	private boolean replaceNatural(final Location center,
		final Starfall.Offset offset, final Material material) {

		final Location loc =
			center.clone().add(offset.x(), offset.y(), offset.z());
		if (Starfall.isNaturalSurface(loc.getBlock().getType())) {
			loc.getBlock().setType(material);
			return true;
		}
		return false;
	}

	private Material scorched(final Random rng) {
		final double roll = rng.nextDouble();
		if (roll < 0.55) {
			return Material.COBBLED_DEEPSLATE;
		}
		return roll < 0.9 ? Material.BLACKSTONE : Material.MAGMA_BLOCK;
	}

	private void igniteAbove(final Location center, final Starfall.Offset offset) {
		final Location above =
			center.clone().add(offset.x(), offset.y() + 1, offset.z());
		if (above.getBlock().getType() == Material.AIR) {
			above.getBlock().setType(Material.FIRE);
		}
	}

	private void dropFragment(final Location center) {
		final Location rest = center.clone().add(0, 1, 0);
		final Item item = center.getWorld().dropItem(rest,
			StarFragment.create());
		item.setGlowing(true);
		item.setUnlimitedLifetime(true);
		item.setWillAge(false);
		item.setInvulnerable(true);
	}

	@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
	private void spawnGuards(final Location center, final Random rng) {
		final World world = center.getWorld();
		for (final Starfall.Offset offset : Starfall.guardOffsets(rng)) {
			final int x = center.getBlockX() + offset.x();
			final int z = center.getBlockZ() + offset.z();
			final Location loc = new Location(world, x + 0.5,
				world.getHighestBlockYAt(x, z) + 1, z + 0.5);
			world.spawn(loc, Spider.class,
				spider -> mobForge.forge(spider, MobVariant.CINDERLING));
		}
	}

	@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
	private void announce(final Location impact) {
		final double radiusSq =
			Starfall.ANNOUNCE_RADIUS * Starfall.ANNOUNCE_RADIUS;
		for (final Player player : impact.getWorld().getPlayers()) {
			final Location loc = player.getLocation();
			final double dx = impact.getX() - loc.getX();
			final double dz = impact.getZ() - loc.getZ();
			if (dx * dx + dz * dz > radiusSq) {
				continue;
			}

			// Play the boom from the impact's direction so it carries
			final Vector toward = new Vector(dx, 0, dz).normalize().multiply(12);
			final Location soundLoc = player.getEyeLocation().add(toward);
			player.playSound(soundLoc,
				Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.7f);
			player.playSound(soundLoc,
				Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.5f);
			player.sendMessage("\u00a7bSomething fell from the sky to the "
				+ Starfall.directionPhrase(dx, dz) + "!");
		}
	}

	private void beginSmoke(final Location center) {
		final Location smoke = center.clone().add(0, 1, 0);
		final World world = center.getWorld();
		smokeTask = new BukkitRunnable() {
			private int ticks;

			@Override
			public void run() {
				world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, smoke,
					3, 1.5, 0.5, 1.5, 0.01, null, true);
				world.spawnParticle(Particle.LAVA, smoke, 2, 1.5, 0.3, 1.5);
				ticks += 20;
				if (ticks >= Starfall.SMOKE_TICKS) {
					cancel();
					releaseChunkTicket();
				}
			}
		}.runTaskTimer(plugin, 20L, 20L);
	}

	@SuppressWarnings("PMD.NullAssignment")
	private void releaseChunkTicket() {
		if (ticketedChunk != null) {
			ticketedChunk.removePluginChunkTicket(plugin);
			ticketedChunk = null;
		}
	}
}

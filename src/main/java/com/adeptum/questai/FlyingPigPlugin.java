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

package com.adeptum.questai;

import com.adeptum.questai.resourcepack.ResourcePackManager;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Pigs that hop about the sky as though the ground had been taken away and
 * most of the gravity with it.
 *
 * <p>The arc itself is {@link PigFlight}'s to decide; this class only reads
 * the world for it and puts the answer back. The wings are two display
 * entities riding the pig, because a vanilla client cannot be handed new
 * entity geometry — the same trick the rat's body uses.
 */
@Slf4j
public class FlyingPigPlugin implements SubPlugin {

	private static final Component FLYING_PIG_NAME = Component.text("Flying Pig");

	/** Fast enough that an arc reads as one movement, cheap enough to keep. */
	private static final long FLIGHT_INTERVAL = 2L;

	/** How far down to look for the ground a hop pushes off from. */
	private static final int GROUND_SEARCH = 64;

	/** Ticks of rest between hops, so they do not bounce like a spring. */
	private static final int REST_MIN = 20;
	private static final int REST_RANGE = 60;

	/** How hard a pig drifts sideways while it is up there. */
	private static final double DRIFT = 0.08;
	private static final double DRIFT_CHANCE = 0.06;

	private static final NamespacedKey WING_KEY =
		new NamespacedKey("questai", "pig_wing");
	private static final NamespacedKey RESTED_KEY =
		new NamespacedKey("questai", "pig_rested_at");

	/** How far out from the spine each wing sits, and how high up the back. */
	private static final float WING_OUT = 0.32f;
	private static final float WING_UP = 0.62f;
	private static final float WING_SCALE = 0.75f;

	/** The two poses: wings driven down on a beat, held out on a glide. */
	private static final float BEAT_ANGLE = (float) Math.toRadians(-38);
	private static final float GLIDE_ANGLE = (float) Math.toRadians(6);

	/** Ticks the client is given to tween from one pose to the other. */
	private static final int FLAP_TWEEN = 4;

	private final Random random = new Random();
	private final JavaPlugin plugin;

	// Define a set of grass biomes where flying pigs can spawn
	private static final Set<Biome> PIG_BIOMES = Set.of(
		Biome.PLAINS,
		Biome.FOREST,
		Biome.BIRCH_FOREST,
		Biome.FLOWER_FOREST,
		Biome.SUNFLOWER_PLAINS,
		Biome.TAIGA
	);

	public FlyingPigPlugin(JavaPlugin plugin) {
		super();
		this.plugin = plugin;
	}

	@Override
	public void onEnable() {
		log.atInfo().log("Flying Pigs Plugin Enabled!");
		new FlightTask().runTaskTimer(plugin, FLIGHT_INTERVAL, FLIGHT_INTERVAL);
	}

	@Override
	public void onDisable() {
		log.atInfo().log("Flying Pigs Plugin Disabled!");
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		if (!event.isNewChunk()) {
			return;
		}

		final World world = event.getWorld();

		if (world.getEnvironment() != World.Environment.NORMAL) {
			return;
		}

		final Location spawnLocation = event.getChunk().getBlock(8, 100, 8)
			.getLocation().add(0, random.nextInt(20), 0);

		final Biome biome = world.getBiome(spawnLocation.getBlockX(),
			spawnLocation.getBlockY(),
			spawnLocation.getBlockZ()
		);

		if (!PIG_BIOMES.contains(biome)) {
			return; // Do not spawn if the biome is not a "pig biome"
		}

		if (random.nextDouble() < 0.03) { // 3% chance per chunk load
			spawnFlyingPig(world, spawnLocation);
		}
	}

	private void spawnFlyingPig(World world, Location location) {
		Pig pig = (Pig) world.spawnEntity(location, EntityType.PIG);
		pig.customName(FLYING_PIG_NAME);
		pig.setCustomNameVisible(true);
		// The fall is this plugin's to apply, a fraction of the real one
		pig.setGravity(false);
		fitWings(pig);
	}

	/**
	 * Gives a pig its pair of wings. Both sides wear the same model; the far
	 * one is turned through half a circle, which mirrors it about the spine
	 * so a single set of cubes serves them both.
	 */
	private void fitWings(final Pig pig) {
		for (final int side : new int[] {1, -1}) {
			final ItemDisplay wing = pig.getWorld().spawn(pig.getLocation(),
				ItemDisplay.class, display -> {
					display.setItemStack(wingItem());
					display.getPersistentDataContainer()
						.set(WING_KEY, PersistentDataType.INTEGER, side);
					display.setInterpolationDuration(FLAP_TWEEN);
				});
			pose(wing, side, GLIDE_ANGLE);
			pig.addPassenger(wing);
		}
	}

	private static ItemStack wingItem() {
		final ItemStack stack = new ItemStack(Material.PHANTOM_MEMBRANE);
		final ItemMeta meta = stack.getItemMeta();
		meta.setCustomModelData(ResourcePackManager.PIG_WING_CMD);
		stack.setItemMeta(meta);
		return stack;
	}

	/**
	 * Sets one wing's pose: out to its own side of the spine, tilted by the
	 * beat angle. The client tweens there over {@link #FLAP_TWEEN} ticks, so
	 * a pose only has to be sent when the stroke actually changes.
	 */
	private static void pose(final ItemDisplay wing, final int side,
		final float angle) {

		wing.setInterpolationDelay(0);
		wing.setTransformation(new Transformation(
			new Vector3f(WING_OUT * side, WING_UP, 0.0f),
			// Half a turn on the far side mirrors the same cubes across
			new AxisAngle4f(side > 0 ? 0.0f : (float) Math.PI, 0, 1, 0),
			new Vector3f(WING_SCALE, WING_SCALE, WING_SCALE),
			new AxisAngle4f(side * angle, 0, 0, 1)));
	}

	/** The side of the spine a display sits on, or null if it is no wing. */
	private static Integer wingSide(final Entity entity) {
		return entity instanceof ItemDisplay ? entity.getPersistentDataContainer()
			.get(WING_KEY, PersistentDataType.INTEGER) : null;
	}

	private static boolean isFlyingPig(final Pig pig) {
		return FLYING_PIG_NAME.equals(pig.customName());
	}

	/**
	 * Moves every flying pig one step along its arc, and keeps the wings and
	 * the world tidy around them.
	 */
	private final class FlightTask extends BukkitRunnable {

		@Override
		public void run() {
			for (final World world : Bukkit.getWorlds()) {
				for (final Pig pig : world.getEntitiesByClass(Pig.class)) {
					if (isFlyingPig(pig)) {
						fly(pig);
					}
				}
				sweepStrayWings(world);
			}
		}

		/**
		 * Reads the air under one pig, asks {@link PigFlight} what happens
		 * next, and writes it back — beating the wings only on the way up.
		 */
		private void fly(final Pig pig) {
			final List<Entity> wings = pig.getPassengers();
			if (wings.isEmpty()) {
				// Grew up before there were wings to be had, or lost them
				fitWings(pig);
				return;
			}

			final Vector velocity = pig.getVelocity();
			final double rise = PigFlight.nextRise(velocity.getY(),
				clearanceUnder(pig), rested(pig));
			if (rise == PigFlight.HOP) {
				stampRest(pig);
			}
			pig.setVelocity(drift(velocity).setY(rise));
			flap(wings, PigFlight.beating(rise));
		}

		/** Nudges the sideways drift now and then, so an arc travels. */
		private Vector drift(final Vector velocity) {
			if (random.nextDouble() >= DRIFT_CHANCE) {
				return velocity;
			}
			return velocity.setX((random.nextDouble() - 0.5) * DRIFT)
				.setZ((random.nextDouble() - 0.5) * DRIFT);
		}

		/** How much open air lies between a pig and the first solid thing. */
		private double clearanceUnder(final Pig pig) {
			final Location at = pig.getLocation();
			final World world = at.getWorld();
			final int floor = Math.max(world.getMinHeight(),
				at.getBlockY() - GROUND_SEARCH);
			for (int y = at.getBlockY() - 1; y >= floor; y--) {
				if (!world.getBlockAt(at.getBlockX(), y, at.getBlockZ())
					.isPassable()) {

					return at.getY() - (y + 1);
				}
			}
			// Nothing beneath it at all: treat as open sky, so it drifts down
			return PigFlight.CEILING;
		}

		/** Whether this pig has waited out its rest between hops. */
		private boolean rested(final Pig pig) {
			final Integer at = pig.getPersistentDataContainer()
				.get(RESTED_KEY, PersistentDataType.INTEGER);
			return at == null || pig.getTicksLived() >= at;
		}

		private void stampRest(final Pig pig) {
			pig.getPersistentDataContainer().set(RESTED_KEY,
				PersistentDataType.INTEGER, pig.getTicksLived()
					+ REST_MIN + random.nextInt(REST_RANGE));
		}

		/**
		 * Drives the wings down on a beat and lets them out flat otherwise.
		 * The pose only goes out when the stroke changes, so a whole glide
		 * costs nothing.
		 */
		private void flap(final List<Entity> wings, final boolean beating) {
			for (final Entity passenger : wings) {
				final Integer side = wingSide(passenger);
				if (side != null) {
					pose((ItemDisplay) passenger, side,
						beating ? BEAT_ANGLE : GLIDE_ANGLE);
				}
			}
		}

		/**
		 * Clears wings whose pig is gone. Display entities outlive the mob
		 * they rode, so without this a despawn or a restart would leave a
		 * pair of them hanging in the sky.
		 */
		private void sweepStrayWings(final World world) {
			for (final ItemDisplay display
				: world.getEntitiesByClass(ItemDisplay.class)) {

				if (wingSide(display) != null && isStray(display)) {
					display.remove();
				}
			}
		}

		private boolean isStray(final ItemDisplay wing) {
			final Entity carrier = wing.getVehicle();
			return carrier == null || carrier.isDead()
				|| !(carrier instanceof Pig pig) || !isFlyingPig(pig);
		}
	}
}

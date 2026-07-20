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
import com.adeptum.questai.resourcepack.ResourcePackManager;
import com.adeptum.questai.utility.SpawnGround;
import com.adeptum.questai.village.VillageScanner;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Keeps a thin population of rats scurrying underground and around
 * villages.
 *
 * <p>A rat is an invisible silverfish carrying a display entity that shows
 * the pack's cube-built rat model: the silverfish contributes low, fast,
 * floor-hugging movement and a hitbox, the display the look. Rats never
 * fight back — their retaliation is cancelled at the targeting stage and
 * their bite is zeroed besides — and they spawn without regard to light,
 * which is what separates vermin from monsters.
 */
public class RatSpawner implements SubPlugin {

	private static final long SPAWN_INTERVAL_TICKS = 600L;
	private static final long FACING_INTERVAL_TICKS = 3L;

	/** At most this many rats within earshot of any one player. */
	private static final int CAP_NEARBY = 3;
	private static final double CAP_RANGE = 32.0;

	private static final double SPAWN_DISTANCE_MIN = 12.0;
	private static final double SPAWN_DISTANCE_SPREAD = 12.0;

	/** Blocks below the surface heightmap before a spot counts as a cave. */
	private static final int MIN_DEPTH = 5;

	/** Vertical wobble around the player's own level when caving. */
	private static final int DEPTH_JITTER = 5;

	/** Lifts the model from the display's centre onto the carrier's back. */
	private static final float MODEL_RAISE = 0.35f;

	private final JavaPlugin plugin;
	private final MobForge forge;
	private final VillageScanner scanner;
	private final Random random = new Random();
	private final boolean enabled;

	private BukkitTask spawnTask;
	private BukkitTask facingTask;

	public RatSpawner(final JavaPlugin plugin, final MobForge forge,
		final VillageScanner scanner) {

		this.plugin = plugin;
		this.forge = forge;
		this.scanner = scanner;
		this.enabled = plugin.getConfig().getBoolean("rats.enabled", true);
	}

	@Override
	public void onEnable() {
		if (!enabled) {
			return;
		}
		spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnSweep,
			SPAWN_INTERVAL_TICKS, SPAWN_INTERVAL_TICKS);
		facingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::faceSweep,
			FACING_INTERVAL_TICKS, FACING_INTERVAL_TICKS);
	}

	@Override
	public void onDisable() {
		if (spawnTask != null) {
			spawnTask.cancel();
		}
		if (facingTask != null) {
			facingTask.cancel();
		}
	}

	/** Rats run, hide or ignore you; they never square up. */
	@EventHandler(ignoreCancelled = true)
	public void onTarget(final EntityTargetEvent event) {
		if (isRat(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onDeath(final EntityDeathEvent event) {
		if (!isRat(event.getEntity())) {
			return;
		}
		for (final Entity passenger : event.getEntity().getPassengers()) {
			if (passenger instanceof ItemDisplay) {
				passenger.remove();
			}
		}
		event.getDrops().add(RatMeat.create());
	}

	private void spawnSweep() {
		for (final Player player : Bukkit.getOnlinePlayers()) {
			if (ratsNear(player) < CAP_NEARBY) {
				trySpawnNear(player);
			}
		}
	}

	private int ratsNear(final Player player) {
		int count = 0;
		for (final Entity entity
			: player.getNearbyEntities(CAP_RANGE, CAP_RANGE, CAP_RANGE)) {
			if (isRat(entity)) {
				count++;
			}
		}
		return count;
	}

	private void trySpawnNear(final Player player) {
		final Location base = player.getLocation();
		final World world = player.getWorld();
		final double angle = random.nextDouble() * 2 * Math.PI;
		final double distance =
			SPAWN_DISTANCE_MIN + random.nextDouble() * SPAWN_DISTANCE_SPREAD;
		final int x = base.getBlockX()
			+ (int) Math.round(distance * Math.cos(angle));
		final int z = base.getBlockZ()
			+ (int) Math.round(distance * Math.sin(angle));

		final Location spot = scanner.hasVillagersNearby(base)
			? SpawnGround.findSurface(world, x, z)
			: caveSpot(world, x, base.getBlockY(), z);
		if (spot != null) {
			spawnRat(spot);
		}
	}

	/**
	 * A floor near the player's own depth, kept only when genuinely under
	 * the surface. Deliberately no light check: rats colonise lit mines
	 * and dark caves alike.
	 */
	private Location caveSpot(final World world, final int x, final int y,
		final int z) {

		final int wobble = random.nextInt(DEPTH_JITTER * 2 + 1) - DEPTH_JITTER;
		final Location spot = SpawnGround.findNear(world, x, y + wobble, z);
		if (spot == null
			|| spot.getBlockY() >= world.getHighestBlockYAt(x, z) - MIN_DEPTH) {
			return null;
		}
		return spot;
	}

	private void spawnRat(final Location spot) {
		final Silverfish carrier = spot.getWorld().spawn(spot, Silverfish.class,
			rat -> {
				forge.forge(rat, MobVariant.RAT);
				// An ambient creature wears no floating nameplate, stays
				// unseen behind the model, and turns over naturally
				rat.setCustomNameVisible(false);
				rat.setInvisible(true);
				rat.setRemoveWhenFarAway(true);
			});
		carrier.addPassenger(spawnBody(spot));
	}

	private ItemDisplay spawnBody(final Location spot) {
		return spot.getWorld().spawn(spot, ItemDisplay.class, display -> {
			display.setItemStack(bodyItem());
			display.setTransformation(new Transformation(
				new Vector3f(0.0f, MODEL_RAISE, 0.0f), new AxisAngle4f(),
				new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f()));
			// The same tag as the carrier, so a sweep can find strays
			MobTags.tag(display.getPersistentDataContainer(), MobVariant.RAT);
		});
	}

	private static ItemStack bodyItem() {
		final ItemStack stack = new ItemStack(Material.POISONOUS_POTATO);
		final ItemMeta meta = stack.getItemMeta();
		meta.setCustomModelData(ResourcePackManager.RAT_BODY_CMD);
		stack.setItemMeta(meta);
		return stack;
	}

	/**
	 * Turns every rat model to face where its carrier runs, and removes any
	 * model whose carrier is gone — which also mops up after despawns and
	 * restarts, so no separate orphan sweep is needed.
	 */
	private void faceSweep() {
		for (final World world : Bukkit.getWorlds()) {
			for (final ItemDisplay display
				: world.getEntitiesByClass(ItemDisplay.class)) {
				if (isRat(display)) {
					steer(display);
				}
			}
		}
	}

	private static void steer(final ItemDisplay display) {
		final Entity carrier = display.getVehicle();
		if (carrier == null || carrier.isDead()) {
			display.remove();
		} else {
			display.setRotation(carrier.getLocation().getYaw(), 0.0f);
		}
	}

	/** True for both the carrier and its display, which share the tag. */
	private static boolean isRat(final Entity entity) {
		if (entity instanceof LivingEntity || entity instanceof ItemDisplay) {
			return MobTags.variantOf(entity.getPersistentDataContainer())
				== MobVariant.RAT;
		}
		return false;
	}
}

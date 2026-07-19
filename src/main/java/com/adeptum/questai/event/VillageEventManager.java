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

import com.adeptum.questai.SubPlugin;
import com.adeptum.questai.model.VillageInfo;
import com.adeptum.questai.villager.MemoryEvent;
import com.adeptum.questai.villager.StoredLocation;
import com.adeptum.questai.villager.VillagerProfileStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Raises and resolves village events. Triggering piggybacks on the
 * periodic village scan; all event state is in-memory and raiders are
 * never persisted, so nothing can leak across restarts.
 */
public class VillageEventManager implements SubPlugin, VillageCheckListener {

	private final JavaPlugin plugin;
	private final VillagerProfileStore profileStore;

	private final Map<VillageKey, RaidState> raids = new HashMap<>();
	private final Map<VillageKey, FestivalState> festivals = new HashMap<>();
	private final Map<VillageKey, Long> raidCooldownUntil = new HashMap<>();
	private final Map<VillageKey, Long> festivalCooldownUntil = new HashMap<>();
	private BukkitTask task;

	public VillageEventManager(final JavaPlugin plugin,
		final VillagerProfileStore profileStore) {

		this.plugin = plugin;
		this.profileStore = profileStore;
	}

	@Override
	public void onEnable() {
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
	}

	@Override
	public void onDisable() {
		task.cancel();
		raids.values().forEach(this::removeRaiders);
		raids.clear();
	}

	@Override
	public void onVillageCheck(final Player player, final Location location,
		final VillageInfo info) {

		tryStartRaid(player, location);
		tryStartFestival(location);
	}

	/** True when a quest completed at this location earns the festival bonus. */
	public boolean festivalBoostApplies(final Location loc) {
		final long now = System.currentTimeMillis();
		for (final FestivalState festival : festivals.values()) {
			if (festival.active(now) && festival.covers(
				loc.getWorld().getUID(), loc.getX(), loc.getZ())) {
				return true;
			}
		}
		return false;
	}

	private void tryStartRaid(final Player player, final Location loc) {
		final World world = player.getWorld();
		if (!VillageEvents.canStartRaid(world.getTime(),
			ThreadLocalRandom.current().nextDouble())
			|| raidActiveNear(world.getUID(), loc)
			|| onCooldown(raidCooldownUntil, world.getUID(), loc)) {
			return;
		}

		// Without a named villager nearby, victory could reward nothing
		final List<Villager> defenders = profiledVillagersNear(loc);
		if (!defenders.isEmpty()) {
			startRaid(loc, defenders);
		}
	}

	private void tryStartFestival(final Location loc) {
		final World world = loc.getWorld();
		final VillageKey key = VillageKey.from(world.getUID(),
			loc.getBlockX(), loc.getBlockZ());
		if (!VillageEvents.canStartFestival(world.getTime(),
			ThreadLocalRandom.current().nextDouble())
			|| festivals.containsKey(key)
			|| onCooldown(festivalCooldownUntil, world.getUID(), loc)
			|| profiledVillagersNear(loc).size() < 2) {
			return;
		}

		festivals.put(key, new FestivalState(key, StoredLocation.from(loc),
			System.currentTimeMillis() + VillageEvents.FESTIVAL_WINDOW_MILLIS));
		stampCooldown(festivalCooldownUntil, key,
			VillageEvents.FESTIVAL_COOLDOWN_MILLIS);

		for (final Player nearby : playersNear(loc)) {
			nearby.sendMessage("\u00a76The villagers gather —"
				+ " a festival breaks out in the village!");
			nearby.playSound(loc, Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
			nearby.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.4f);
		}
	}

	private boolean raidActiveNear(final UUID worldId, final Location loc) {
		final double range = 2 * VillageEvents.EVENT_RADIUS;
		for (final RaidState raid : raids.values()) {
			if (raid.getCenter().worldId().equals(worldId)
				&& raid.getCenter().distanceSquaredXz(loc.getX(), loc.getZ())
					<= range * range) {
				return true;
			}
		}
		return false;
	}

	private boolean onCooldown(final Map<VillageKey, Long> cooldowns,
		final UUID worldId, final Location loc) {

		final Long until = cooldowns.get(
			VillageKey.from(worldId, loc.getBlockX(), loc.getBlockZ()));
		return until != null && until > System.currentTimeMillis();
	}

	private void stampCooldown(final Map<VillageKey, Long> cooldowns,
		final VillageKey key, final long cooldownMillis) {

		final long until = System.currentTimeMillis() + cooldownMillis;
		for (final VillageKey neighbor : key.neighborhood()) {
			cooldowns.put(neighbor, until);
		}
	}

	private void startRaid(final Location center,
		final List<Villager> defenders) {

		final World world = center.getWorld();
		final Set<UUID> raiderIds = spawnRaiders(center, defenders);

		final VillageKey key = VillageKey.from(world.getUID(),
			center.getBlockX(), center.getBlockZ());
		raids.put(key, new RaidState(key, StoredLocation.from(center),
			System.currentTimeMillis() + VillageEvents.RAID_WINDOW_MILLIS,
			raiderIds));
		stampCooldown(raidCooldownUntil, key, VillageEvents.RAID_COOLDOWN_MILLIS);

		for (final Player nearby : playersNear(center)) {
			nearby.sendMessage("\u00a74The dead close in on the village!");
			nearby.playSound(center, Sound.EVENT_RAID_HORN, 1.0f, 0.8f);
		}
	}

	@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
	private Set<UUID> spawnRaiders(final Location center,
		final List<Villager> defenders) {

		final World world = center.getWorld();
		final ThreadLocalRandom rng = ThreadLocalRandom.current();
		final Set<UUID> ids = new HashSet<>();

		for (final VillageEvents.SpawnOffset offset
			: VillageEvents.spawnRing(VillageEvents.raiderCount(rng), rng)) {

			final int x = (int) Math.floor(center.getX() + offset.x());
			final int z = (int) Math.floor(center.getZ() + offset.z());
			final Location spawnLoc = new Location(world, x + 0.5,
				world.getHighestBlockYAt(x, z) + 1, z + 0.5);

			final Zombie zombie = world.spawn(spawnLoc, Zombie.class,
				raider -> {
					raider.setAdult();
					raider.customName(Component.text("\u00a7cRaider"));
					raider.setCustomNameVisible(true);
					raider.setPersistent(false);
					raider.setRemoveWhenFarAway(false);
					raider.setShouldBurnInDay(false);
				});
			zombie.setTarget(defenders.get(rng.nextInt(defenders.size())));
			ids.add(zombie.getUniqueId());
		}
		return ids;
	}

	@EventHandler
	public void onEntityDeath(final EntityDeathEvent event) {
		final Entity entity = event.getEntity();
		for (final RaidState raid : raids.values()) {
			if (raid.onRaiderDeath(entity.getUniqueId())) {
				resolveIfFinished(raid);
				return;
			}
		}
		if (entity instanceof Villager villager
			&& profileStore.hasProfile(villager.getUniqueId())) {
			markVillagerLoss(villager);
		}
	}

	private void markVillagerLoss(final Villager villager) {
		final Location loc = villager.getLocation();
		final double radius = VillageEvents.EVENT_RADIUS;
		for (final RaidState raid : raids.values()) {
			if (raid.getCenter().worldId().equals(villager.getWorld().getUID())
				&& raid.getCenter().distanceSquaredXz(loc.getX(), loc.getZ())
					<= radius * radius) {
				raid.onVillagerLost();
			}
		}
	}

	private void resolveIfFinished(final RaidState raid) {
		final RaidState.Outcome outcome = raid.check(System.currentTimeMillis());
		if (outcome == RaidState.Outcome.ONGOING) {
			return;
		}
		raids.remove(raid.getKey());
		if (outcome == RaidState.Outcome.DEFENDED) {
			finishDefended(raid);
		} else {
			removeRaiders(raid);
		}
	}

	private void tick() {
		final long now = System.currentTimeMillis();
		final Iterator<RaidState> iterator = raids.values().iterator();
		while (iterator.hasNext()) {
			final RaidState raid = iterator.next();
			sweepVanishedRaiders(raid);

			final RaidState.Outcome outcome = raid.check(now);
			if (outcome == RaidState.Outcome.DEFENDED) {
				iterator.remove();
				finishDefended(raid);
			} else if (outcome == RaidState.Outcome.FAILED) {
				iterator.remove();
				removeRaiders(raid);
			}
		}
		tickFestivals(now);
	}

	private void tickFestivals(final long now) {
		final Iterator<FestivalState> iterator = festivals.values().iterator();
		while (iterator.hasNext()) {
			final FestivalState festival = iterator.next();
			if (festival.active(now)) {
				celebrateFestival(festival);
			} else {
				iterator.remove();
				announceFestivalEnd(festival);
			}
		}
	}

	private void celebrateFestival(final FestivalState festival) {
		final World world = Bukkit.getWorld(festival.center().worldId());
		if (world == null) {
			return;
		}
		final Location center = festival.center().toLocation();
		for (final Villager villager : profiledVillagersNear(center)) {
			world.spawnParticle(Particle.HAPPY_VILLAGER,
				villager.getEyeLocation(), 10, 0.5, 0.5, 0.5);
		}
		world.spawnParticle(Particle.NOTE, center.clone().add(0, 2, 0),
			5, 2, 1, 2);
		world.playSound(center, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f,
			0.8f + ThreadLocalRandom.current().nextFloat());
	}

	private void announceFestivalEnd(final FestivalState festival) {
		if (Bukkit.getWorld(festival.center().worldId()) != null) {
			for (final Player player
				: playersNear(festival.center().toLocation())) {
				player.sendMessage("\u00a77The festival winds down.");
			}
		}
	}

	private void sweepVanishedRaiders(final RaidState raid) {
		for (final UUID id : raid.aliveRaiders()) {
			final Entity entity = Bukkit.getEntity(id);
			if (entity == null || entity.isDead()) {
				raid.onRaiderVanished(id);
			}
		}
	}

	private void finishDefended(final RaidState raid) {
		final World world = Bukkit.getWorld(raid.getCenter().worldId());
		if (world == null) {
			return;
		}
		final Location center = raid.getCenter().toLocation();
		final String title = VillageEvents.raidTitle(raid.getRaiderCount());
		final List<Player> players = playersNear(center);

		for (final Villager villager : profiledVillagersNear(center)) {
			world.spawnParticle(Particle.HEART,
				villager.getEyeLocation(), 5, 0.3, 0.3, 0.3);
			world.playSound(villager.getLocation(),
				Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
			for (final Player player : players) {
				profileStore.recordEvent(villager.getUniqueId(),
					player.getUniqueId(),
					MemoryEvent.Type.DEFENDED_VILLAGE, title);
			}
		}
		for (final Player player : players) {
			player.sendMessage(
				"\u00a7aThe raid is broken! The villagers will remember this.");
			player.playSound(player.getLocation(),
				Sound.ENTITY_VILLAGER_CELEBRATE, 1.0f, 1.0f);
		}
	}

	private void removeRaiders(final RaidState raid) {
		for (final UUID id : raid.aliveRaiders()) {
			final Entity entity = Bukkit.getEntity(id);
			if (entity != null) {
				entity.remove();
			}
		}
	}

	private List<Villager> profiledVillagersNear(final Location loc) {
		final List<Villager> villagers = new ArrayList<>();
		for (final Entity entity : loc.getWorld().getNearbyEntities(loc,
			VillageEvents.EVENT_RADIUS, 16, VillageEvents.EVENT_RADIUS)) {
			if (entity instanceof Villager villager
				&& profileStore.hasProfile(villager.getUniqueId())) {
				villagers.add(villager);
			}
		}
		return villagers;
	}

	private List<Player> playersNear(final Location center) {
		final double radius = VillageEvents.EVENT_RADIUS;
		final List<Player> players = new ArrayList<>();
		for (final Player player : center.getWorld().getPlayers()) {
			if (player.getLocation().distanceSquared(center) <= radius * radius) {
				players.add(player);
			}
		}
		return players;
	}
}

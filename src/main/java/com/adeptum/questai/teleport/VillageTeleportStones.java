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

package com.adeptum.questai.teleport;

import com.adeptum.questai.SubPlugin;
import com.adeptum.questai.utility.SpawnGround;
import com.adeptum.questai.village.NamedVillage;
import com.adeptum.questai.village.VillageRegistry;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Grants and uses the Village Teleport Stone. A fully fortified village
 * offers one through dialogue ({@link #grant}); right-clicking it carries
 * the holder back to the village, behind a per-player cooldown.
 */
public class VillageTeleportStones implements SubPlugin {

	/**
	 * Why an item entity left the world. Only these mean the stone itself
	 * is gone; a chunk unloading, a player picking it up or a hopper
	 * swallowing it all remove the entity while the stone lives on, and
	 * treating those as a loss is exactly how a village ends up issuing a
	 * second one.
	 */
	private static final Set<EntityRemoveEvent.Cause> DESTROYED = EnumSet.of(
		EntityRemoveEvent.Cause.DEATH, EntityRemoveEvent.Cause.DESPAWN,
		EntityRemoveEvent.Cause.EXPLODE, EntityRemoveEvent.Cause.OUT_OF_WORLD,
		EntityRemoveEvent.Cause.DISCARD);

	private final JavaPlugin plugin;
	private final VillageRegistry registry;
	private final TeleportStoneStore stones;
	private final boolean enabled;
	private final long cooldownMillis;
	private final Map<UUID, Long> lastUse = new HashMap<>();

	public VillageTeleportStones(final JavaPlugin plugin,
		final VillageRegistry registry, final TeleportStoneStore stones) {

		this.plugin = plugin;
		this.registry = registry;
		this.stones = stones;
		this.enabled = plugin.getConfig()
			.getBoolean("villages.teleport.enabled", true);
		this.cooldownMillis = plugin.getConfig()
			.getInt("villages.teleport.cooldown-seconds", 120) * 1000L;
	}

	/**
	 * Whether this village's stone is already out there, and so whether the
	 * dialogue may offer another. The books are the answer; the world scan
	 * only ever adds to them.
	 */
	public boolean stoneIssued(final String rowId) {
		final String live = live(rowId);
		if (stones.issued(live)) {
			return true;
		}
		// A stone the books never knew about is adopted, not ignored
		return TeleportStoneCensus.exists(rowId) && stones.adopt(live, null);
	}

	/**
	 * The id the books are kept under for the village this one names. A stone
	 * carries whichever id its village had on the day it was handed over, and
	 * that row may since have been taken into another; filing it under the id
	 * on the item would leave a record nothing ever looks at again, while the
	 * living village went on believing its stone was still out there.
	 */
	private String live(final String rowId) {
		return registry.resolve(rowId);
	}

	/** False keeps the stone's claim entry out of the dialogue GUI. */
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void onEnable() {
		if (enabled) {
			// Once the worlds are up: anything the scan can already see and
			// the books have not heard of goes onto them before the first
			// villager gets a chance to offer a second one
			Bukkit.getScheduler().runTask(plugin, this::adoptStrayStones);
		}
	}

	@Override
	public void onDisable() {
		// Cooldowns are in-memory only; the books look after themselves
	}

	/**
	 * Takes every stone the world will admit to onto the books. At startup
	 * that is little — nobody is online and only the spawn chunks are up —
	 * so the same adoption runs again as each player joins and whenever a
	 * stone is used, which is where a stone that outlived the records
	 * actually surfaces.
	 */
	private void adoptStrayStones() {
		final int adopted = adopt(TeleportStoneCensus.found());
		plugin.getLogger().info("[VillageTeleportStones] " + stones.size()
			+ " village stone(s) on the books" + (adopted == 0 ? "."
				: ", " + adopted + " adopted from the world."));
	}

	/** Records any of these stones the books had not heard of. */
	private int adopt(final Map<String, UUID> found) {
		int adopted = 0;
		for (final Map.Entry<String, UUID> stone : found.entrySet()) {
			if (stones.adopt(live(stone.getKey()), stone.getValue())) {
				adopted++;
			}
		}
		return adopted;
	}

	/**
	 * A joining player brings their pockets into view for the first time.
	 * A stone that was issued before the books existed, or that sat out a
	 * migration in an ender chest, is adopted here.
	 */
	@EventHandler
	public void onPlayerJoin(final PlayerJoinEvent event) {
		if (enabled) {
			adopt(TeleportStoneCensus.carriedBy(event.getPlayer()));
		}
	}

	/**
	 * A stone that leaves the world for good frees its village to issue a
	 * replacement. Only real destruction counts: an entity removed because
	 * its chunk unloaded, or because somebody picked it up, is a stone that
	 * still very much exists.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityRemove(final EntityRemoveEvent event) {
		if (!enabled || !(event.getEntity() instanceof Item item)
			|| !DESTROYED.contains(event.getCause())) {

			return;
		}
		final String rowId = VillageTeleportStone.rowIdOf(item.getItemStack());
		if (rowId != null) {
			final String live = live(rowId);
			stones.release(live);
			plugin.getLogger().info("[VillageTeleportStones] The stone of "
				+ live + " was destroyed; the village may issue another.");
		}
	}

	/**
	 * Hands the player a stone bound to this village. Re-checks the world in
	 * case one appeared since the offer was shown, and refuses politely when
	 * the village can no longer be resolved.
	 */
	public void grant(final Player player, final String rowId) {
		final NamedVillage village = registry.byRowId(rowId);
		if (village == null || stoneIssued(rowId)) {
			player.sendMessage("\u00a77The stone will not answer to you just now.");
			return;
		}
		final String live = live(rowId);
		stones.issue(live, player.getUniqueId());

		player.getInventory()
			.addItem(VillageTeleportStone.create(live, village.name(),
				(int) (cooldownMillis / 1000)))
			.values()
			.forEach(rest -> player.getWorld()
				.dropItemNaturally(player.getLocation(), rest));
		player.sendMessage("\u00a7b" + village.name()
			+ " entrusts you with a way home.");
		player.playSound(player.getLocation(),
			Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
	}

	@EventHandler
	public void onPlayerInteract(final PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_AIR
			&& event.getAction() != Action.RIGHT_CLICK_BLOCK
			|| event.getHand() != EquipmentSlot.HAND) {
			return;
		}

		final String rowId = VillageTeleportStone.rowIdOf(event.getItem());
		if (rowId != null) {
			useStone(event.getPlayer(), rowId);
		}
	}

	private void useStone(final Player player, final String rowId) {
		// Using one proves it exists and names who has it, which is the
		// surest adoption point of all
		stones.adopt(live(rowId), player.getUniqueId());
		final long now = System.currentTimeMillis();
		final Long last = lastUse.get(player.getUniqueId());
		final long cooling = last == null ? 0 : cooldownMillis - (now - last);
		if (cooling > 0) {
			// Rounded up, so the last fraction of a second never reads as
			// nought left on a stone that is still refusing
			player.sendActionBar(Component.text("\u00a78The stone is still cold \u2014 \u00a77"
				+ VillageTeleportStone.duration((cooling + 999) / 1000)
				+ "\u00a78 to go."));
			return;
		}

		final NamedVillage village = registry.byRowId(rowId);
		final Location target = village == null ? null : landing(village);
		if (target == null) {
			player.sendActionBar(Component.text("\u00a77The bond has faded."));
			return;
		}

		final Location origin = player.getLocation();
		departFrom(origin);
		target.setYaw(origin.getYaw());
		target.setPitch(origin.getPitch());
		player.teleport(target);
		lastUse.put(player.getUniqueId(), now);
		arriveAt(target);
		// Said on arrival as well as on the lore: this is the moment the
		// player wants to know when they can come back
		player.sendActionBar(Component.text("§8The stone cools — ready again in §7"
			+ VillageTeleportStone.duration(cooldownMillis / 1000) + "§8."));
	}

	/** Leaving: motes flung outward in a puff of smoke, a low pull of sound. */
	private static void departFrom(final Location where) {
		where.getWorld().spawnParticle(Particle.PORTAL, where, 60,
			0.3, 0.9, 0.3, 0.6);
		where.getWorld().spawnParticle(Particle.LARGE_SMOKE, where, 12,
			0.2, 0.5, 0.2, 0.02);
		where.getWorld().playSound(where,
			Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
	}

	/** Arriving: the portal collapses inward, sparks, and a bright chime. */
	private static void arriveAt(final Location where) {
		where.getWorld().spawnParticle(Particle.REVERSE_PORTAL, where, 60,
			0.3, 0.9, 0.3, 0.4);
		where.getWorld().spawnParticle(Particle.END_ROD, where, 15,
			0.25, 0.6, 0.25, 0.02);
		where.getWorld().playSound(where,
			Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
		where.getWorld().playSound(where,
			Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
	}

	/** A safe standing spot at the village centre, hugging its surface. */
	private Location landing(final NamedVillage village) {
		final Location centre = village.centre().toLocation();
		if (centre == null) {
			return null;
		}
		final int x = centre.getBlockX();
		final int z = centre.getBlockZ();
		centre.getWorld().getChunkAt(x >> 4, z >> 4).load(true);

		final Location near =
			SpawnGround.findNear(centre.getWorld(), x, centre.getBlockY(), z);
		if (near != null) {
			return near;
		}
		final int top = centre.getWorld().getHighestBlockYAt(x, z);
		return new Location(centre.getWorld(), x + 0.5, top + 1.0, z + 0.5);
	}
}

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Grants and uses the Village Teleport Stone. A fully fortified village
 * offers one through dialogue ({@link #grant}); right-clicking it carries
 * the holder back to the village, behind a per-player cooldown.
 */
public class VillageTeleportStones implements SubPlugin {

	private final VillageRegistry registry;
	private final boolean enabled;
	private final long cooldownMillis;
	private final Map<UUID, Long> lastUse = new HashMap<>();

	public VillageTeleportStones(final JavaPlugin plugin,
		final VillageRegistry registry) {

		this.registry = registry;
		this.enabled = plugin.getConfig()
			.getBoolean("villages.teleport.enabled", true);
		this.cooldownMillis = plugin.getConfig()
			.getInt("villages.teleport.cooldown-seconds", 120) * 1000L;
	}

	/** False keeps the stone's claim entry out of the dialogue GUI. */
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void onEnable() {
		// Event registration is handled by the plugin loader
	}

	@Override
	public void onDisable() {
		// Cooldowns are in-memory only; nothing to tear down
	}

	/**
	 * Hands the player a stone bound to this village. Re-checks the world in
	 * case one appeared since the offer was shown, and refuses politely when
	 * the village can no longer be resolved.
	 */
	public void grant(final Player player, final String rowId) {
		final NamedVillage village = registry.byRowId(rowId);
		if (village == null || TeleportStoneCensus.exists(rowId)) {
			player.sendMessage("\u00a77The stone will not answer to you just now.");
			return;
		}

		player.getInventory()
			.addItem(VillageTeleportStone.create(rowId, village.name()))
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
		final long now = System.currentTimeMillis();
		final Long last = lastUse.get(player.getUniqueId());
		if (last != null && now - last < cooldownMillis) {
			player.sendActionBar(Component.text("\u00a78The stone is still cold."));
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

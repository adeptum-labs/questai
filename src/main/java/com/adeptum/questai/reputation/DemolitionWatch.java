package com.adeptum.questai.reputation;

import com.adeptum.questai.SubPlugin;
import com.adeptum.questai.fortify.BuiltBlocks;
import com.adeptum.questai.fortify.VillageWorksStore;
import com.adeptum.questai.village.VillageRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Watches the village's built works get torn down, and remembers who did
 * it. Only a player's own hands carry blame — what creepers and powder
 * kegs do in the night is nobody's ledger.
 */
public final class DemolitionWatch implements SubPlugin {

	private final VillageRegistry registry;
	private final VillageWorksStore worksStore;
	private final Standings standings;

	public DemolitionWatch(final VillageRegistry registry,
		final VillageWorksStore worksStore, final Standings standings) {

		this.registry = registry;
		this.worksStore = worksStore;
		this.standings = standings;
	}

	@Override
	public void onEnable() {
	}

	@Override
	public void onDisable() {
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(final BlockBreakEvent event) {
		final Location where = event.getBlock().getLocation();
		if (where.getWorld() == null) {
			return;
		}
		final VillageWorksStore.WorksHit struck = worksStore.worksAt(
			where.getWorld().getUID(), where.getBlockX(), where.getBlockY(),
			where.getBlockZ());
		if (struck == null
			|| !shouldRecord(struck.hit(), event.getBlock().getType())) {

			return;
		}
		standings.change(event.getPlayer(), registry.byRowId(struck.rowId()),
			Reputation.DEMOLISHED_BLOCK);
	}

	/**
	 * Decides whether a hit should count toward the village's demolished
	 * record. Ring hits only count if the broken material is palisade stock;
	 * structure hits always count.
	 */
	private boolean shouldRecord(final BuiltBlocks.Hit hit,
		final Material brokenType) {

		if (hit == BuiltBlocks.Hit.NONE) {
			return false;
		}
		if (hit == BuiltBlocks.Hit.RING) {
			return ringMaterial(brokenType);
		}
		return true;
	}

	/** The stuff the palisade is actually made of. */
	static boolean ringMaterial(final Material type) {
		return isStandardBuildingMaterial(type) || isDecorativeMaterial(type);
	}

	/** Logs, fences, and stairs that form the ring's frame. */
	private static boolean isStandardBuildingMaterial(final Material type) {
		return Tag.LOGS.isTagged(type) || Tag.FENCES.isTagged(type)
			|| Tag.WOODEN_STAIRS.isTagged(type)
			|| type == Material.COBBLESTONE || type == Material.COARSE_DIRT;
	}

	/** Torches and lanterns placed on the ring. */
	private static boolean isDecorativeMaterial(final Material type) {
		return type == Material.LANTERN || type == Material.WALL_TORCH
			|| type == Material.TORCH;
	}
}

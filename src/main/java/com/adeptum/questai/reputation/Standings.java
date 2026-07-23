package com.adeptum.questai.reputation;

import com.adeptum.questai.village.NamedVillage;
import com.adeptum.questai.village.VillageRegistry;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The one door to village standing: resolves locations to villages, reads
 * the mended ledger, applies changes, and tells the player when a deed
 * moved them into a different standing altogether.
 */
public final class Standings {

	private final VillageRegistry registry;
	private final VillageReputationStore store;

	public Standings(final VillageRegistry registry,
		final VillageReputationStore store) {

		this.registry = registry;
		this.store = store;
	}

	/** Standing with the village at this location; zero outside any. */
	public int at(final Location location, final UUID playerId) {
		final NamedVillage village =
			location == null ? null : registry.find(location);
		return village == null ? 0
			: store.get(VillageRegistry.rowIdFor(village), playerId);
	}

	/** Standing keyed directly by row id, for callers that carry one. */
	public int of(final String rowId, final UUID playerId) {
		return rowId == null ? 0 : store.get(rowId, playerId);
	}

	/**
	 * Applies a deed to the village at this location. The player only
	 * hears about it when their standing crosses into another bucket, so
	 * repeated small deeds stay quiet.
	 */
	public void change(final Player player, final Location location,
		final int delta) {

		final NamedVillage village =
			location == null ? null : registry.find(location);
		if (village == null || delta == 0) {
			return;
		}
		final String rowId = VillageRegistry.rowIdFor(village);
		final int before = store.get(rowId, player.getUniqueId());
		final int after = store.adjust(rowId, player.getUniqueId(), delta);
		if (Reputation.standing(before) != Reputation.standing(after)) {
			player.sendMessage("§7Your standing with §6" + village.name()
				+ "§7 has " + (after > before ? "risen." : "fallen."));
		}
	}
}

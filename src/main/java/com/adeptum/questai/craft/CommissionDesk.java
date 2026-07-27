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

package com.adeptum.questai.craft;

import com.adeptum.questai.fortify.MaterialTally;
import com.adeptum.questai.fortify.VillageWorksStore;
import com.adeptum.questai.fortify.WorkState;
import com.adeptum.questai.reputation.Standings;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * The counter the dialogue talks to when a player asks a craftsman for
 * work.
 *
 * <p>Everything the screen needs to decide what to draw is answered here,
 * so the conversation does not have to know about the catalogue, the
 * ledger, the standing or how far the village has built.
 */
public class CommissionDesk {

	private final CommissionStore store;
	private final VillageWorksStore worksStore;
	private final Standings standings;

	public CommissionDesk(final CommissionStore store,
		final VillageWorksStore worksStore, final Standings standings) {

		this.store = store;
		this.worksStore = worksStore;
		this.standings = standings;
	}

	/** The best piece this trade will take on here, or null for none. */
	public Commission offerFor(final String profession, final String rowId,
		final UUID playerId) {

		if (profession == null || rowId == null) {
			return null;
		}
		return CommissionGate.bestFor(profession, tierAt(rowId),
			standingOf(rowId, playerId));
	}

	/** The work this village already has in hand for the player, or null. */
	public CommissionOrder openOrder(final String rowId, final UUID playerId) {
		return rowId == null ? null : store.get(rowId, playerId);
	}

	/** Whether this villager will hand a finished piece over right now. */
	public boolean collectable(final String villagerProfession,
		final String rowId, final UUID playerId, final long now) {

		final CommissionOrder order = openOrder(rowId, playerId);
		final Commission commission =
			order == null ? null : Commission.byName(order.commission());
		return commission != null && CommissionGate.collectableBy(
			villagerProfession, commission.getProfession(), order.readyAt(),
			now);
	}

	/** How much of a role's price the player is carrying. */
	public int carried(final ItemStack[] contents, final Commission commission,
		final String role) {

		return MaterialTally.count(contents, commission.accepted(role));
	}

	/**
	 * How far the village has built. The works are the gate on the better
	 * pieces, so an unwired or unbuilt village keeps the trade to its
	 * plainest work.
	 */
	private int tierAt(final String rowId) {
		if (worksStore == null) {
			return 0;
		}
		final WorkState works = worksStore.get(rowId);
		return works == null ? 0 : works.getTier();
	}

	/** How the village feels; a village with no ledger feels nothing. */
	private int standingOf(final String rowId, final UUID playerId) {
		return standings == null ? 0 : standings.of(rowId, playerId);
	}
}

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
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;

/**
 * Keeps rat meat recognisable through the fire. The raw morsel is a tagged
 * rabbit, so the vanilla recipe happily cooks it — into a plain cooked
 * rabbit, shedding the tag and sprite. A dedicated exact-match recipe would
 * race the vanilla one with registration-order luck deciding the winner, so
 * the vanilla recipe is left to do the cooking and only its result is
 * swapped here.
 */
public class RatCooking implements SubPlugin {

	@Override
	public void onEnable() {
		// Listener-only subsystem; registration is handled by the plugin
	}

	@Override
	public void onDisable() {
		// Nothing to tear down
	}

	/** Furnaces and smokers, which fire the same smelt event. */
	@EventHandler(ignoreCancelled = true)
	public void onSmelt(final FurnaceSmeltEvent event) {
		if (RatMeat.isRatMeat(event.getSource())) {
			event.setResult(RatMeat.createCooked());
		}
	}

	/** Campfires cook through the block event instead. */
	@EventHandler(ignoreCancelled = true)
	public void onCampfireCook(final BlockCookEvent event) {
		if (RatMeat.isRatMeat(event.getSource())) {
			event.setResult(RatMeat.createCooked());
		}
	}
}

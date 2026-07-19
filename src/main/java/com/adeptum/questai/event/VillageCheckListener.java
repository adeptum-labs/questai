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

import com.adeptum.questai.model.VillageInfo;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Receives the result of the periodic village scan so other systems can
 * piggyback on it instead of scanning again. Called only when a village
 * was actually detected around the player.
 */
@FunctionalInterface
public interface VillageCheckListener {

	void onVillageCheck(Player player, Location location, VillageInfo info);
}

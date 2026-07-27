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

package com.adeptum.questai.dialogue;

import lombok.Builder;

/**
 * Which entries a villager's main dialogue screen offers.
 *
 * <p>Carried as one value rather than a tail of booleans: the screen has
 * grown enough doors that a positional argument list stopped being
 * readable at the call site, and stopped fitting inside the parameter
 * limit besides.
 *
 * @param questAvailable the villager has work to hand out
 * @param tradeable the villager will open a merchant screen
 * @param worksOpen the village is short of materials for its next project
 * @param stoneOffer the finished village will part with a teleport stone
 * @param commissionOffer this trade will take on a piece of work
 * @param commissionWaiting a piece is being made but is not finished
 * @param commissionReady a finished piece is waiting to be collected
 */
@Builder
public record DialogueOptions(boolean questAvailable, boolean tradeable,
	boolean worksOpen, boolean stoneOffer, boolean commissionOffer,
	boolean commissionWaiting, boolean commissionReady) {
}

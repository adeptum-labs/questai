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

package com.adeptum.questai;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the mcMMO XPGainReason parameter used when rewarding players.
 *
 * <p>The old code passed a display message (e.g. "§aYou earned 100 MCMMO XP
 * in MINING!") as the XPGainReason. mcMMO's ExperienceAPI resolves this via
 * {@link XPGainReason#getXPGainReason(String)} — which returns null for
 * unrecognised strings — then throws InvalidXPGainReasonException.
 */
class RewardPlayerTest {

	/**
	 * Reproduces the original bug: a chat-formatted display message is
	 * not a valid XPGainReason, so mcMMO cannot resolve it.
	 */
	@Test
	void displayMessageIsNotAValidXpGainReason() {
		final String oldReason = "§aYou earned 100 MCMMO XP in MINING!";

		assertNull(XPGainReason.getXPGainReason(oldReason),
			"A display message must not resolve to a valid XPGainReason — "
				+ "mcMMO throws InvalidXPGainReasonException when this is null");
	}

	/**
	 * Verifies the fix: "COMMAND" resolves to a valid XPGainReason.
	 */
	@Test
	void commandIsAValidXpGainReason() {
		final XPGainReason reason = XPGainReason.getXPGainReason("COMMAND");

		assertNotNull(reason,
			"COMMAND must resolve to a valid XPGainReason");
		assertEquals(XPGainReason.COMMAND, reason);
	}
}

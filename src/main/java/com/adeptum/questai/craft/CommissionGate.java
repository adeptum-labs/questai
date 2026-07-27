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

import com.adeptum.questai.reputation.Reputation;

/**
 * What a craftsman will take on, and when the work is done.
 *
 * <p>All of it is arithmetic over plain values so the rules can be read and
 * tested off-server: which piece a trade offers, whether the wait has run
 * out, and who may hand the finished piece over.
 */
public final class CommissionGate {

	/**
	 * Once a finished piece has sat this long uncollected, any villager will
	 * part with it. Without that a player could lose their materials for
	 * good when the village's last smith dies.
	 */
	private static final long STALE_MILLIS = 72L * 60 * 60 * 1000;

	private static final long MINUTE_MILLIS = 60L * 1000;

	private CommissionGate() {
	}

	/** Whether the village has built enough, and likes the player enough. */
	public static boolean unlocked(final Commission commission, final int tier,
		final int rep) {

		final Commission.Gate gate = commission.getGate();
		return tier >= gate.minTier()
			&& Reputation.standing(rep).ordinal()
				>= gate.minStanding().ordinal();
	}

	/**
	 * The best piece this trade will take on, or null when the profession
	 * keeps no catalogue or nothing in it has been earned yet. The catalogue
	 * rises within a profession, so the last unlocked entry is the best one.
	 */
	public static Commission bestFor(final String profession, final int tier,
		final int rep) {

		Commission best = null;
		for (final Commission commission : Commission.values()) {
			if (commission.getProfession().equals(profession)
				&& unlocked(commission, tier, rep)) {
				best = commission;
			}
		}
		return best;
	}

	/** When the piece will be finished, shortened by the configured speed. */
	public static long readyAt(final long placedAt, final int minutes,
		final double speed) {

		final double scale = speed <= 0 ? 1.0 : speed;
		return placedAt + (long) (minutes * MINUTE_MILLIS / scale);
	}

	/** Whether the wait has run out. */
	public static boolean ready(final long readyAt, final long now) {
		return now >= readyAt;
	}

	/** Whole minutes still to wait, rounded up, floored at zero. */
	public static long remainingMinutes(final long readyAt, final long now) {
		final long left = readyAt - now;
		return left <= 0 ? 0 : (left + MINUTE_MILLIS - 1) / MINUTE_MILLIS;
	}

	/**
	 * Whether this villager will hand the piece over: the trade that took
	 * the order always will, and once the piece has gone stale on the shelf
	 * anyone will, so a village that has lost the trade entirely still pays
	 * out.
	 */
	public static boolean collectableBy(final String villagerProfession,
		final String orderProfession, final long readyAt, final long now) {

		if (!ready(readyAt, now)) {
			return false;
		}
		return orderProfession.equals(villagerProfession)
			|| now > readyAt + STALE_MILLIS;
	}
}

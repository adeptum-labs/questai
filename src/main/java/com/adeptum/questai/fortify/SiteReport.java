package com.adeptum.questai.fortify;

import com.adeptum.questai.relic.RelicCompass;

/**
 * What a fruitless site search saw, and how that reads to the donor.
 *
 * <p>A refusal that only says "nowhere level to build" leaves the player
 * with nothing to act on. Counting why each footprint was turned down, and
 * remembering the closest near miss, turns the same refusal into an errand:
 * go that way, and clear it.
 */
public final class SiteReport {

	private static final String OPENING = "§7They have what they need, but ";
	private static final String HINT =
		" §8Clear and level a patch nearby, then speak to them again.";

	private int blocked;
	private int built;
	private int unseen;
	private int steep;
	private int bestSpread = Integer.MAX_VALUE;
	private int bestDx;
	private int bestDz;

	/** Counts a footprint turned down by what the ground under it holds. */
	public void rejected(final GroundCover.Verdict verdict) {
		switch (verdict) {
			case BLOCKED -> blocked++;
			case BUILT -> built++;
			default -> unseen++;
		}
	}

	/** Counts a footprint nobody has been out to see yet. */
	public void unscanned() {
		unseen++;
	}

	/** Counts buildable ground that was simply too broken, and how far off. */
	public void tooSteep(final int spread, final int dx, final int dz) {
		steep++;
		if (spread < bestSpread) {
			bestSpread = spread;
			bestDx = dx;
			bestDz = dz;
		}
	}

	/**
	 * The line the donor reads. A near miss wins over every other reason,
	 * because it is the only one that points somewhere.
	 */
	public String describe() {
		if (steep > 0) {
			return OPENING + "the flattest ground they found lies "
				+ RelicCompass.cardinal(bestDx, bestDz)
				+ " of here, and even that is too broken." + HINT;
		}
		if (worst() == 0) {
			return OPENING + "nowhere level to build.";
		}
		if (blocked == worst()) {
			return OPENING + "every way they turn the ground runs into water."
				+ HINT;
		}
		if (built == worst()) {
			return OPENING + "the ground around them is already spoken for."
				+ HINT;
		}
		return OPENING + "nobody has walked far enough out to know the ground.";
	}

	/** How many footprints the commonest reason accounts for. */
	private int worst() {
		return Math.max(blocked, Math.max(built, unseen));
	}
}

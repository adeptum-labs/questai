package com.adeptum.questai.reputation;

/**
 * How a village feels about one player, and the arithmetic of feeling.
 *
 * <p>Standing runs from {@link #MIN} to {@link #MAX}. Deeds move it, time
 * mends the negative half of it, and everything a village does differently
 * for a liked or disliked player is derived from it here, in one pure
 * place, so the behaviour is testable off-server and tunable in one file.
 */
public final class Reputation {

	public static final int MIN = -100;
	public static final int MAX = 100;

	/** Completing a quest for one of the village's own. */
	public static final int QUEST_COMPLETED = 5;
	/** Standing among the defenders when a raid breaks. */
	public static final int RAID_DEFENDED = 10;
	/** Watching a raid succeed without stopping it. */
	public static final int RAID_FAILED = -10;
	/** Each villager lost in a raid the player witnessed. */
	public static final int VILLAGER_LOST = -5;
	/** Each block of the village's works torn out. */
	public static final int DEMOLISHED_BLOCK = -1;

	/** At or below this the village refuses trade and donations. */
	public static final int NO_TRADE_AT = -40;
	/** At or below this no villager offers fresh work. */
	public static final int NO_QUESTS_AT = -80;

	private static final long MEND_INTERVAL_MILLIS = 2L * 60 * 60 * 1000;
	private static final double OFFER_BONUS_PER_POINT = 0.002;
	private static final double OFFER_CEILING = 1.2;
	private static final int RESPECTED_AT = 25;
	private static final int REVERED_AT = 75;
	private static final int RICH_REWARDS_AT = 50;

	private Reputation() {
	}

	/** Clamps a raw value into the standing bounds. */
	public static int clamp(final int value) {
		return Math.max(MIN, Math.min(MAX, value));
	}

	/**
	 * Grudges soften: a negative standing creeps one point back toward
	 * zero for every two hours that pass. Goodwill is kept, not mended.
	 */
	public static int mend(final int value, final long elapsedMillis) {
		if (value >= 0 || elapsedMillis <= 0) {
			return value;
		}
		final long mended = value + elapsedMillis / MEND_INTERVAL_MILLIS;
		return (int) Math.min(0, mended);
	}

	/** Scales the fresh-quest offer chance by standing. */
	public static double offerScale(final int rep) {
		if (rep <= NO_QUESTS_AT) {
			return 0.0;
		}
		if (rep < 0) {
			return 1.0 + rep / 100.0;
		}
		return Math.min(OFFER_CEILING, 1.0 + rep * OFFER_BONUS_PER_POINT);
	}

	/** Scales a quest's XP payout by standing. */
	public static int rewardScale(final int xp, final int rep) {
		if (rep < 0) {
			return xp * 4 / 5;
		}
		return rep >= RICH_REWARDS_AT ? xp * 11 / 10 : xp;
	}

	/** True while the village will still do business with the player. */
	public static boolean tradesWith(final int rep) {
		return rep > NO_TRADE_AT;
	}

	/** The coarse bucket a standing is felt in. */
	public static Standing standing(final int rep) {
		if (rep <= NO_TRADE_AT) {
			return Standing.HATED;
		}
		if (rep < 0) {
			return Standing.DISLIKED;
		}
		if (rep >= REVERED_AT) {
			return Standing.REVERED;
		}
		return rep >= RESPECTED_AT ? Standing.RESPECTED : Standing.NEUTRAL;
	}

	/**
	 * A dialogue-prompt clause voicing the standing, or null while the
	 * village has no strong feelings either way.
	 */
	public static String standingClause(final int rep) {
		return switch (standing(rep)) {
			case HATED -> "You see this player as an enemy of the village.";
			case DISLIKED -> "You are wary and cold toward this player.";
			case NEUTRAL -> null;
			case RESPECTED ->
				"You respect this player's service to the village.";
			case REVERED ->
				"You regard this player as a hero of the village.";
		};
	}

	/** The steps of feeling a village can hold about a player. */
	public enum Standing {
		HATED, DISLIKED, NEUTRAL, RESPECTED, REVERED
	}
}

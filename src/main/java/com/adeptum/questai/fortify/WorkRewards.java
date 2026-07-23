package com.adeptum.questai.fortify;

import com.adeptum.questai.mob.MobDrops;
import com.adeptum.questai.star.Starfall;
import java.util.Random;

/**
 * What a finished project hands back.
 *
 * <p>The skill is drawn at award time rather than baked in when the project
 * starts, matching how selling a star fragment works. Deliberately no relic
 * roll: relics stay tied to quests, chains and stars. The skill pool is the
 * same array a star fragment sale draws from, by reference.
 */
public final class WorkRewards {

	private WorkRewards() {
	}

	/** Which mcMMO skill this award lands in. */
	public static String pickSkill(final Random rng) {
		return Starfall.SELL_SKILLS[rng.nextInt(Starfall.SELL_SKILLS.length)];
	}

	public static int xpFor(final VillageWork work) {
		return work.getXpReward();
	}

	/** A gear draw at this project's rate, or null when it misses. */
	public static MobDrops.GearRoll rollGear(final Random rng,
		final VillageWork work) {

		return MobDrops.roll(rng, work.getGearChance());
	}

	/** Quests completed under a finished bell tower ring a little richer. */
	public static int bellBoost(final int xp) {
		return xp * 5 / 4;
	}
}

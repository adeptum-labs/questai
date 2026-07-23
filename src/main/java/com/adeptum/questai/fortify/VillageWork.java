package com.adeptum.questai.fortify;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Material;

/**
 * The fixed ladder of projects a village works through, one at a time.
 *
 * <p>Requirements are keyed by a role rather than by a concrete material, so a
 * player carrying spruce is never turned away by a requirement naming oak. The
 * quantities are tuned for player effort and are deliberately far below the
 * block count each structure places; the village supplies the remainder.
 *
 * <p>A tier is only offered once the village holds drawings for it —
 * {@link #hasPlans()} — so the ladder pauses rather than promising a project
 * nobody knows how to raise.
 */
@Getter
public enum VillageWork {

	WATCHTOWER("a watchtower", 900, 0.05, true, "structures/watchtower.txt",
		requirements("rough_stone", 64, "logs", 32)),
	PALISADE("a palisade", 1400, 0.08, true, null,
		requirements("logs", 192, "rough_stone", 64)),
	GATE("a gate", 2200, 0.18, false, null,
		requirements("dressed_stone", 128, "iron", 24, "torches", 16)),
	BELL_TOWER("a bell tower", 3200, 0.30, false, null,
		requirements("dressed_stone", 256, "iron", 48, "gold", 8));

	private static final Map<String, Set<Material>> ACCEPTED = acceptedRoles();

	private final String displayName;
	private final int xpReward;
	private final double gearChance;
	@Getter(AccessLevel.NONE)
	private final boolean plans;
	/** Point-structure grid resource; null for the palisade's module ring. */
	private final String schematicResource;
	private final Map<String, Integer> requirements;

	VillageWork(final String displayName, final int xpReward,
		final double gearChance, final boolean plans,
		final String schematicResource, final Map<String, Integer> requirements) {

		this.displayName = displayName;
		this.xpReward = xpReward;
		this.gearChance = gearChance;
		this.plans = plans;
		this.schematicResource = schematicResource;
		this.requirements = requirements;
	}

	/** Whether the village can raise this yet; false pauses the ladder here. */
	public boolean hasPlans() {
		return plans;
	}

	/** The project at this rung, or null when the ladder is finished. */
	public static VillageWork byTier(final int tier) {
		return tier < 0 || tier >= values().length ? null : values()[tier];
	}

	public static int count() {
		return values().length;
	}

	/** Which materials count toward a role; empty for an unknown role. */
	public Set<Material> accepted(final String role) {
		return ACCEPTED.getOrDefault(role, Set.of());
	}

	/** How this project reads inside a villager's memory of the player. */
	public String getMemoryTitle() {
		return displayName;
	}

	private static Map<String, Integer> requirements(final Object... pairs) {
		final Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return Map.copyOf(map);
	}

	private static Map<String, Set<Material>> acceptedRoles() {
		final Map<String, Set<Material>> roles = new LinkedHashMap<>();
		roles.put("logs", materials(Material.OAK_LOG, Material.SPRUCE_LOG,
			Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG,
			Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG));
		roles.put("rough_stone", materials(Material.COBBLESTONE,
			Material.MOSSY_COBBLESTONE, Material.ANDESITE, Material.STONE,
			Material.SANDSTONE));
		roles.put("dressed_stone", materials(Material.STONE_BRICKS,
			Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
			Material.CUT_SANDSTONE, Material.DEEPSLATE_TILES));
		roles.put("iron", materials(Material.IRON_INGOT));
		roles.put("gold", materials(Material.GOLD_INGOT));
		roles.put("torches", materials(Material.TORCH, Material.LANTERN));
		return Map.copyOf(roles);
	}

	private static Set<Material> materials(final Material... values) {
		final EnumSet<Material> set = EnumSet.noneOf(Material.class);
		for (final Material material : values) {
			set.add(material);
		}
		return Set.copyOf(set);
	}
}

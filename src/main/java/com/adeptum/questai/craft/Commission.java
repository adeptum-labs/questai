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

import com.adeptum.questai.mob.GearEnchant;
import com.adeptum.questai.mob.GearItem;
import com.adeptum.questai.reputation.Reputation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.bukkit.Material;

/**
 * The work a village craftsman will take on, by trade.
 *
 * <p>Each entry belongs to one profession, named as the plain string Bukkit
 * reports rather than the profession type itself: that type is registry
 * backed, so naming it here would drag the registry into every test that
 * touches the catalogue. The string is what the dialogue already carries.
 *
 * <p>A craftsman offers the best single piece the player has earned — the
 * catalogue is ordered so that within a profession the gates only ever rise,
 * and the last unlocked entry wins. Prices, waits and gates are data; the
 * shape around them is not.
 */
@Getter
public enum Commission {

	QUIVER_OF_ARROWS("a quiver of arrows", "FLETCHER",
		gate(0, Reputation.Standing.NEUTRAL, 8),
		requirements("flint", 16, "feathers", 16, "logs", 8),
		List.of(CommissionOutput.supply(Material.ARROW, 64))),
	HARVEST_STORES("winter stores", "FARMER",
		gate(0, Reputation.Standing.NEUTRAL, 12),
		requirements("grain", 32),
		List.of(CommissionOutput.supply(Material.BREAD, 24),
			CommissionOutput.supply(Material.BONE_MEAL, 8))),
	SALTED_PROVISIONS("salted provisions", "BUTCHER",
		gate(0, Reputation.Standing.NEUTRAL, 10),
		requirements("grain", 24, "coal", 8),
		List.of(CommissionOutput.supply(Material.COOKED_BEEF, 16))),
	SEA_LARDER("a sea larder", "FISHERMAN",
		gate(0, Reputation.Standing.NEUTRAL, 10),
		requirements("fish", 16, "coal", 8),
		List.of(CommissionOutput.supply(Material.COOKED_SALMON, 16))),
	WOOLLENS("woollens", "SHEPHERD",
		gate(0, Reputation.Standing.NEUTRAL, 10),
		requirements("wool", 24, "string", 8),
		List.of(CommissionOutput.supply(Material.WHITE_BED, 2),
			CommissionOutput.supply(Material.WHITE_CARPET, 8))),
	DRESSED_STONE_ORDER("dressed stone", "MASON",
		gate(0, Reputation.Standing.NEUTRAL, 15),
		requirements("stone", 64, "coal", 16),
		List.of(CommissionOutput.supply(Material.STONE_BRICKS, 64),
			CommissionOutput.supply(Material.CHISELED_STONE_BRICKS, 16))),
	SCRIBES_BUNDLE("a scribe's bundle", "LIBRARIAN",
		gate(1, Reputation.Standing.NEUTRAL, 15),
		requirements("paper", 24, "leather", 6, "string", 4),
		List.of(CommissionOutput.supply(Material.BOOKSHELF, 6))),
	SURVEYORS_KIT("a surveyor's kit", "CARTOGRAPHER",
		gate(1, Reputation.Standing.NEUTRAL, 15),
		requirements("paper", 16, "redstone", 8, "iron", 4),
		List.of(CommissionOutput.supply(Material.COMPASS, 1),
			CommissionOutput.supply(Material.MAP, 8))),
	SADDLERY("a saddle", "LEATHERWORKER",
		gate(1, Reputation.Standing.NEUTRAL, 15),
		requirements("leather", 12, "iron", 4),
		List.of(CommissionOutput.supply(Material.SADDLE, 1))),
	PLATED_HELM("a plated helm", "ARMORER",
		gate(1, Reputation.Standing.NEUTRAL, 20),
		requirements("iron", 24, "coal", 16),
		List.of(CommissionOutput.gear(GearItem.IRON_HELMET,
			"\u00a7fVillage Helm", GearEnchant.PROTECTION, 2,
			GearEnchant.UNBREAKING, 2))),
	HUNTERS_BOW("a hunter's bow", "FLETCHER",
		gate(1, Reputation.Standing.RESPECTED, 25),
		requirements("logs", 24, "string", 12, "feathers", 16),
		List.of(CommissionOutput.gear(GearItem.BOW, "\u00a7fHunter's Bow",
			GearEnchant.POWER, 3, GearEnchant.UNBREAKING, 2))),
	KEEN_BLADE("a keen blade", "WEAPONSMITH",
		gate(1, Reputation.Standing.RESPECTED, 30),
		requirements("iron", 32, "coal", 16, "flint", 8),
		List.of(CommissionOutput.gear(GearItem.IRON_SWORD, "\u00a7fKeen Blade",
			GearEnchant.SHARPNESS, 3, GearEnchant.UNBREAKING, 2))),
	CRAFTSMANS_WHETSTONE("a craftsman's whetstone", "TOOLSMITH",
		gate(2, Reputation.Standing.RESPECTED, 25),
		requirements("iron", 16, "stone", 32, "coal", 24),
		List.of(CommissionOutput.masterwork(Material.BRICK,
			"\u00a7bCraftsman's Whetstone"))),
	WARDED_CUIRASS("a warded cuirass", "ARMORER",
		gate(3, Reputation.Standing.RESPECTED, 45),
		requirements("iron", 48, "diamond", 2, "coal", 32),
		List.of(CommissionOutput.gear(GearItem.IRON_CHESTPLATE,
			"\u00a7fWarden's Cuirass", GearEnchant.PROTECTION, 4,
			GearEnchant.THORNS, 2, GearEnchant.UNBREAKING, 3))),
	SANCTIFIED_STORES("sanctified stores", "CLERIC",
		gate(3, Reputation.Standing.RESPECTED, 60),
		requirements("gold", 16, "lapis", 16, "amethyst", 6),
		List.of(CommissionOutput.supply(Material.GOLDEN_APPLE, 2),
			CommissionOutput.supply(Material.EXPERIENCE_BOTTLE, 8))),
	EMBERBRAND("the Emberbrand", "WEAPONSMITH",
		gate(4, Reputation.Standing.REVERED, 90),
		requirements("iron", 24, "gold", 8, "diamond", 1, "coal", 48),
		List.of(CommissionOutput.gear(GearItem.DIAMOND_SWORD,
			"\u00a76Emberbrand", GearEnchant.SHARPNESS, 4,
			GearEnchant.FIRE_ASPECT, 2, GearEnchant.UNBREAKING, 3)));

	private final String displayName;
	/** The profession name as Bukkit reports it, never the profession type. */
	private final String profession;
	private final Gate gate;
	private final Map<String, Integer> requirements;
	private final List<CommissionOutput> outputs;

	Commission(final String displayName, final String profession,
		final Gate gate, final Map<String, Integer> requirements,
		final List<CommissionOutput> outputs) {

		this.displayName = displayName;
		this.profession = profession;
		this.gate = gate;
		this.requirements = requirements;
		this.outputs = outputs;
	}

	/** Which materials count toward a role of this commission's price. */
	public Set<Material> accepted(final String role) {
		return CraftRoles.accepted(role);
	}

	/** What the offer and the finished piece are shown as in the GUI. */
	public Material icon() {
		return outputs.get(0).supply();
	}

	/** How this piece of work reads inside a villager's memory. */
	public String getMemoryTitle() {
		return displayName;
	}

	/** Resolves a stored name, or null when the catalogue no longer has it. */
	public static Commission byName(final String name) {
		for (final Commission commission : values()) {
			if (commission.name().equals(name)) {
				return commission;
			}
		}
		return null;
	}

	private static Gate gate(final int minTier,
		final Reputation.Standing minStanding, final int minutes) {

		return new Gate(minTier, minStanding, minutes);
	}

	private static Map<String, Integer> requirements(final Object... pairs) {
		final Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return Map.copyOf(map);
	}

	/**
	 * What a village must have built and how it must feel about the player
	 * before this work is offered, and how long the work then takes.
	 *
	 * @param minTier the fortification rung the village must have reached
	 * @param minStanding the standing the player must hold with the village
	 * @param minutes the wait before the piece can be collected
	 */
	public record Gate(int minTier, Reputation.Standing minStanding,
		int minutes) {
	}
}

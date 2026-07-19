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

import java.util.List;
import lombok.Getter;
import org.bukkit.Material;

/**
 * The pool of gear that variant mobs can drop, each with the
 * enchantments that suit it.
 */
@Getter
public enum GearItem {

	IRON_SWORD(Material.IRON_SWORD, List.of(GearEnchant.SHARPNESS,
		GearEnchant.FIRE_ASPECT, GearEnchant.KNOCKBACK,
		GearEnchant.UNBREAKING)),
	DIAMOND_SWORD(Material.DIAMOND_SWORD, List.of(GearEnchant.SHARPNESS,
		GearEnchant.FIRE_ASPECT, GearEnchant.UNBREAKING)),
	BOW(Material.BOW, List.of(GearEnchant.POWER, GearEnchant.PUNCH,
		GearEnchant.UNBREAKING)),
	CROSSBOW(Material.CROSSBOW, List.of(GearEnchant.QUICK_CHARGE,
		GearEnchant.PIERCING, GearEnchant.UNBREAKING)),
	IRON_CHESTPLATE(Material.IRON_CHESTPLATE, List.of(GearEnchant.PROTECTION,
		GearEnchant.THORNS, GearEnchant.UNBREAKING)),
	IRON_HELMET(Material.IRON_HELMET, List.of(GearEnchant.PROTECTION,
		GearEnchant.PROJECTILE_PROTECTION, GearEnchant.UNBREAKING));

	private final Material material;
	private final List<GearEnchant> enchants;

	GearItem(final Material material, final List<GearEnchant> enchants) {
		this.material = material;
		this.enchants = enchants;
	}
}

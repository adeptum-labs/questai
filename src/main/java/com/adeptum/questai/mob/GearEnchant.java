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

import lombok.Getter;

/**
 * The enchantments variant gear can roll, abstracted from the
 * registry-backed Bukkit Enchantment type so loot logic stays testable
 * off-server. The forge maps these to real enchantments.
 */
@Getter
public enum GearEnchant {

	SHARPNESS(1, 5),
	FIRE_ASPECT(1, 2),
	KNOCKBACK(1, 2),
	POWER(1, 5),
	PUNCH(1, 2),
	UNBREAKING(1, 3),
	PROTECTION(1, 4),
	PROJECTILE_PROTECTION(1, 4),
	THORNS(1, 3),
	QUICK_CHARGE(1, 3),
	PIERCING(1, 4);

	private final int minLevel;
	private final int maxLevel;

	GearEnchant(final int minLevel, final int maxLevel) {
		this.minLevel = minLevel;
		this.maxLevel = maxLevel;
	}
}

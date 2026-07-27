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

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * Which materials satisfy which role in a commission's price.
 *
 * <p>A craftsman names what the work needs rather than which block it must
 * come from, so a player carrying birch is never turned away by a price
 * written in oak. The village works keep their own, narrower table; the two
 * are deliberately separate because a builder and a smith do not accept the
 * same stone.
 */
public final class CraftRoles {

	private static final Map<String, Set<Material>> ACCEPTED = roleTable();

	private CraftRoles() {
	}

	/** Which materials count toward a role; empty for an unknown role. */
	public static Set<Material> accepted(final String role) {
		return ACCEPTED.getOrDefault(role, Set.of());
	}

	/** Every role a commission price may be written in. */
	public static Set<String> roles() {
		return ACCEPTED.keySet();
	}

	private static Map<String, Set<Material>> roleTable() {
		final Map<String, Set<Material>> roles = new LinkedHashMap<>();
		roles.put("iron", materials(Material.IRON_INGOT));
		roles.put("gold", materials(Material.GOLD_INGOT));
		roles.put("diamond", materials(Material.DIAMOND));
		roles.put("coal", materials(Material.COAL, Material.CHARCOAL));
		roles.put("flint", materials(Material.FLINT));
		roles.put("logs", materials(Material.OAK_LOG, Material.SPRUCE_LOG,
			Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG,
			Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG));
		roles.put("stone", materials(Material.STONE_BRICKS, Material.STONE,
			Material.ANDESITE, Material.DEEPSLATE_TILES,
			Material.CUT_SANDSTONE));
		roles.put("leather", materials(Material.LEATHER, Material.RABBIT_HIDE));
		roles.put("wool", woolMaterials());
		roles.put("string", materials(Material.STRING));
		roles.put("feathers", materials(Material.FEATHER));
		roles.put("grain", materials(Material.WHEAT, Material.POTATO,
			Material.CARROT, Material.BEETROOT));
		roles.put("fish", materials(Material.COD, Material.SALMON));
		roles.put("paper", materials(Material.PAPER, Material.BOOK));
		roles.put("lapis", materials(Material.LAPIS_LAZULI));
		roles.put("redstone", materials(Material.REDSTONE));
		roles.put("amethyst", materials(Material.AMETHYST_SHARD));
		return Map.copyOf(roles);
	}

	/** Every dye of wool serves; a shepherd does not care about colour. */
	private static Set<Material> woolMaterials() {
		final EnumSet<Material> set = EnumSet.noneOf(Material.class);
		for (final Material material : Material.values()) {
			if (material.name().endsWith("_WOOL")) {
				set.add(material);
			}
		}
		return Set.copyOf(set);
	}

	private static Set<Material> materials(final Material... values) {
		final EnumSet<Material> set = EnumSet.noneOf(Material.class);
		for (final Material material : values) {
			set.add(material);
		}
		return Set.copyOf(set);
	}
}
